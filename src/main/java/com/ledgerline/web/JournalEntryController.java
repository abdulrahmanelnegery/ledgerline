package com.ledgerline.web;

import com.ledgerline.domain.JournalEntry;
import com.ledgerline.service.LedgerService;
import com.ledgerline.web.JournalDtos.JournalEntryResponse;
import com.ledgerline.web.JournalDtos.PostEntryRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
public class JournalEntryController {

    private final LedgerService ledger;

    public JournalEntryController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/journal-entries")
    public ResponseEntity<JournalEntryResponse> post(
            @Valid @RequestBody PostEntryRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            UriComponentsBuilder uri) {

        JournalEntry entry = ledger.post(request.toCommand(), idempotencyKey);
        URI location = uri.path("/journal-entries/{id}").buildAndExpand(entry.getId()).toUri();
        return ResponseEntity.created(location).body(JournalEntryResponse.of(entry));
    }

    @GetMapping("/journal-entries/{id}")
    public JournalEntryResponse get(@PathVariable Long id) {
        return JournalEntryResponse.of(ledger.getEntry(id));
    }
}
