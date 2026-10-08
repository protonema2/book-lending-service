package com.lexhive.lending.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** A clock tests can move forward, e.g. to make a loan overdue without waiting 14 days. */
public class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant start) {
        // PostgreSQL stores microseconds; keep test instants comparable with what is read back.
        this.instant = start.truncatedTo(ChronoUnit.MICROS);
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock is always UTC");
    }
}
