package com.ledgerline.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The sum-to-zero invariant is the whole point of double-entry, so it is
 * pinned here directly against the domain factory with no Spring or database.
 */
class JournalEntryBalanceTest {

    private final Account cash = new Account("Cash", "USD", AccountType.ASSET);
    private final Account sales = new Account("Sales", "USD", AccountType.REVENUE);
    private final Account cashEur = new Account("Cash EUR", "EUR", AccountType.ASSET);

    @Test
    void aBalancedTwoLineEntryConstructs() {
        JournalEntry entry = JournalEntry.create("cash sale", List.of(
                new PostingLine(cash, Money.of("100.00", "USD")),
                new PostingLine(sales, Money.of("-100.00", "USD"))));

        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.getCurrency()).isEqualTo("USD");
        assertThat(entry.getLines())
                .extracting(line -> line.getAmount().doubleValue())
                .containsExactly(100.0, -100.0);
    }

    @Test
    void aBalancedEntryWithMoreThanTwoLinesConstructs() {
        Account fees = new Account("Fees", "USD", AccountType.EXPENSE);

        JournalEntry entry = JournalEntry.create("sale with fee", List.of(
                new PostingLine(cash, Money.of("97.00", "USD")),
                new PostingLine(fees, Money.of("3.00", "USD")),
                new PostingLine(sales, Money.of("-100.00", "USD"))));

        assertThat(entry.getLines()).hasSize(3);
    }

    @Test
    void anUnbalancedEntryIsRejected() {
        assertThatThrownBy(() -> JournalEntry.create("bad", List.of(
                new PostingLine(cash, Money.of("100.00", "USD")),
                new PostingLine(sales, Money.of("-90.00", "USD")))))
                .isInstanceOf(InvalidJournalEntryException.class)
                .hasMessageContaining("does not balance")
                .hasMessageContaining("10.00");
    }

    @Test
    void aSingleLineEntryIsRejected() {
        assertThatThrownBy(() -> JournalEntry.create("one line", List.of(
                new PostingLine(cash, Money.of("0.00", "USD")))))
                .isInstanceOf(InvalidJournalEntryException.class)
                .hasMessageContaining("at least two lines");
    }

    @Test
    void anEntryMixingCurrenciesIsRejected() {
        assertThatThrownBy(() -> JournalEntry.create("mixed", List.of(
                new PostingLine(cash, Money.of("100.00", "USD")),
                new PostingLine(cashEur, Money.of("-100.00", "EUR")))))
                .isInstanceOf(InvalidJournalEntryException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void aLineWhoseCurrencyDoesNotMatchItsAccountIsRejected() {
        assertThatThrownBy(() -> JournalEntry.create("account mismatch", List.of(
                new PostingLine(cash, Money.of("100.00", "EUR")),
                new PostingLine(cashEur, Money.of("-100.00", "EUR")))))
                .isInstanceOf(InvalidJournalEntryException.class)
                .hasMessageContaining("does not match account");
    }
}
