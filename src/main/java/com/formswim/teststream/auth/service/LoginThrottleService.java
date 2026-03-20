package com.formswim.teststream.auth.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginThrottleService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW_DURATION = Duration.ofMinutes(30);
    static final Duration INITIAL_BLOCK_DURATION = Duration.ofMinutes(15);
    static final Duration MAX_BLOCK_DURATION = Duration.ofHours(24);

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
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

        attempts.compute(key, (ip, existing) -> {
            Instant now = Instant.now(clock);
            AttemptRecord record = existing;

            if (record == null) {
                record = new AttemptRecord();
            }

            pruneOldFailures(record, now);

            if (!isCurrentlyBlocked(record, now) && record.failures.isEmpty() && shouldReset(record, now)) {
                return null;
            }

            record.lastAttemptAt = now;

            return record;
        });
    }

    /** Records a failed login attempt for the given IP. */
    public void recordFailure(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return;
        }

        attempts.compute(key, (ip, existing) -> {
            Instant now = Instant.now(clock);
            AttemptRecord record = existing;

            if (record == null) {
                record = new AttemptRecord();
            }

            record.lastAttemptAt = now;
            pruneOldFailures(record, now);

            // If already blocked, don't extend the block on each request.
            if (isCurrentlyBlocked(record, now)) {
                return record;
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

            return record;
        });
    }

    /** Resets all throttling state for the IP, typically after a successful login. */
    public void resetFailures(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return;
        }

        attempts.remove(key);
    }

    public boolean isBlocked(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return false;
        }

        AttemptRecord record = attempts.get(key);
        if (record == null) {
            return false;
        }

        Instant now = Instant.now(clock);

        if (record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt)) {
            record.blockExpiresAt = null;
        }

        pruneOldFailures(record, now);
        if (!isCurrentlyBlocked(record, now) && record.failures.isEmpty() && shouldReset(record, now)) {
            attempts.remove(key);
            return false;
        }

        return isCurrentlyBlocked(record, now);
    }

    public Duration getRemainingBlockDuration(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return Duration.ZERO;
        }

        AttemptRecord record = attempts.get(key);
        if (record == null || record.blockExpiresAt == null) {
            return Duration.ZERO;
        }

        Instant now = Instant.now(clock);
        if (now.isAfter(record.blockExpiresAt)) {
            return Duration.ZERO;
        }

        return Duration.between(now, record.blockExpiresAt);
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