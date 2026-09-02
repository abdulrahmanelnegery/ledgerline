package com.ledgerline.domain;

/**
 * The five classical account categories in double-entry bookkeeping.
 * Type does not change how a balance is computed here (balance is always the
 * signed sum of posted lines), it is metadata that a reporting layer would use.
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}
