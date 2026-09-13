package com.musinsa.point.point.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EarnAmountPolicyTest {

    private final EarnAmountPolicy policy = new EarnAmountPolicy(TestPolicies.defaults());

    private ErrorCode rejectionCodeFor(long amount) {
        return catchThrowableOfType(() -> policy.validate(amount), ApiException.class).getErrorCode();
    }

    @Test
    @DisplayName("1회 적립 최소 · 최대 금액은 그 값까지 허용한다")
    void boundsAreInclusive() {
        assertThatCode(() -> policy.validate(1)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(100_000)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1회 적립 한도를 넘으면 적립 금액 범위 오류로 거절한다")
    void aboveMaximumIsRejectedAsRangeError() {
        assertThat(rejectionCodeFor(100_001)).isEqualTo(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("금액이 0 이하이면 정책 범위를 따지기 전에 잘못된 요청으로 거절한다")
    void zeroOrNegativeIsRejectedAsInvalidRequest() {
        for (long amount : new long[] {0L, -1L, Long.MIN_VALUE}) {
            assertThat(rejectionCodeFor(amount)).isEqualTo(ErrorCode.INVALID_REQUEST);
        }
    }

    @Test
    @DisplayName("표현할 수 있는 가장 큰 금액도 덧셈을 하기 전에 범위 검사에서 걸러진다")
    void hugeAmountIsRejectedBeforeAnyArithmetic() {
        assertThat(rejectionCodeFor(Long.MAX_VALUE)).isEqualTo(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("한도를 설정으로 바꾸면 판정도 따라 바뀐다")
    void limitsComeFromConfigurationNotFromCode() {
        EarnAmountPolicy narrowed = new EarnAmountPolicy(TestPolicies.withEarnRange(10, 20));

        assertThatCode(() -> narrowed.validate(20)).doesNotThrowAnyException();
        assertThatThrownBy(() -> narrowed.validate(21)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> narrowed.validate(9)).isInstanceOf(ApiException.class);
    }
}
