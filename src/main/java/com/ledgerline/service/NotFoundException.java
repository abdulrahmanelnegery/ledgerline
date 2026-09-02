package com.ledgerline.service;

/** A referenced account or entry does not exist. Surfaces as HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException account(Long id) {
        return new NotFoundException("account " + id + " not found");
    }

    public static NotFoundException entry(Long id) {
        return new NotFoundException("journal entry " + id + " not found");
    }
}
