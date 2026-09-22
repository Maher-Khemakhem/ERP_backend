package com.example.erp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory throttling for the public login endpoint.
 *
 * The two independent buckets make it harder to bypass throttling by rotating
 * usernames or source addresses. This is intentionally a login limiter, not a
 * general API limiter; business traffic should not share the login quota.
 */
@Component
class LoginRateLimiter {

    private final int ipCapacity;
    private final long ipWindowNanos;
    private final int usernameCapacity;
    private final long usernameWindowNanos;
    private final int maxBuckets;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    LoginRateLimiter(
            @Value("${app.security.login-rate-limit.ip-capacity:30}") int ipCapacity,
            @Value("${app.security.login-rate-limit.ip-window-seconds:60}") long ipWindowSeconds,
            @Value("${app.security.login-rate-limit.username-capacity:10}") int usernameCapacity,
            @Value("${app.security.login-rate-limit.username-window-seconds:60}") long usernameWindowSeconds,
            @Value("${app.security.login-rate-limit.max-buckets:10000}") int maxBuckets) {
        this.ipCapacity = requirePositive(ipCapacity, "ip-capacity");
        this.ipWindowNanos = secondsToNanos(ipWindowSeconds, "ip-window-seconds");
        this.usernameCapacity = requirePositive(usernameCapacity, "username-capacity");
        this.usernameWindowNanos = secondsToNanos(usernameWindowSeconds, "username-window-seconds");
        this.maxBuckets = requirePositive(maxBuckets, "max-buckets");
    }

    void check(String clientIp, String username) {
        String normalizedIp = normalize(clientIp, "unknown");
        String normalizedUsername = normalize(username, "unknown");

        long ipRetryAfter = bucket("ip:" + normalizedIp, ipCapacity, ipWindowNanos).tryConsume();
        long usernameRetryAfter = bucket("username:" + normalizedUsername, usernameCapacity, usernameWindowNanos)
                .tryConsume();
        long retryAfter = Math.max(ipRetryAfter, usernameRetryAfter);
        if (retryAfter > 0) {
            throw new LoginRateLimitExceededException(retryAfter);
        }
        cleanupIfNeeded();
    }

    private Bucket bucket(String key, int capacity, long windowNanos) {
        return buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, windowNanos));
    }

    private void cleanupIfNeeded() {
        long operation = operations.incrementAndGet();
        if (operation % 500 != 0 || buckets.size() <= maxBuckets) {
            return;
        }

        int toRemove = buckets.size() - maxBuckets;
        buckets.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastTouchedNanos()))
                .limit(toRemove)
                .forEach(entry -> buckets.remove(entry.getKey(), entry.getValue()));
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long secondsToNanos(long seconds, String name) {
        if (seconds <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        try {
            return Math.multiplyExact(seconds, 1_000_000_000L);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " is too large", ex);
        }
    }

    private static final class Bucket {
        private final int capacity;
        private final long windowNanos;
        private double tokens;
        private long lastRefillNanos;
        private long lastTouchedNanos;

        private Bucket(int capacity, long windowNanos) {
            this.capacity = capacity;
            this.windowNanos = windowNanos;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastTouchedNanos = lastRefillNanos;
        }

        private synchronized long tryConsume() {
            long now = System.nanoTime();
            long elapsed = Math.max(0, now - lastRefillNanos);
            tokens = Math.min(capacity, tokens + ((double) elapsed / windowNanos) * capacity);
            lastRefillNanos = now;
            lastTouchedNanos = now;

            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }

            double missingTokens = 1 - tokens;
            return Math.max(1, (long) Math.ceil((missingTokens / capacity) * windowNanos / 1_000_000_000d));
        }

        private synchronized long lastTouchedNanos() {
            return lastTouchedNanos;
        }
    }
}
