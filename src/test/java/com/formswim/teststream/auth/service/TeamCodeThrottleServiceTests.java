package com.formswim.teststream.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TeamCodeThrottleServiceTests {

    @Test
    void failuresResetAfterInactivityWindow() {
        Instant start = Instant.parse("2026-03-19T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        TeamCodeThrottleService throttle = new TeamCodeThrottleService(clock);

        String ip = "203.0.113.10";

        throttle.recordFailure(ip);
        throttle.recordFailure(ip);
        assertThat(throttle.isBlocked(ip)).isFalse();

        // After > 1 hour of no failures, counts should reset.
        clock.setInstant(start.plus(TeamCodeThrottleService.INACTIVITY_RESET_AFTER).plusSeconds(1));

        throttle.recordFailure(ip);
        throttle.recordFailure(ip);
        throttle.recordFailure(ip);
        throttle.recordFailure(ip);

        // If earlier failures had not reset, this would have hit the 5-failure threshold and blocked.
        assertThat(throttle.isBlocked(ip)).isFalse();

        throttle.recordFailure(ip);
        assertThat(throttle.isBlocked(ip)).isTrue();
    }

    @Test
    void blockStillAppliesWithinBlockDuration() {
        Instant start = Instant.parse("2026-03-19T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        TeamCodeThrottleService throttle = new TeamCodeThrottleService(clock);

        String ip = "127.0.0.1";
        for (int i = 0; i < TeamCodeThrottleService.MAX_FAILURES; i++) {
            throttle.recordFailure(ip);
        }

        assertThat(throttle.isBlocked(ip)).isTrue();

        clock.setInstant(start.plus(TeamCodeThrottleService.BLOCK_DURATION).minusSeconds(1));
        assertThat(throttle.isBlocked(ip)).isTrue();

        clock.setInstant(start.plus(TeamCodeThrottleService.BLOCK_DURATION).plusSeconds(1));
        assertThat(throttle.isBlocked(ip)).isFalse();
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
