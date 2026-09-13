package com.musinsa.point.api;

/** maxBalance 를 비워 보내면 설정 기본값으로 되돌린다는 뜻이다. 그래서 필수값으로 두지 않는다. */
public record AccountLimitRequest(Long maxBalance) {
}
