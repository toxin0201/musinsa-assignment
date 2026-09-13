package com.musinsa.point.api;

import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.query.EarningSummary;
import java.time.Instant;

public record EarningSummaryResponse(String pointKey, EarningKind kind, EarningStatus status, long originalAmount,
        long remainingAmount, Instant earnedAt, Instant expiresAt) {

    public static EarningSummaryResponse from(EarningSummary summary) {
        return new EarningSummaryResponse(summary.pointKey(), summary.kind(), summary.status(),
                summary.originalAmount(), summary.remainingAmount(), summary.earnedAt(), summary.expiresAt());
    }
}
