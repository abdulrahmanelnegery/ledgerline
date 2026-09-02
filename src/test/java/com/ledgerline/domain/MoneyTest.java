package com.ledgerline.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void constructsWithAnIso4217Currency() {
        Money money = Money.of("10.00", "USD");

        assertThat(money.amount()).isEqualByComparingTo("10.00");
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsACurrencyThatIsNotThreeUppercaseLetters() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.00"), "usd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO 4217");
    }

    @Test
    void addsTwoAmountsOfTheSameCurrency() {
        Money sum = Money.of("10.00", "USD").add(Money.of("2.50", "USD"));

        assertThat(sum.amount()).isEqualByComparingTo("12.50");
        assertThat(sum.currency()).isEqualTo("USD");
    }

    @Test
    void refusesToAddAcrossCurrencies() {
        assertThatThrownBy(() -> Money.of("10.00", "USD").add(Money.of("10.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }

    @Test
    void reportsWhetherTheAmountIsZeroRegardlessOfScale() {
        assertThat(Money.of("0.00", "USD").isZero()).isTrue();
        assertThat(new Money(BigDecimal.ZERO, "USD").isZero()).isTrue();
        assertThat(Money.of("0.01", "USD").isZero()).isFalse();
    }
}
