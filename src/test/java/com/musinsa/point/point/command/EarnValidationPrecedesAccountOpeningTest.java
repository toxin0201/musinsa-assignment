package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.account.AccountLimitService;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 성공할 수 없는 적립 요청은 계정을 만들지도, 계정 행을 잠그지도 않는다.
 * 금액과 만료일수는 회원의 지금 상태와 무관하게 판정할 수 있으므로 먼저 본다.
 * 보유 한도만은 현재 잔액을 읽어야 알 수 있어 잠근 뒤에 본다.
 */
class EarnValidationPrecedesAccountOpeningTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M90";

    @Autowired
    private EarnService earnService;

    @Autowired
    private AccountLimitService accountLimitService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @Test
    @DisplayName("만료일수가 하한에 못 미치면 계정을 만들지 않고 돌려보낸다")
    void anExpiryBelowTheMinimumLeavesNoAccountBehind() {
        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 1_000, 0)))
                .isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);

        assertThat(accountRepository.findByMemberId(MEMBER_ID)).isEmpty();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("만료일수가 상한을 넘어도 계정을 만들지 않는다")
    void anExpiryBeyondTheMaximumLeavesNoAccountBehind() {
        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 1_000, 3_000)))
                .isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("1회 적립 한도를 넘는 금액도 계정을 만들지 않는다")
    void anAmountBeyondThePerEarnLimitLeavesNoAccountBehind() {
        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 200_000, 365)))
                .isEqualTo(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE);

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("관리자 수기 지급도 같은 순서로 본다")
    void theAdminPathChecksInTheSameOrder() {
        assertThat(rejectionCodeOf(() -> earnService.earnByAdmin(MEMBER_ID, 1_000, 0, "admin-1", "보상")))
                .isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("보유 한도 검사는 계정을 잠근 뒤에 하고, 거절해도 적립은 한 건도 남기지 않는다")
    void theBalanceLimitIsCheckedAfterLockingAndLeavesNoEarning() {
        accountLimitService.setMaxBalance(MEMBER_ID, 1_000L);

        assertThat(rejectionCodeOf(() -> earnService.earn(MEMBER_ID, 2_000, 365)))
                .isEqualTo(ErrorCode.BALANCE_LIMIT_EXCEEDED);

        assertThat(accountRepository.findByMemberId(MEMBER_ID)).isPresent();
        assertThat(earningRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("모든 검사를 통과한 적립만 계정을 열고 기록을 남긴다")
    void onlyAValidEarningOpensTheAccount() {
        earnService.earn(MEMBER_ID, 1_000, 365);

        assertThat(accountRepository.findByMemberId(MEMBER_ID)).isPresent();
        assertThat(earningRepository.count()).isEqualTo(1);
    }
}
