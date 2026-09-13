package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사용취소는 쓴 것을 돌려주는 일이라 보유 한도를 보지 않는다.
 * 한도는 새로 주는 적립에만 걸린다는 것을 한 회원의 한도 1,500 안에서 확인한다.
 */
class NoLimitCheckOnRestorationTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M45";
    private static final long PERSONAL_MAX_BALANCE = 1_500;

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private PointAccountRepository accountRepository;

    private void setPersonalLimit() {
        PointAccount account = accountRepository.findByMemberId(MEMBER_ID).orElseThrow();
        account.changeMaxBalance(PERSONAL_MAX_BALANCE);
        accountRepository.saveAndFlush(account);
    }

    /** 한도 1,500 을 꽉 채워 쓰고 다시 채운 상태. 여기서 1,200 을 돌려받으면 한도를 넘게 된다. */
    private String fillUpToTheLimitThenSpendAndRefill() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        setPersonalLimit();
        earnService.earn(MEMBER_ID, 500, 365);
        String usePointKey = useService.use(MEMBER_ID, "ORDER-80", 1_200).pointKey();
        earnService.earn(MEMBER_ID, 1_200, 365);
        return usePointKey;
    }

    @Test
    @DisplayName("사용취소로 보유 한도를 넘게 되더라도 취소는 그대로 받아 준다")
    void restoringPointsIsAllowedEvenWhenItPushesTheBalanceOverTheLimit() {
        String usePointKey = fillUpToTheLimitThenSpendAndRefill();

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, usePointKey, 1_200);

        assertThat(canceled.canceledAmount()).isEqualTo(1_200);
        assertThat(canceled.balance()).isEqualTo(2_700);
    }

    @Test
    @DisplayName("한도를 넘긴 뒤에도 새 적립은 여전히 한도에서 막힌다")
    void newEarningsAreStillBlockedByTheLimitAfterwards() {
        String usePointKey = fillUpToTheLimitThenSpendAndRefill();
        useCancelService.cancel(MEMBER_ID, usePointKey, 1_200);

        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 1, 365)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("만료분이 여러 건이면 모두 새 적립으로 돌아오고 그 합이 한도를 넘어도 막지 않는다")
    void everyExpiredShareComesBackEvenWhenTheTotalBlowsPastTheLimit() {
        earnService.earn(MEMBER_ID, 100_000, 30);
        earnService.earn(MEMBER_ID, 100_000, 30);
        useService.use(MEMBER_ID, "ORDER-81", 200_000);
        PointAccount account = accountRepository.findByMemberId(MEMBER_ID).orElseThrow();
        account.changeMaxBalance(150_000L);
        accountRepository.saveAndFlush(account);
        clock.advance(Duration.ofDays(31));

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, "C", 200_000);

        assertThat(canceled.restorations())
                .extracting(UseCancelRestoration::earningPointKey, UseCancelRestoration::amount,
                        UseCancelRestoration::reissued, UseCancelRestoration::newEarningPointKey)
                .containsExactly(
                        tuple("A", 100_000L, true, "E"),
                        tuple("B", 100_000L, true, "F"));
        assertThat(canceled.balance()).isEqualTo(200_000);
    }
}
