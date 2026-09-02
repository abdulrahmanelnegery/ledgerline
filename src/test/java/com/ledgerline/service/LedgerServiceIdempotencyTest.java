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
import org.springframework.dao.DataIntegrityViolationException;

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
 * The service level of idempotency: a known key short-circuits before the
 * poster runs, and a key that loses the race in the poster (a duplicate-key
 * {@link DataIntegrityViolationException}) is resolved by re-reading the
 * winner, not propagated. Pure unit test, no Spring context.
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
    void aKnownKeyReturnsTheStoredEntryAndNeverReachesThePoster() {
        when(idempotencyRecords.findById("seen"))
                .thenReturn(Optional.of(new IdempotencyRecord("seen", 7L, Instant.now())));
        when(entries.findWithLinesById(7L)).thenReturn(Optional.of(postedEntry));

        JournalEntry result = service().post(command(), "seen");

        assertThat(result).isSameAs(postedEntry);
        verify(poster, never()).post(any(), any());
    }

    @Test
    void anUnknownKeyPostsExactlyOnce() {
        PostEntryCommand command = command();
        when(idempotencyRecords.findById("fresh")).thenReturn(Optional.empty());
        when(poster.post(command, "fresh")).thenReturn(postedEntry);

        JournalEntry result = service().post(command, "fresh");

        assertThat(result).isSameAs(postedEntry);
        verify(poster, times(1)).post(command, "fresh");
    }

    @Test
    void losingTheKeyRaceReturnsTheWinnerInsteadOfAnError() {
        PostEntryCommand command = command();
        when(idempotencyRecords.findById("racy"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyRecord("racy", 9L, Instant.now())));
        when(poster.post(command, "racy"))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        when(entries.findWithLinesById(9L)).thenReturn(Optional.of(postedEntry));

        JournalEntry result = service().post(command, "racy");

        assertThat(result).isSameAs(postedEntry);
    }

    @Test
    void aBlankKeyIsTreatedAsNoKey() {
        PostEntryCommand command = command();
        when(poster.post(command, null)).thenReturn(postedEntry);

        JournalEntry result = service().post(command, "   ");

        assertThat(result).isSameAs(postedEntry);
        verify(poster).post(command, null);
        verify(idempotencyRecords, never()).findById(any());
    }
}
