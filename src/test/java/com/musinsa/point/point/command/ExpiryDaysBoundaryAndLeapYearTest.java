package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExpiryDaysBoundaryAndLeapYearTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M3";
    private static final Instant EARNED_AT = Instant.parse("2026-09-13T00:00:00Z");

    @Autowired
    private EarnService earnService;

    private ErrorCode rejectionCodeFor(int expireDays) {
        return catchThrowableOfType(() -> earnService.earn(MEMBER_ID, 1_000, expireDays), ApiException.class)
                .getErrorCode();
    }

    @Test
    @DisplayName("만료일수를 적지 않으면 기본 365일 뒤에 만료된다")
    void defaultExpiryIsOneYear() {
        clock.fixedAt(EARNED_AT);

        assertThat(earnService.earn(MEMBER_ID, 1_000, null).expiresAt())
                .isEqualTo(Instant.parse("2027-09-13T00:00:00Z"));
    }

    @Test
    @DisplayName("하루짜리 적립은 다음 날 같은 시각에 만료된다")
    void oneDayExpiryIsAllowed() {
        clock.fixedAt(EARNED_AT);

        assertThat(earnService.earn(MEMBER_ID, 1_000, 1).expiresAt())
                .isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
    }

    @Test
    @DisplayName("만료일수가 하루보다 짧으면 만료일수 범위 오류로 거절한다")
    void expiryShorterThanOneDayIsRejected() {
        clock.fixedAt(EARNED_AT);

        assertThat(rejectionCodeFor(0)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
        assertThat(rejectionCodeFor(-1)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("만료 시각이 적립일의 5년째 되는 날보다 앞이면 통과하고 그 날이면 거절한다")
    void fiveYearsIsAnExclusiveUpperBound() {
        clock.fixedAt(EARNED_AT);

        assertThat(earnService.earn(MEMBER_ID, 1_000, 1_825).expiresAt())
                .isEqualTo(Instant.parse("2031-09-12T00:00:00Z"));
        assertThat(rejectionCodeFor(1_826)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("2월 29일에 적립하면 5년 상한이 2월 28일로 맞춰진다")
    void leapDayEarningMovesTheBoundaryToTheTwentyEighth() {
        clock.fixedAt(Instant.parse("2024-02-29T00:00:00Z"));

        assertThat(earnService.earn(MEMBER_ID, 1_000, 1_825).expiresAt())
                .isEqualTo(Instant.parse("2029-02-27T00:00:00Z"));
        assertThat(rejectionCodeFor(1_826)).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }
}
