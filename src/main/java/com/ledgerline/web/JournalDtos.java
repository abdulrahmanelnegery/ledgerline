package com.ledgerline.web;

import com.ledgerline.domain.JournalEntry;
import com.ledgerline.domain.JournalLine;
import com.ledgerline.service.LedgerRow;
import com.ledgerline.service.PostEntryCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and response shapes for the journal-entry and ledger endpoints. */
final class JournalDtos {

    private JournalDtos() {
    }

    record PostEntryRequest(
            String description,
            @NotNull @Size(min = 2, message = "an entry needs at least two lines") @Valid List<LineRequest> lines) {

        PostEntryCommand toCommand() {
            List<PostEntryCommand.Line> commandLines = lines.stream()
                    .map(line -> new PostEntryCommand.Line(line.accountId(), line.amount(), line.currency()))
                    .toList();
            return new PostEntryCommand(description, commandLines);
        }
    }

    record LineRequest(
            @NotNull Long accountId,
            @NotNull BigDecimal amount,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 alpha-3 code") String currency) {
    }

    record JournalEntryResponse(
            Long id,
            String description,
            String currency,
            Instant postedAt,
            List<LineResponse> lines) {

        static JournalEntryResponse of(JournalEntry entry) {
            List<LineResponse> lineResponses = entry.getLines().stream()
                    .map(LineResponse::of)
                    .toList();
            return new JournalEntryResponse(
                    entry.getId(), entry.getDescription(), entry.getCurrency(),
                    entry.getPostedAt(), lineResponses);
        }
    }

    record LineResponse(Long id, Long accountId, BigDecimal amount) {
        static LineResponse of(JournalLine line) {
            return new LineResponse(line.getId(), line.getAccount().getId(), line.getAmount());
        }
    }

    record LedgerResponse(Long accountId, List<LedgerRow> lines) {
    }
}
