package com.musinsa.point.point.query;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BalanceExcludesExpiredAndCanceledTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M50";

    @Autowired
    private EarnService earnService;

    @Autowired
    private EarnCancelService earnCancelService;

    @Autowired
    private UseService useService;

    @Autowired
    private BalanceQueryService balanceQueryService;

    @Test
    @DisplayName("잔액은 지금 쓸 수 있는 적립만 더한 값이고 기준 시각을 함께 알려 준다")
    void theBalanceIsTheSumOfWhatCanBeSpentRightNow() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        earnService.earn(MEMBER_ID, 500, 365);

        BalanceView balance = balanceQueryService.balance(MEMBER_ID);

        assertThat(balance.balance()).isEqualTo(1_500);
        assertThat(balance.asOf()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("만료된 적립은 잔액에서 빠진다")
    void expiredEarningsDropOutOfTheBalance() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        earnService.earn(MEMBER_ID, 500, 365);

        clock.advance(Duration.ofDays(31));

        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isEqualTo(500);
    }

    @Test
    @DisplayName("적립취소된 건은 잔액에서 빠진다")
    void canceledEarningsDropOutOfTheBalance() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        earnService.earn(MEMBER_ID, 500, 365);

        earnCancelService.cancel(MEMBER_ID, "A");

        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isEqualTo(500);
    }

    @Test
    @DisplayName("사용한 만큼 잔액이 줄고 되돌리면 다시 늘어난다")
    void spendingLowersTheBalanceAndCancelingRaisesItBack() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        useService.use(MEMBER_ID, "ORDER-100", 400);

        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isEqualTo(600);
    }

    @Test
    @DisplayName("남은 포인트가 하나도 없으면 잔액은 0이다")
    void anAccountWithNothingLeftReportsZero() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        earnCancelService.cancel(MEMBER_ID, "A");

        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isZero();
    }

    @Test
    @DisplayName("포인트를 한 번도 받은 적 없는 회원은 조회 대상이 아니다")
    void aMemberWithoutAnAccountCannotBeQueried() {
        assertThat(rejectionCodeOf(() -> balanceQueryService.balance("NEVER-EARNED")))
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
