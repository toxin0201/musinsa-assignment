package com.musinsa.point.point.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpiryPolicyTest {

    private static final Instant EARNED_AT = Instant.parse("2026-09-13T00:00:00Z");

    private final ExpiryPolicy policy = new ExpiryPolicy(TestPolicies.defaults());

    private ErrorCode rejectionCodeFor(Integer expireDays) {
        return catchThrowableOfType(ApiException.class, () -> policy.resolveExpiresAt(EARNED_AT, expireDays))
                .getErrorCode();
    }

    @Test
    @DisplayName("만료일수를 적지 않으면 기본 365일이 붙는다")
    void missingExpireDaysFallsBackToTheDefault() {
        assertThat(policy.resolveExpiresAt(EARNED_AT, null)).isEqualTo(Instant.parse("2027-09-13T00:00:00Z"));
    }

    @Test
    @DisplayName("만료일수는 하루부터 가능하다")
    void oneDayIsAllowed() {
        assertThat(policy.resolveExpiresAt(EARNED_AT, 1)).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
    }

    @Test
    @DisplayName("당일 만료나 과거 만료는 만료일수 범위 오류로 거절한다")
    void zeroOrNegativeDaysIsRejected() {
        assertThat(rejectionCodeFor(0)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
        assertThat(rejectionCodeFor(-1)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("만료 시각이 적립일의 5년째 되는 날보다 앞이면 통과하고 그 날이 되면 거절한다")
    void fiveYearsIsAnExclusiveUpperBound() {
        assertThat(policy.resolveExpiresAt(EARNED_AT, 1_825)).isEqualTo(Instant.parse("2031-09-12T00:00:00Z"));
        assertThat(rejectionCodeFor(1_826)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("2월 29일에 적립하면 5년째 되는 날은 2월 28일로 맞춰진다")
    void leapDayEarningShiftsTheFiveYearBoundaryToTheTwentyEighth() {
        Instant leapDay = Instant.parse("2024-02-29T00:00:00Z");
        ExpiryPolicy leapPolicy = new ExpiryPolicy(TestPolicies.defaults());

        assertThat(leapPolicy.resolveExpiresAt(leapDay, 1_825)).isEqualTo(Instant.parse("2029-02-27T00:00:00Z"));
        assertThat(catchThrowableOfType(ApiException.class, () -> leapPolicy.resolveExpiresAt(leapDay, 1_826))
                .getErrorCode()).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("아주 큰 만료일수를 넣어도 날짜 계산이 넘치지 않고 범위 오류로 끝난다")
    void hugeExpireDaysIsRejectedWithoutOverflow() {
        assertThat(rejectionCodeFor(Integer.MAX_VALUE)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("만료 기본값과 상한을 설정으로 바꾸면 판정도 따라 바뀐다")
    void expiryRulesComeFromConfigurationNotFromCode() {
        ExpiryPolicy shortLived = new ExpiryPolicy(TestPolicies.withExpiry(30, 7, 1));

        assertThat(shortLived.resolveExpiresAt(EARNED_AT, null)).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
        assertThat(catchThrowableOfType(ApiException.class, () -> shortLived.resolveExpiresAt(EARNED_AT, 6))
                .getErrorCode()).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
        assertThat(shortLived.resolveExpiresAt(EARNED_AT, 364)).isEqualTo(Instant.parse("2027-09-12T00:00:00Z"));
        assertThat(catchThrowableOfType(ApiException.class, () -> shortLived.resolveExpiresAt(EARNED_AT, 365))
                .getErrorCode()).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("적립 시각의 시·분·초가 만료 시각에 그대로 유지된다")
    void timeOfDayIsPreserved() {
        Instant afternoon = Instant.parse("2026-09-13T15:30:45Z");

        assertThat(policy.resolveExpiresAt(afternoon, 10)).isEqualTo(Instant.parse("2026-09-23T15:30:45Z"));
    }
}
