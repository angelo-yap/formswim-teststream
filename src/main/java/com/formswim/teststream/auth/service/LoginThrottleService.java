package com.formswim.teststream.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class LoginThrottleService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW_DURATION = Duration.ofMinutes(30);
    static final Duration INITIAL_BLOCK_DURATION = Duration.ofMinutes(15);
    static final Duration MAX_BLOCK_DURATION = Duration.ofHours(24);

    private final Cache<String, AttemptRecord> attempts = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(MAX_BLOCK_DURATION)
        .build();
    private final Clock clock;

    public LoginThrottleService() {
        this(Clock.systemUTC());
    }

    LoginThrottleService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records a login attempt (successful or not). Used to keep the rolling window "alive" so state
     * only resets after {@link #WINDOW_DURATION} with no login posts from the IP.
     */
    public void recordAttempt(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return;
        }

        Instant now = Instant.now(clock);
        AttemptRecord record = attempts.get(key, ip -> new AttemptRecord());
        synchronized (record) {
            pruneOldFailures(record, now);

            if (!isCurrentlyBlocked(record, now) && record.failures.isEmpty() && shouldReset(record, now)) {
                attempts.invalidate(key);
                return;
            }

            record.lastAttemptAt = now;
        }
    }

    /** Records a failed login attempt for the given IP. */
    public void recordFailure(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return;
        }

        Instant now = Instant.now(clock);
        AttemptRecord record = attempts.get(key, ip -> new AttemptRecord());
        synchronized (record) {
            record.lastAttemptAt = now;
            pruneOldFailures(record, now);

            // If already blocked, don't extend the block on each request.
            if (isCurrentlyBlocked(record, now)) {
                return;
            }

            record.failures.addLast(now);
            pruneOldFailures(record, now);

            if (record.failures.size() >= MAX_FAILURES) {
                Duration next = record.currentBlockDuration == null
                    ? INITIAL_BLOCK_DURATION
                    : record.currentBlockDuration.multipliedBy(2);
                if (next.compareTo(MAX_BLOCK_DURATION) > 0) {
                    next = MAX_BLOCK_DURATION;
                }

                record.currentBlockDuration = next;
                record.blockExpiresAt = now.plus(next);
            }
        }
    }

    /** Resets all throttling state for the IP, typically after a successful login. */
    public void resetFailures(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return;
        }

        attempts.invalidate(key);
    }

    public boolean isBlocked(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return false;
        }

        AttemptRecord record = attempts.getIfPresent(key);
        if (record == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        synchronized (record) {
            if (record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt)) {
                record.blockExpiresAt = null;
            }

            pruneOldFailures(record, now);
            if (!isCurrentlyBlocked(record, now) && record.failures.isEmpty() && shouldReset(record, now)) {
                attempts.invalidate(key);
                return false;
            }

            return isCurrentlyBlocked(record, now);
        }
    }

    public Duration getRemainingBlockDuration(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return Duration.ZERO;
        }

        AttemptRecord record = attempts.getIfPresent(key);
        if (record == null || record.blockExpiresAt == null) {
            return Duration.ZERO;
        }

        Instant now = Instant.now(clock);
        synchronized (record) {
            if (record.blockExpiresAt == null || now.isAfter(record.blockExpiresAt)) {
                return Duration.ZERO;
            }
            return Duration.between(now, record.blockExpiresAt);
        }
    }

    private boolean shouldReset(AttemptRecord record, Instant now) {
        if (record.lastAttemptAt == null) {
            return true;
        }
        return Duration.between(record.lastAttemptAt, now).compareTo(WINDOW_DURATION) > 0;
    }

    private boolean isCurrentlyBlocked(AttemptRecord record, Instant now) {
        return record.blockExpiresAt != null && !now.isAfter(record.blockExpiresAt);
    }

    private void pruneOldFailures(AttemptRecord record, Instant now) {
        Instant cutoff = now.minus(WINDOW_DURATION);
        while (!record.failures.isEmpty()) {
            Instant head = record.failures.peekFirst();
            if (head != null && head.isBefore(cutoff)) {
                record.failures.removeFirst();
                continue;
            }
            break;
        }

        if (record.failures.isEmpty() && record.blockExpiresAt == null && record.lastAttemptAt != null
            && Duration.between(record.lastAttemptAt, now).compareTo(WINDOW_DURATION) > 0) {
            record.currentBlockDuration = null;
        }
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null) {
            return null;
        }

        String trimmed = clientIp.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        return trimmed;
    }

    private static final class AttemptRecord {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockExpiresAt;
        private Duration currentBlockDuration;
        private Instant lastAttemptAt;
    }
}