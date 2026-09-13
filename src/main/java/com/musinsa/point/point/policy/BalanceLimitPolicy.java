package com.musinsa.point.point.policy;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import org.springframework.stereotype.Component;

/** 보유 한도 검사. 회원에게 개인 한도가 걸려 있으면 설정 기본값보다 그 값이 앞선다. */
@Component
public class BalanceLimitPolicy {

    private final PointPolicyProperties policies;

    public BalanceLimitPolicy(PointPolicyProperties policies) {
        this.policies = policies;
    }

    /** availableBalance 와 amount 는 모두 1회 적립 한도를 통과한 값이라 더해도 넘치지 않는다. */
    public void validate(long availableBalance, long amount, Long personalMaxBalance) {
        long limit = personalMaxBalance != null ? personalMaxBalance : policies.balance().defaultMax();
        if (availableBalance + amount > limit) {
            throw new ApiException(ErrorCode.BALANCE_LIMIT_EXCEEDED,
                    "보유 한도 %d 를 초과합니다. 현재 사용 가능 잔액 %d".formatted(limit, availableBalance));
        }
    }
}
