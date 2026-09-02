package com.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One posting against one account. A positive amount is a debit, a negative
 * amount is a credit. Currency is not stored per line: every line in an entry
 * shares the entry's currency, which the factory has already checked against
 * each account.
 */
@Entity
@Table(name = "journal_line")
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private JournalEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    protected JournalLine() {
        // for JPA
    }

    JournalLine(JournalEntry entry, Account account, BigDecimal amount) {
        this.entry = entry;
        this.account = account;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public JournalEntry getEntry() {
        return entry;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
