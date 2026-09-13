package com.musinsa.point.account;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class AdminLimitUpdateAndDefaultResetTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M53";

    @Autowired
    private AccountLimitService accountLimitService;

    @Autowired
    private EarnService earnService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Test
    @DisplayName("개인 한도를 걸면 그 회원의 적립은 기본 한도가 아니라 그 값에서 막힌다")
    void aPersonalLimitOverridesTheConfiguredDefault() {
        earnService.earn(MEMBER_ID, 1_000, 365);

        accountLimitService.setMaxBalance(MEMBER_ID, 1_000L);

        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 1, 365)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("한도를 비우면 설정 기본값으로 돌아가 다시 적립할 수 있다")
    void clearingTheLimitFallsBackToTheDefault() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        accountLimitService.setMaxBalance(MEMBER_ID, 1_000L);

        AccountLimitResult cleared = accountLimitService.setMaxBalance(MEMBER_ID, null);

        assertThat(cleared.maxBalance()).isNull();
        assertThat(earnService.earn(MEMBER_ID, 1_000, 365).balance()).isEqualTo(2_000);
    }

    @Test
    @DisplayName("한 번도 적립한 적 없는 회원에게도 한도를 미리 걸어 둘 수 있다")
    void aLimitCanBeSetBeforeTheMemberEverEarnsAnything() {
        AccountLimitResult result = accountLimitService.setMaxBalance("BRAND-NEW", 500L);

        assertThat(result.memberId()).isEqualTo("BRAND-NEW");
        assertThat(result.maxBalance()).isEqualTo(500);
        assertThat(accountRepository.findByMemberId("BRAND-NEW")).isPresent();
        assertThat(rejectionCodeOf(() -> earnService.earn("BRAND-NEW", 501, 365)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
        assertThat(earnService.earn("BRAND-NEW", 500, 365).balance()).isEqualTo(500);
    }

    @ParameterizedTest(name = "한도 {0} 은 거절된다")
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("보유 한도는 1원 이상이어야 한다")
    void theLimitMustBeAtLeastOne(long maxBalance) {
        earnService.earn(MEMBER_ID, 1_000, 365);

        assertThat(rejectionCodeOf(() -> accountLimitService.setMaxBalance(MEMBER_ID, maxBalance)))
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(accountRepository.findByMemberId(MEMBER_ID).orElseThrow().getMaxBalance()).isNull();
    }

    @Test
    @DisplayName("이미 쌓인 잔액보다 낮은 한도도 걸 수 있고 기존 잔액은 그대로 남는다")
    void aLimitBelowTheCurrentBalanceIsAcceptedAndKeepsWhatIsAlreadyThere() {
        earnService.earn(MEMBER_ID, 5_000, 365);

        AccountLimitResult result = accountLimitService.setMaxBalance(MEMBER_ID, 1_000L);

        assertThat(result.maxBalance()).isEqualTo(1_000);
        assertThat(accountRepository.findByMemberId(MEMBER_ID).orElseThrow().getMaxBalance()).isEqualTo(1_000);
        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 1, 365)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("같은 한도를 두 번 걸어도 결과가 달라지지 않는다")
    void settingTheSameLimitTwiceChangesNothing() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        accountLimitService.setMaxBalance(MEMBER_ID, 2_000L);

        AccountLimitResult second = accountLimitService.setMaxBalance(MEMBER_ID, 2_000L);

        assertThat(second.maxBalance()).isEqualTo(2_000);
        assertThat(accountRepository.count()).isEqualTo(1);
    }
}
