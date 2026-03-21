package com.formswim.teststream.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class LoginThrottleServiceTests {

    @Test
    void blocksAfterFiveFailuresWithinWindow() {
        Instant start = Instant.parse("2026-03-19T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginThrottleService throttle = new LoginThrottleService(clock);

        String ip = "203.0.113.10";

        for (int i = 0; i < LoginThrottleService.MAX_FAILURES; i++) {
            throttle.recordFailure(ip);
        }

        assertThat(throttle.isBlocked(ip)).isTrue();
        assertThat(throttle.getRemainingBlockDuration(ip)).isGreaterThan(Duration.ZERO);
    }

    @Test
    void blockDurationDoublesOnRepeatedBlocks() {
        Instant start = Instant.parse("2026-03-19T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginThrottleService throttle = new LoginThrottleService(clock);

        String ip = "127.0.0.1";

        for (int i = 0; i < LoginThrottleService.MAX_FAILURES; i++) {
            throttle.recordFailure(ip);
        }
        Duration first = throttle.getRemainingBlockDuration(ip);
        assertThat(first).isGreaterThan(Duration.ZERO);

        // Let the first block expire but keep failures within the 30-minute window.
        clock.setInstant(start.plus(LoginThrottleService.INITIAL_BLOCK_DURATION).plusSeconds(1));
        assertThat(throttle.isBlocked(ip)).isFalse();

        // One more failure inside the same rolling window should trigger the next block (double duration).
        throttle.recordFailure(ip);
        assertThat(throttle.isBlocked(ip)).isTrue();

        Duration second = throttle.getRemainingBlockDuration(ip);
        assertThat(second).isGreaterThanOrEqualTo(LoginThrottleService.INITIAL_BLOCK_DURATION.multipliedBy(2).minusSeconds(5));
    }

    @Test
    void stateResetsAfterWindowExpiresWithoutFurtherAttempts() {
        Instant start = Instant.parse("2026-03-19T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginThrottleService throttle = new LoginThrottleService(clock);

        String ip = "198.51.100.5";

        throttle.recordFailure(ip);
        throttle.recordFailure(ip);

        // No attempts for > 30 minutes; state should reset on the next attempt.
        clock.setInstant(start.plus(LoginThrottleService.WINDOW_DURATION).plusSeconds(1));
        throttle.recordAttempt(ip);

        // Now only 3 more failures should NOT trigger a block (because earlier failures were out of window).
        throttle.recordFailure(ip);
        throttle.recordFailure(ip);
        throttle.recordFailure(ip);

        assertThat(throttle.isBlocked(ip)).isFalse();
    }

    @Test
    void concurrentAccessDoesNotCorruptState() throws Exception {
        LoginThrottleService throttle = new LoginThrottleService(Clock.systemUTC());
        String ip = "203.0.113.77";

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                futures.add(executor.submit(() -> {
                    throttle.recordFailure(ip);
                    throttle.isBlocked(ip);
                    throttle.getRemainingBlockDuration(ip);
                }));
            }

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        assertThat(throttle.isBlocked(ip)).isTrue();
        assertThat(throttle.getRemainingBlockDuration(ip)).isGreaterThan(Duration.ZERO);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
