package com.ledgerline.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * A request to post one journal entry. Currency is per line so a mismatch can
 * be reported precisely; the domain factory rejects the entry if the lines
 * disagree or if a line does not match its account.
 */
public record PostEntryCommand(String description, List<Line> lines) {

    public record Line(Long accountId, BigDecimal amount, String currency) {
    }
}
