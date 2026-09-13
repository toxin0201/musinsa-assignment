package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BalanceLimitDefaultAndPersonalTest extends AbstractPointIntegrationTest {

    @Autowired
    private EarnService earnService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PointEarningRepository earningRepository;

    private void fillBalance(String memberId, long target) {
        long remaining = target;
        while (remaining > 0) {
            long chunk = Math.min(remaining, 100_000);
            earnService.earn(memberId, chunk, null);
            remaining -= chunk;
        }
    }

    private void setPersonalLimit(String memberId, Long maxBalance) {
        PointAccount account = accountRepository.findByMemberId(memberId).orElseThrow();
        account.changeMaxBalance(maxBalance);
        accountRepository.saveAndFlush(account);
    }

    @Test
    @DisplayName("개인 한도가 없으면 설정 기본 한도까지 적립할 수 있고 그 위는 거절한다")
    void defaultLimitAllowsUpToItselfAndRejectsOneMore() {
        fillBalance("M5", 999_900);

        assertThat(earnService.earn("M5", 100, null).balance()).isEqualTo(1_000_000);

        ApiException rejected = catchThrowableOfType(ApiException.class, () -> earnService.earn("M5", 1, null));
        assertThat(rejected.getErrorCode()).isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);

        PointAccount account = accountRepository.findByMemberId("M5").orElseThrow();
        assertThat(earningRepository.sumAvailableBalance(account.getId(), clock.instant())).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("만료된 포인트는 한도 판정에 포함되지 않는다")
    void expiredPointsDoNotCountTowardsTheLimit() {
        fillBalance("M5b", 500_000);
        clock.advance(Duration.ofDays(400));

        assertThat(earnService.earn("M5b", 100_000, null).balance()).isEqualTo(100_000);
        assertThat(earnService.earn("M5b", 100_000, null).balance()).isEqualTo(200_000);
    }

    @Test
    @DisplayName("회원에게 개인 한도가 걸려 있으면 설정 기본값보다 그 값이 앞선다")
    void personalLimitWinsOverTheConfiguredDefault() {
        earnService.earn("M6", 1_900, null);
        setPersonalLimit("M6", 2_000L);

        assertThat(rejectionCodeOf(() -> earnService.earn("M6", 200, null)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
        assertThat(earnService.earn("M6", 100, null).balance()).isEqualTo(2_000);
    }

    @Test
    @DisplayName("개인 한도를 비우면 다시 설정 기본 한도를 따른다")
    void clearingThePersonalLimitFallsBackToTheDefault() {
        earnService.earn("M6b", 1_900, null);
        setPersonalLimit("M6b", 2_000L);
        catchThrowableOfType(ApiException.class, () -> earnService.earn("M6b", 200, null));

        setPersonalLimit("M6b", null);

        assertThat(earnService.earn("M6b", 200, null).balance()).isEqualTo(2_100);
    }

    @Test
    @DisplayName("현재 잔액보다 낮은 개인 한도를 걸어도 기존 잔액은 그대로 두고 이후 적립만 막는다")
    void loweringTheLimitBelowTheBalanceOnlyBlocksFutureEarnings() {
        earnService.earn("M6c", 5_000, null);
        setPersonalLimit("M6c", 1_000L);

        PointAccount account = accountRepository.findByMemberId("M6c").orElseThrow();
        assertThat(earningRepository.sumAvailableBalance(account.getId(), clock.instant())).isEqualTo(5_000);
        assertThat(rejectionCodeOf(() -> earnService.earn("M6c", 1, null)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);
    }
}
