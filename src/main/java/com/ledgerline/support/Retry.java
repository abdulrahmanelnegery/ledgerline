package com.ledgerline.support;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Minimal retry with exponential backoff for a single retryable failure mode.
 *
 * <p>Used on the entry-posting path: if two posts touch the same account and
 * the pessimistic row lock is somehow not held (for example a read path that
 * only took the optimistic {@code @Version} guard), the loser sees an
 * optimistic lock failure and this retries it on a fresh transaction.
 *
 * <p>ponytail: hand-rolled, no resilience library. Swap for Resilience4j only
 * if more than this one call site needs configurable retry policy.
 */
public final class Retry {

    private Retry() {
    }

    public static <T> T withBackoff(int maxAttempts,
                                    long baseDelayMillis,
                                    Predicate<RuntimeException> retryable,
                                    Supplier<T> action) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }

        RuntimeException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!retryable.test(e)) {
                    throw e;
                }
                last = e;
                sleepBackoff(baseDelayMillis, attempt);
            }
        }
        throw last;
    }

    private static void sleepBackoff(long baseDelayMillis, int attempt) {
        if (baseDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(baseDelayMillis * (1L << attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while backing off", e);
        }
    }
}
