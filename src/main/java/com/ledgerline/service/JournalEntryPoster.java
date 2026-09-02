package com.ledgerline.service;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.JournalEntry;
import com.ledgerline.domain.Money;
import com.ledgerline.domain.PostingLine;
import com.ledgerline.repo.AccountRepository;
import com.ledgerline.repo.IdempotencyRecordRepository;
import com.ledgerline.repo.JournalEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes one entry in a single transaction. Concurrent posts that touch the
 * same account serialize on its {@code SELECT ... FOR UPDATE} row lock, so each
 * one reads a fresh account version and there is no optimistic conflict to
 * retry. If an idempotency key is given, its row is inserted in this same
 * transaction: a duplicate key fails the insert, this transaction rolls back,
 * and {@link LedgerService} returns the winner's entry.
 */
@Component
public class JournalEntryPoster {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdempotencyRecordRepository idempotencyRecords;

    public JournalEntryPoster(AccountRepository accounts,
                              JournalEntryRepository entries,
                              IdempotencyRecordRepository idempotencyRecords) {
        this.accounts = accounts;
        this.entries = entries;
        this.idempotencyRecords = idempotencyRecords;
    }

    @Transactional
    public JournalEntry post(PostEntryCommand command, String idempotencyKey) {
        Map<Long, Account> locked = lockAccountsInIdOrder(command);

        List<PostingLine> postings = new ArrayList<>();
        for (PostEntryCommand.Line line : command.lines()) {
            Account account = locked.get(line.accountId());
            postings.add(new PostingLine(account, new Money(line.amount(), line.currency())));
        }

        JournalEntry entry = JournalEntry.create(command.description(), postings);
        locked.values().forEach(Account::recordPosting);
        JournalEntry saved = entries.saveAndFlush(entry);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRecords.insertRecord(idempotencyKey.strip(), saved.getId());
        }
        return saved;
    }

    private Map<Long, Account> lockAccountsInIdOrder(PostEntryCommand command) {
        List<Long> ids = command.lines().stream()
                .map(PostEntryCommand.Line::accountId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Account> locked = new LinkedHashMap<>();
        for (Long id : ids) {
            Account account = accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> NotFoundException.account(id));
            locked.put(id, account);
        }
        return locked;
    }
}
