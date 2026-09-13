package com.musinsa.point.point.command;

import java.util.List;

public record UseCancelResult(String pointKey, long canceledAmount, List<UseCancelRestoration> restorations,
        long balance, long remainingCancelableAmount) {
}
