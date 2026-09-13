package com.musinsa.point.point.policy;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import org.springframework.stereotype.Component;

/** 1회 적립 금액 검사. 잔액에 더해 보기 전에 통과해야 하므로 덧셈이 넘칠 일이 없다. */
@Component
public class EarnAmountPolicy {

    private final PointPolicyProperties policies;

    public EarnAmountPolicy(PointPolicyProperties policies) {
        this.policies = policies;
    }

    public void validate(long amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "적립 금액은 1 이상이어야 합니다.");
        }
        long min = policies.earn().minAmount();
        long max = policies.earn().maxAmount();
        if (amount < min || amount > max) {
            throw new ApiException(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE,
                    "1회 적립 금액은 %d 이상 %d 이하여야 합니다.".formatted(min, max));
        }
    }
}
