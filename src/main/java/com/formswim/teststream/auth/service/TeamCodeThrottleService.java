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

            if (record == null || record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt)) {
                record = new AttemptRecord();
            }

            record.failureCount++;
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
        if (record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt)) {
            attempts.remove(key);
            return false;
        }

        return record.blockExpiresAt != null && !now.isAfter(record.blockExpiresAt);
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
    }
}
