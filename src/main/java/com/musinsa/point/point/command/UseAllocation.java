package com.musinsa.point.point.command;

/** 사용 금액이 적립 건 하나에서 얼마나 빠져나갔는지. 적립 건은 그 건을 만든 거래의 pointKey 로 가리킨다. */
public record UseAllocation(String earningPointKey, long amount) {
}
