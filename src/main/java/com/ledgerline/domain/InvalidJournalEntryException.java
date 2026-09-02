package com.ledgerline.domain;

/**
 * Raised by the {@link JournalEntry} factory when a proposed entry breaks a
 * bookkeeping invariant: fewer than two lines, mixed currencies, or a signed
 * line total that is not exactly zero. Surfaces to callers as HTTP 422.
 */
public class InvalidJournalEntryException extends RuntimeException {

    public InvalidJournalEntryException(String message) {
        super(message);
    }
}
