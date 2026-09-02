package com.ledgerline.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A balanced transaction: two or more lines whose signed amounts sum to exactly
 * zero, all in one currency.
 *
 * <p>The invariant is enforced in {@link #create}, not only by a database
 * check, so an unbalanced entry cannot be constructed in memory. Callers that
 * catch {@link InvalidJournalEntryException} translate it to HTTP 422.
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
        // for JPA
    }

    private JournalEntry(String description, String currency) {
        this.description = description;
        this.currency = currency;
        this.postedAt = Instant.now();
    }

    /**
     * Build a balanced entry or fail. Checks, in order: at least two lines, one
     * shared currency that also matches every account, and a signed line total
     * of exactly zero.
     */
    public static JournalEntry create(String description, List<PostingLine> postings) {
        if (postings == null || postings.size() < 2) {
            throw new InvalidJournalEntryException("a journal entry needs at least two lines");
        }

        String currency = postings.get(0).money().currency();
        for (PostingLine posting : postings) {
            if (!posting.money().currency().equals(currency)) {
                throw new InvalidJournalEntryException(
                        "all lines must use the same currency, found " + currency
                                + " and " + posting.money().currency());
            }
            if (!posting.account().getCurrency().equals(currency)) {
                throw new InvalidJournalEntryException(
                        "line currency " + currency + " does not match account "
                                + posting.account().getId() + " (" + posting.account().getCurrency() + ")");
            }
        }

        BigDecimal signedSum = postings.stream()
                .map(posting -> posting.money().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (signedSum.signum() != 0) {
            throw new InvalidJournalEntryException(
                    "entry does not balance, signed sum of lines is " + signedSum.toPlainString());
        }

        JournalEntry entry = new JournalEntry(description, currency);
        for (PostingLine posting : postings) {
            entry.lines.add(new JournalLine(entry, posting.account(), posting.money().amount()));
        }
        return entry;
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public List<JournalLine> getLines() {
        return List.copyOf(lines);
    }
}
