package com.ledgerline.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable amount plus ISO 4217 currency code.
 *
 * <p>The sign carries the direction of a ledger line: a positive amount is a
 * debit, a negative amount is a credit. Arithmetic across currencies is
 * rejected rather than silently coerced.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "currency must be an ISO 4217 alpha-3 code, got: " + currency);
        }
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean sameCurrencyAs(Money other) {
        return currency.equals(other.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!sameCurrencyAs(other)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
