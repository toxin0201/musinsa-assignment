package com.musinsa.point.api;

import com.musinsa.point.point.command.UseCancelResult;
import java.util.List;

public record UseCancelResponse(String pointKey, long canceledAmount,
        List<UseCancelRestorationResponse> restorations, long balance, long remainingCancelableAmount) {

    public static UseCancelResponse from(UseCancelResult result) {
        return new UseCancelResponse(result.pointKey(), result.canceledAmount(),
                result.restorations().stream().map(UseCancelRestorationResponse::from).toList(),
                result.balance(), result.remainingCancelableAmount());
    }
}
