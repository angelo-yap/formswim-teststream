package com.formswim.teststream.auth.service;

import com.formswim.teststream.auth.model.AppUser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginThrottleService {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottleService() {
        this(Clock.systemUTC());
    }

    LoginThrottleService(Clock clock) {
        this.clock = clock;
    }

    public void recordFailure(String email) {
        String normalizedEmail = AppUser.normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }

        attempts.compute(normalizedEmail, (key, existing) -> {
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

    public void resetFailures(String email) {
        String normalizedEmail = AppUser.normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }

        attempts.remove(normalizedEmail);
    }

    public boolean isBlocked(String email) {
        String normalizedEmail = AppUser.normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }

        AttemptRecord record = attempts.get(normalizedEmail);
        if (record == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        if (record.blockExpiresAt != null && now.isAfter(record.blockExpiresAt)) {
            attempts.remove(normalizedEmail);
            return false;
        }

        return record.blockExpiresAt != null && !now.isAfter(record.blockExpiresAt);
    }

    private static final class AttemptRecord {
        private int failureCount;
        private Instant blockExpiresAt;
    }
}