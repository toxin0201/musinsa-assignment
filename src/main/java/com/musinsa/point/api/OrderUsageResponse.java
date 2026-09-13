package com.musinsa.point.api;

import com.musinsa.point.point.query.OrderUsage;

public record OrderUsageResponse(String orderNo, String usePointKey, long usedAmount, long canceledAmount,
        long netUsedAmount) {

    public static OrderUsageResponse from(OrderUsage usage) {
        return new OrderUsageResponse(usage.orderNo(), usage.usePointKey(), usage.usedAmount(),
                usage.canceledAmount(), usage.netUsedAmount());
    }
}
