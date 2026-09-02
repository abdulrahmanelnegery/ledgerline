package com.ledgerline.service;

import java.math.BigDecimal;
import java.time.Instant;

/** One line in an account's history, with the balance after it was applied. */
public record LedgerRow(
        Long lineId,
        Long entryId,
        Instant postedAt,
        String description,
        BigDecimal amount,
        BigDecimal runningBalance) {
}
