package com.musinsa.point.point.query;

import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.EarningStatus;
import java.time.Instant;

public record EarningSummary(String pointKey, EarningKind kind, EarningStatus status, long originalAmount,
        long remainingAmount, Instant earnedAt, Instant expiresAt) {
}
