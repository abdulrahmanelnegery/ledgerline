package com.ledgerline.integration;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.AccountType;
import com.ledgerline.repo.AccountRepository;
import com.ledgerline.repo.JournalEntryRepository;
import com.ledgerline.service.LedgerService;
import com.ledgerline.service.PostEntryCommand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres via Testcontainers. Self-skips when Docker is unavailable
 * ({@code disabledWithoutDocker = true}), so {@code mvn verify} stays green
 * without Docker and the pure unit tests still cover the invariants.
 *
 * <p>Locking under test: {@code AccountRepository.findByIdForUpdate} takes a
 * {@code SELECT ... FOR UPDATE} row lock, and {@code Account.version} adds an
 * optimistic guard on the same path.
 */
@SpringBootTest
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentPostingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LedgerService ledger;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JournalEntryRepository entries;

    @Test
    void twoConcurrentPostsToTheSameAccountsBothApplyWithNoLostUpdate() throws Exception {
        Account cash = accounts.save(new Account("Cash", "USD", AccountType.ASSET));
        Account sales = accounts.save(new Account("Sales", "USD", AccountType.REVENUE));
        PostEntryCommand command = new PostEntryCommand("concurrent sale", List.of(
                new PostEntryCommand.Line(cash.getId(), new BigDecimal("100.00"), "USD"),
                new PostEntryCommand.Line(sales.getId(), new BigDecimal("-100.00"), "USD")));

        List<Throwable> failures = runConcurrently(2, () -> ledger.post(command, null));

        assertThat(failures).isEmpty();
        assertThat(ledger.balanceOf(cash.getId())).isEqualByComparingTo("200.00");
        assertThat(ledger.balanceOf(sales.getId())).isEqualByComparingTo("-200.00");
        assertThat(entries.count()).isEqualTo(2);
        assertThat(accounts.findById(cash.getId()).orElseThrow().getVersion()).isEqualTo(2L);
    }

    @Test
    void concurrentPostsWithTheSameIdempotencyKeyPostExactlyOnce() throws Exception {
        Account cash = accounts.save(new Account("Cash", "USD", AccountType.ASSET));
        Account sales = accounts.save(new Account("Sales", "USD", AccountType.REVENUE));
        PostEntryCommand command = new PostEntryCommand("idempotent sale", List.of(
                new PostEntryCommand.Line(cash.getId(), new BigDecimal("100.00"), "USD"),
                new PostEntryCommand.Line(sales.getId(), new BigDecimal("-100.00"), "USD")));

        Set<Long> entryIds = ConcurrentHashMap.newKeySet();
        List<Throwable> failures = runConcurrently(5,
                () -> entryIds.add(ledger.post(command, "same-key").getId()));

        assertThat(failures).isEmpty();
        assertThat(entryIds).hasSize(1);
        assertThat(ledger.balanceOf(cash.getId())).isEqualByComparingTo("100.00");
    }

    private static List<Throwable> runConcurrently(int threads, Runnable action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    action.run();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(finished).as("all worker threads finished within 30s").isTrue();
        return failures;
    }
}
