package com.musinsa.point.point.command;

import com.musinsa.point.point.EarningKind;
import java.time.Instant;

public record EarnResult(String pointKey, long amount, EarningKind kind, Instant expiresAt, long balance) {
}
