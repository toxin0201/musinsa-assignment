package com.musinsa.point.point.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BalanceLimitPolicyTest {

    private final BalanceLimitPolicy policy = new BalanceLimitPolicy(TestPolicies.defaults());

    @Test
    @DisplayName("개인 한도를 두지 않은 회원은 설정 기본 한도를 따른다")
    void memberWithoutPersonalLimitFollowsTheDefault() {
        assertThatCode(() -> policy.validate(999_900, 100, null)).doesNotThrowAnyException();

        assertThat(catchThrowableOfType(() -> policy.validate(1_000_000, 1, null), ApiException.class)
                .getErrorCode()).isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("한도와 딱 같아지는 적립은 허용한다")
    void reachingTheLimitExactlyIsAllowed() {
        assertThatCode(() -> policy.validate(0, 1_000_000, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회원에게 개인 한도가 있으면 설정 기본값보다 그 값이 앞선다")
    void personalLimitWinsOverTheDefault() {
        assertThat(catchThrowableOfType(() -> policy.validate(1_900, 200, 2_000L), ApiException.class)
                .getErrorCode()).isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
        assertThatCode(() -> policy.validate(1_900, 100, 2_000L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개인 한도가 기본값보다 클 수도 있다")
    void personalLimitMayBeHigherThanTheDefault() {
        assertThatCode(() -> policy.validate(1_000_000, 500_000, 5_000_000L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개인 한도가 0 이면 어떤 적립도 한도를 넘는다")
    void zeroPersonalLimitBlocksEveryEarning() {
        assertThat(catchThrowableOfType(() -> policy.validate(0, 1, 0L), ApiException.class).getErrorCode())
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("기본 한도를 설정으로 바꾸면 판정도 따라 바뀐다")
    void defaultLimitComesFromConfigurationNotFromCode() {
        BalanceLimitPolicy tight = new BalanceLimitPolicy(TestPolicies.withDefaultMaxBalance(500));

        assertThatCode(() -> tight.validate(400, 100, null)).doesNotThrowAnyException();
        assertThat(catchThrowableOfType(() -> tight.validate(400, 101, null), ApiException.class).getErrorCode())
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }
}
