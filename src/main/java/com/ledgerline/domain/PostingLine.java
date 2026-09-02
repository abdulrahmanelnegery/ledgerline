package com.ledgerline.domain;

/**
 * Input to {@link JournalEntry#create}: a resolved account paired with the
 * signed {@link Money} to post against it.
 */
public record PostingLine(Account account, Money money) {
}
