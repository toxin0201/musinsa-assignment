package com.musinsa.point.point.policy;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * 만료 시각 계산. 상한은 "적립일의 N년째 되는 날 전까지"이며 일수 상수가 아니라 달력으로 비교한다.
 * 2월 29일에 적립하면 5년 뒤 같은 날이 없으므로 2월 28일로 맞춰지는데, 그 보정을 그대로 상한으로 쓴다.
 */
@Component
public class ExpiryPolicy {

    private final PointPolicyProperties policies;

    public ExpiryPolicy(PointPolicyProperties policies) {
        this.policies = policies;
    }

    public Instant resolveExpiresAt(Instant earnedAt, Integer expireDays) {
        int days = expireDays != null ? expireDays : policies.expiry().defaultDays();
        int minDays = policies.expiry().minDays();
        if (days < minDays) {
            throw new ApiException(ErrorCode.EXPIRY_OUT_OF_RANGE, "만료일수는 %d 일 이상이어야 합니다.".formatted(minDays));
        }

        Instant expiresAt = earnedAt.plus(days, ChronoUnit.DAYS);
        Instant upperBound = LocalDateTime.ofInstant(earnedAt, ZoneOffset.UTC)
                .plusYears(policies.expiry().maxYears())
                .toInstant(ZoneOffset.UTC);
        if (!expiresAt.isBefore(upperBound)) {
            throw new ApiException(ErrorCode.EXPIRY_OUT_OF_RANGE,
                    "만료일은 적립일로부터 %d 년 미만이어야 합니다.".formatted(policies.expiry().maxYears()));
        }
        return expiresAt;
    }
}
