# ledgerline

A small double-entry ledger API.

I built this to learn Java 21 and Spring Boot 3 properly, so the scope is
deliberately narrow: five endpoints, one migration, one interesting invariant.
The goal was real code quality on a small surface rather than a broad feature
set. There is no authentication, no multi-currency conversion, and no reporting
layer, because none of that was needed to exercise the parts I wanted to learn:
JPA mapping, transaction boundaries, pessimistic and optimistic locking, Flyway,
Testcontainers, and a clean domain model that enforces its own rules.

## What "double-entry" means here

Every transaction is a `JournalEntry` made of two or more `JournalLine`s. Each
line posts a signed amount against one account. A positive amount is a debit, a
negative amount is a credit.

The hard invariant: the signed sum of all lines in an entry is exactly zero. An
entry that does not balance cannot be constructed. This is enforced in the
domain factory (`JournalEntry.create`), not only by a database check, so the
rule holds even in a plain unit test with no database. The factory also rejects
an entry with fewer than two lines and an entry whose lines do not all share one
currency (and that currency must match each account).

An account balance is never stored on the account row. It is always the signed
sum of that account's posted lines, computed by query. That means a balance
cannot drift out of sync with the ledger, because there is nothing to keep in
sync.

`Money` is a value object (an immutable `record` of amount plus ISO 4217
currency). Arithmetic across currencies throws rather than silently coercing.

## Idempotency

`POST /journal-entries` accepts an optional `Idempotency-Key` header. The key is
stored in its own table (`idempotency_key`), keyed by the header value, with the
id of the entry it created.

The key row is inserted in the same transaction that writes the journal lines,
and `idem_key` is the primary key, so the guarantee is at the database:

- First time a key is seen: the entry is posted and the key row is inserted in
  one atomic transaction. If either fails, both roll back.
- Repeat of a known key: a fast lookup returns the original entry and nothing is
  written.
- Two requests racing with the same key: they serialize on the shared account
  locks (see below), then the second one's key insert violates the primary key.
  That whole transaction rolls back, including its lines and account version
  bumps, and the request re-reads and returns the winner's entry.

A request with no key always posts.

## Concurrency and locking

Posting an entry that touches an account takes a `SELECT ... FOR UPDATE` row
lock on that account (`@Lock(PESSIMISTIC_WRITE)` in `AccountRepository`).
Accounts are locked in ascending id order, so two entries touching the same pair
of accounts cannot deadlock by locking them in opposite orders.

Each post runs in a single transaction with no retry. Concurrent posts that
touch the same account block on that `FOR UPDATE` lock and run one after
another, so each reads a fresh account row and there is no optimistic conflict
to recover from. `Account` also carries an `@Version` column, bumped on every
account a post touches, as a correctness backstop: if some future code path ever
mutated an account without taking the lock, the version check would reject the
lost update rather than let it through.

An earlier version wrapped the post in retry-with-backoff on optimistic
failure. That was wrong: the posting operation is not safe to replay, so a
retry could post the entry more than once. The fix was to drop the retry and
rely on the pessimistic lock plus a single transaction.

`ConcurrentPostingIT` posts conflicting entries from several threads at once and
asserts that every post applies with no lost update, that the entry count is
exact, and that the account version advanced once per post. It also posts the
same idempotency key from five threads at once and asserts exactly one entry is
created.

## Running it

Needs a JDK 21 and Docker (for Postgres).

```bash
# Postgres plus the app, built from source
docker compose up --build

# app on http://localhost:8080, Postgres on localhost:5432
```

Run against a local Postgres without Docker Compose:

```bash
docker run --rm -p 5432:5432 \
  -e POSTGRES_DB=ledgerline -e POSTGRES_USER=ledgerline -e POSTGRES_PASSWORD=ledgerline \
  postgres:16-alpine

./mvnw spring-boot:run
```

Configuration is via environment variables (see `src/main/resources/application.yml`):
`JDBC_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`. Flyway runs the migration in
`src/main/resources/db/migration` on startup; Hibernate is set to `validate`, so
the mapping and the schema have to agree.

## Endpoints

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `POST` | `/accounts` | Create an account | Body: `name`, `currency` (3 letters), `type` (`ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`). Returns `201` with a `Location` header. |
| `GET` | `/accounts/{id}/balance` | Current balance | Signed sum of the account's posted lines. |
| `GET` | `/accounts/{id}/ledger` | Line history with running balance | Oldest first, each row carries the balance after that line. |
| `POST` | `/journal-entries` | Post a balanced entry | Body: optional `description`, `lines[]` of `accountId`, `amount` (string decimal), `currency`. Optional `Idempotency-Key` header. `201` on success, `422` if the entry does not balance or mixes currencies, `400` if the body is malformed or has fewer than two lines. |
| `GET` | `/journal-entries/{id}` | Fetch an entry with its lines | `404` if unknown. |

Errors use one shape: `{"error": {"code", "message", "details"}}`.

### Example

```bash
curl -XPOST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"name":"Cash","currency":"USD","type":"ASSET"}'
curl -XPOST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"name":"Sales","currency":"USD","type":"REVENUE"}'

curl -XPOST localhost:8080/journal-entries -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: sale-0001' \
  -d '{"description":"cash sale","lines":[
        {"accountId":1,"amount":"100.00","currency":"USD"},
        {"accountId":2,"amount":"-100.00","currency":"USD"}]}'

curl localhost:8080/accounts/1/balance
curl localhost:8080/accounts/1/ledger
```

## Tests

```bash
./mvnw -B clean verify
```

Two tiers:

- **Unit and slice tests** run always, no Docker needed.
  - `MoneyTest`: the value object, including cross-currency rejection.
  - `JournalEntryBalanceTest`: the sum-to-zero invariant, direct against the
    domain factory. Balanced constructs; unbalanced, single-line, mixed-currency,
    and account-currency-mismatch all throw.
  - `LedgerServiceIdempotencyTest`: the service-level idempotency handling with
    Mockito. A known key short-circuits before the poster runs; a key that loses
    the race in the poster (a duplicate-key failure) is resolved by re-reading
    the winner rather than propagated.
  - `LedgerApiTest`: `@SpringBootTest` with a random port over an in-memory
    database. Posts a balanced entry and reads it back, checks balance and
    running balance, rejects an unbalanced entry with `422`, and checks that a
    repeated `Idempotency-Key` returns the same entry without double-posting.
- **Integration test** (`ConcurrentPostingIT`, tagged `integration`, run by the
  failsafe plugin in `verify`) uses a real Postgres 16 via Testcontainers. It
  self-skips when Docker is not available (`disabledWithoutDocker = true`), so
  `verify` stays green without Docker while the unit tier still covers the
  invariants.

CI (`.github/workflows/ci.yml`) runs `./mvnw -B clean verify` on GitHub Actions,
where Docker is present, so the integration test runs there.
