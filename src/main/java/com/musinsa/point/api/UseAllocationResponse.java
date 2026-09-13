package com.musinsa.point.api;

import com.musinsa.point.point.command.UseAllocation;

public record UseAllocationResponse(String earningPointKey, long amount) {

    public static UseAllocationResponse from(UseAllocation allocation) {
        return new UseAllocationResponse(allocation.earningPointKey(), allocation.amount());
    }
}
