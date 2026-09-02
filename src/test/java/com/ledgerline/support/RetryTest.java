package com.ledgerline.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryTest {

    private static final class Retryable extends RuntimeException {
    }

    private static final class Fatal extends RuntimeException {
    }

    @Test
    void returnsImmediatelyWhenTheActionSucceedsFirstTry() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.withBackoff(3, 0L, e -> true, () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesARetryableFailureThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.withBackoff(3, 0L, e -> e instanceof Retryable, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new Retryable();
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls).hasValue(3);
    }

    @Test
    void giveUpAfterMaxAttemptsAndRethrowsTheLastFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.withBackoff(3, 0L, e -> true, () -> {
            calls.incrementAndGet();
            throw new Retryable();
        })).isInstanceOf(Retryable.class);

        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryAFailureThePredicateRejects() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.withBackoff(5, 0L, e -> e instanceof Retryable, () -> {
            calls.incrementAndGet();
            throw new Fatal();
        })).isInstanceOf(Fatal.class);

        assertThat(calls).hasValue(1);
    }
}
