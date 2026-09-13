package com.musinsa.point.api;

import com.musinsa.point.point.command.EarnCancelResult;

public record EarnCancelResponse(String pointKey, long canceledAmount, long balance) {

    public static EarnCancelResponse from(EarnCancelResult result) {
        return new EarnCancelResponse(result.pointKey(), result.canceledAmount(), result.balance());
    }
}
