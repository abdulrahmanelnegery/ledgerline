package com.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps a client-supplied {@code Idempotency-Key} to the entry it created. The
 * key is the primary key, so a concurrent second insert with the same value
 * fails at the database and the caller re-reads the original result.
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key", length = 255)
    private String key;

    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public IdempotencyRecord(String key, Long journalEntryId, Instant createdAt) {
        this.key = key;
        this.journalEntryId = journalEntryId;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public Long getJournalEntryId() {
        return journalEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
