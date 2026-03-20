package com.formswim.teststream.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class TeamCodeThrottleService {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    static final Duration INACTIVITY_RESET_AFTER = Duration.ofHours(1);

    private final Cache<String, AttemptRecord> attempts = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();
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

        Instant now = Instant.now(clock);
        AttemptRecord record = attempts.get(key, ip -> new AttemptRecord());
        synchronized (record) {
            boolean blockExpired = record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt);
            boolean inactiveTooLong = record.lastFailureAt != null
                && record.blockExpiresAt == null
                && Duration.between(record.lastFailureAt, now).compareTo(INACTIVITY_RESET_AFTER) > 0;

            if (blockExpired || inactiveTooLong) {
                record.failureCount = 0;
                record.blockExpiresAt = null;
                record.lastFailureAt = null;
            }

            record.failureCount++;
            record.lastFailureAt = now;
            if (record.failureCount >= MAX_FAILURES) {
                record.blockExpiresAt = now.plus(BLOCK_DURATION);
            }
        }
    }

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
            if (record.blockExpiresAt != null) {
                if (now.isAfter(record.blockExpiresAt)) {
                    attempts.invalidate(key);
                    return false;
                }
                return true;
            }

            if (record.lastFailureAt != null
                && Duration.between(record.lastFailureAt, now).compareTo(INACTIVITY_RESET_AFTER) > 0) {
                attempts.invalidate(key);
            }

            return false;
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
        private int failureCount;
        private Instant blockExpiresAt;
        private Instant lastFailureAt;
    }
}
