package com.musinsa.point.point.query;

/** 적립 한 건이 주문 하나에서 움직인 금액. netUsedAmount 가 지금 그 주문에 묶여 있는 금액이다. */
public record OrderUsage(String orderNo, String usePointKey, long usedAmount, long canceledAmount,
        long netUsedAmount) {
}
