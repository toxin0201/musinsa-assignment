package com.musinsa.point.api;

import jakarta.validation.constraints.Min;

/** expireDays 를 비우면 설정 기본값을 쓴다. 범위 검사는 만료 정책이 맡는다. */
public record EarnRequest(@Min(value = 1, message = "적립 금액은 1 이상이어야 합니다.") long amount, Integer expireDays) {
}
