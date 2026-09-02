package com.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A ledger account. Its balance is never stored on the row: it is always the
 * signed sum of the account's posted {@link JournalLine}s, read via query.
 *
 * <p>{@code version} gives optimistic locking on the balance-affecting path.
 * {@code postingCount} is bumped every time the account takes part in a posted
 * entry, which is what forces the version to advance under concurrent posts.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType type;

    @Column(name = "posting_count", nullable = false)
    private long postingCount;

    @Version
    @Column(nullable = false)
    private long version;

    protected Account() {
        // for JPA
    }

    public Account(String name, String currency, AccountType type) {
        this.name = name;
        this.currency = currency;
        this.type = type;
        this.postingCount = 0;
    }

    /** Called when this account participates in a posted entry. */
    public void recordPosting() {
        this.postingCount++;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountType getType() {
        return type;
    }

    public long getPostingCount() {
        return postingCount;
    }

    public long getVersion() {
        return version;
    }
}
