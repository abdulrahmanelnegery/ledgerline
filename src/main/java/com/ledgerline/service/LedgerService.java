package com.ledgerline.service;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.AccountType;
import com.ledgerline.domain.IdempotencyRecord;
import com.ledgerline.domain.JournalEntry;
import com.ledgerline.domain.JournalLine;
import com.ledgerline.repo.AccountRepository;
import com.ledgerline.repo.IdempotencyRecordRepository;
import com.ledgerline.repo.JournalEntryRepository;
import com.ledgerline.repo.JournalLineRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for ledger operations. Handles account creation, balance and
 * history reads, and the idempotency short-circuit around posting.
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final JournalEntryPoster poster;

    public LedgerService(AccountRepository accounts,
                         JournalEntryRepository entries,
                         JournalLineRepository lines,
                         IdempotencyRecordRepository idempotencyRecords,
                         JournalEntryPoster poster) {
        this.accounts = accounts;
        this.entries = entries;
        this.lines = lines;
        this.idempotencyRecords = idempotencyRecords;
        this.poster = poster;
    }

    @Transactional
    public Account createAccount(String name, String currency, AccountType type) {
        return accounts.save(new Account(name, currency, type));
    }

    /**
     * Post a balanced entry. A known idempotency key returns the original entry
     * and writes nothing. The real guarantee is in the poster: the key row is
     * inserted in the same transaction as the lines, so a key that loses a
     * concurrent race fails that insert, and this method re-reads and returns
     * the winner rather than surfacing the error.
     */
    public JournalEntry post(PostEntryCommand command, String idempotencyKey) {
        String key = normalize(idempotencyKey);
        if (key != null) {
            JournalEntry existing = findByIdempotencyKey(key);
            if (existing != null) {
                return existing;
            }
        }

        try {
            return poster.post(command, key);
        } catch (DataIntegrityViolationException race) {
            if (key != null) {
                JournalEntry winner = findByIdempotencyKey(key);
                if (winner != null) {
                    return winner;
                }
            }
            throw race;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long accountId) {
        requireAccount(accountId);
        return lines.sumByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> NotFoundException.account(accountId));
    }

    @Transactional(readOnly = true)
    public JournalEntry getEntry(Long entryId) {
        return entries.findWithLinesById(entryId).orElseThrow(() -> NotFoundException.entry(entryId));
    }

    @Transactional(readOnly = true)
    public List<LedgerRow> ledgerOf(Long accountId) {
        requireAccount(accountId);
        List<LedgerRow> rows = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (JournalLine line : lines.findByAccountIdOrderById(accountId)) {
            running = running.add(line.getAmount());
            JournalEntry entry = line.getEntry();
            rows.add(new LedgerRow(
                    line.getId(),
                    entry.getId(),
                    entry.getPostedAt(),
                    entry.getDescription(),
                    line.getAmount(),
                    running));
        }
        return rows;
    }

    private JournalEntry findByIdempotencyKey(String key) {
        return idempotencyRecords.findById(key)
                .map(IdempotencyRecord::getJournalEntryId)
                .flatMap(entries::findWithLinesById)
                .orElse(null);
    }

    private void requireAccount(Long accountId) {
        if (!accounts.existsById(accountId)) {
            throw NotFoundException.account(accountId);
        }
    }

    private static String normalize(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
