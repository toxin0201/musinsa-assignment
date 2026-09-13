package com.musinsa.point.api;

import com.musinsa.point.point.command.UseResult;
import java.util.List;

public record UseResponse(String pointKey, long amount, List<UseAllocationResponse> allocations, long balance) {

    public static UseResponse from(UseResult result) {
        return new UseResponse(result.pointKey(), result.amount(),
                result.allocations().stream().map(UseAllocationResponse::from).toList(), result.balance());
    }
}
