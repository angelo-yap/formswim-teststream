package com.formswim.teststream.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class TeamCodeThrottleService {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    static final Duration INACTIVITY_RESET_AFTER = Duration.ofHours(1);

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public TeamCodeThrottleService() {
        this(Clock.systemUTC());
    }

    TeamCodeThrottleService(Clock clock) {
        this.clock = clock;
    }

    public void recordFailure(String clientIp) {
        String key = normalizeClientIp(clientIp);
        if (key == null) {
            return;
        }

        attempts.compute(key, (ip, existing) -> {
            Instant now = Instant.now(clock);
            AttemptRecord record = existing;

            boolean blockExpired = record != null && record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt);
            boolean inactiveTooLong = record != null
                && record.lastFailureAt != null
                && record.blockExpiresAt == null
                && Duration.between(record.lastFailureAt, now).compareTo(INACTIVITY_RESET_AFTER) > 0;

            if (record == null || blockExpired || inactiveTooLong) {
                record = new AttemptRecord();
            }

            record.failureCount++;
            record.lastFailureAt = now;
            if (record.failureCount >= MAX_FAILURES) {
                record.blockExpiresAt = now.plus(BLOCK_DURATION);
            }

            return record;
        });
    }

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

        if (record.blockExpiresAt != null) {
            if (now.isAfter(record.blockExpiresAt)) {
                attempts.remove(key);
                return false;
            }
            return true;
        }

        if (record.lastFailureAt != null
            && Duration.between(record.lastFailureAt, now).compareTo(INACTIVITY_RESET_AFTER) > 0) {
            attempts.remove(key);
        }

        return false;
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
        private int failureCount;
        private Instant blockExpiresAt;
        private Instant lastFailureAt;
    }
}
