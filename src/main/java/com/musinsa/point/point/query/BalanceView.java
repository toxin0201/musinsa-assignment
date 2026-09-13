package com.musinsa.point.point.query;

import java.time.Instant;

/** 잔액은 시각에 따라 달라진다(만료). 어느 시점 기준인지를 함께 내보낸다. */
public record BalanceView(long balance, Instant asOf) {
}
