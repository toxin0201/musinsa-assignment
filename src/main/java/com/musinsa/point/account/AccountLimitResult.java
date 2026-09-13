package com.musinsa.point.account;

/** maxBalance 가 null 이면 설정 기본값을 따른다는 뜻이다. */
public record AccountLimitResult(String memberId, Long maxBalance) {
}
