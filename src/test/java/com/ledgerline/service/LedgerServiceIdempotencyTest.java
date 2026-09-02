package com.ledgerline.service;

import com.ledgerline.domain.IdempotencyRecord;
import com.ledgerline.domain.JournalEntry;
import com.ledgerline.repo.AccountRepository;
import com.ledgerline.repo.IdempotencyRecordRepository;
import com.ledgerline.repo.JournalEntryRepository;
import com.ledgerline.repo.JournalLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The idempotency decision lives in {@link LedgerService}: a known key returns
 * the original entry and never reaches the poster. Pure unit test, no context.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceIdempotencyTest {

    @Mock private AccountRepository accounts;
    @Mock private JournalEntryRepository entries;
    @Mock private JournalLineRepository lines;
    @Mock private IdempotencyRecordRepository idempotencyRecords;
    @Mock private JournalEntryPoster poster;

    @Mock private JournalEntry postedEntry;

    private LedgerService service() {
        return new LedgerService(accounts, entries, lines, idempotencyRecords, poster);
    }

    private PostEntryCommand command() {
        return new PostEntryCommand("sale", List.of(
                new PostEntryCommand.Line(1L, new java.math.BigDecimal("100.00"), "USD"),
                new PostEntryCommand.Line(2L, new java.math.BigDecimal("-100.00"), "USD")));
    }

    @Test
    void firstCallPostsAndSecondCallWithTheSameKeyReturnsTheOriginalWithoutPostingAgain() {
        LedgerService service = service();
        PostEntryCommand command = command();

        when(idempotencyRecords.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyRecord("key-1", 42L, Instant.now())));
        when(poster.post(command, "key-1")).thenReturn(postedEntry);
        when(entries.findWithLinesById(42L)).thenReturn(Optional.of(postedEntry));

        JournalEntry first = service.post(command, "key-1");
        assertThat(first).isSameAs(postedEntry);

        JournalEntry second = service.post(command, "key-1");

        assertThat(second).isSameAs(first);
        verify(poster, times(1)).post(command, "key-1");
    }

    @Test
    void aKnownKeyShortCircuitsBeforeAnyPost() {
        LedgerService service = service();

        when(idempotencyRecords.findById("seen"))
                .thenReturn(Optional.of(new IdempotencyRecord("seen", 7L, Instant.now())));
        when(entries.findWithLinesById(7L)).thenReturn(Optional.of(postedEntry));

        JournalEntry result = service.post(command(), "seen");

        assertThat(result).isSameAs(postedEntry);
        verify(poster, never()).post(any(), any());
    }

    @Test
    void aBlankKeyIsTreatedAsNoKey() {
        LedgerService service = service();
        PostEntryCommand command = command();
        when(poster.post(command, null)).thenReturn(postedEntry);

        JournalEntry result = service.post(command, "   ");

        assertThat(result).isSameAs(postedEntry);
        verify(poster).post(command, null);
        verify(idempotencyRecords, never()).findById(any());
    }
}
