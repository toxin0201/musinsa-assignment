package com.musinsa.point.point.policy;

/** 정책 테스트에서 쓰는 설정 값 모음. 운영 기본값과 같은 숫자를 쓰되 테스트마다 일부만 바꾼다. */
final class TestPolicies {

    private TestPolicies() {
    }

    static PointPolicyProperties defaults() {
        return new PointPolicyProperties(
                new PointPolicyProperties.Earn(1, 100_000),
                new PointPolicyProperties.Balance(1_000_000),
                new PointPolicyProperties.Expiry(365, 1, 5));
    }

    static PointPolicyProperties withEarnRange(long minAmount, long maxAmount) {
        PointPolicyProperties base = defaults();
        return new PointPolicyProperties(
                new PointPolicyProperties.Earn(minAmount, maxAmount), base.balance(), base.expiry());
    }

    static PointPolicyProperties withDefaultMaxBalance(long defaultMax) {
        PointPolicyProperties base = defaults();
        return new PointPolicyProperties(
                base.earn(), new PointPolicyProperties.Balance(defaultMax), base.expiry());
    }

    static PointPolicyProperties withExpiry(int defaultDays, int minDays, int maxYears) {
        PointPolicyProperties base = defaults();
        return new PointPolicyProperties(
                base.earn(), base.balance(), new PointPolicyProperties.Expiry(defaultDays, minDays, maxYears));
    }
}
