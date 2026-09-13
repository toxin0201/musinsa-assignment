package com.musinsa.point.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트가 시각을 직접 잡고 흘려보낼 수 있게 하는 시계. 만료 시나리오를 대기 없이 재현한다. */
public class MutableClock extends Clock {

    public static final Instant DEFAULT_NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final ZoneId zone;
    private Instant now;

    public MutableClock() {
        this(DEFAULT_NOW, ZoneOffset.UTC);
    }

    private MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void fixedAt(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = this.now.plus(duration);
    }

    public void reset() {
        this.now = DEFAULT_NOW;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now, newZone);
    }
}
