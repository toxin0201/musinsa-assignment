package com.musinsa.point.point.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 포인트 정책 값. 1회 적립 한도 · 보유 한도 · 만료 규칙을 코드가 아닌 설정에서 읽어 재배포 없이 바꿀 수 있게 한다.
 */
@ConfigurationProperties(prefix = "point")
public record PointPolicyProperties(Earn earn, Balance balance, Expiry expiry) {

    public record Earn(long minAmount, long maxAmount) {
    }

    public record Balance(long defaultMax) {
    }

    /** maxYears 는 "그 해가 되기 전까지"라는 뜻의 상한이다. 일수가 아니라 달력으로 비교한다. */
    public record Expiry(int defaultDays, int minDays, int maxYears) {
    }
}
