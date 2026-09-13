package com.musinsa.point.api;

import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.command.EarnResult;
import java.time.Instant;

public record EarnResponse(String pointKey, long amount, EarningKind kind, Instant expiresAt, long balance) {

    public static EarnResponse from(EarnResult result) {
        return new EarnResponse(result.pointKey(), result.amount(), result.kind(), result.expiresAt(),
                result.balance());
    }
}
