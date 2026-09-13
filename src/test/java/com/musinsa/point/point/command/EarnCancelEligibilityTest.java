package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionDetail;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.point.TransactionType;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EarnCancelEligibilityTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M9";

    @Autowired
    private EarnService earnService;

    @Autowired
    private EarnCancelService earnCancelService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    private PointEarning earningOf(String pointKey) {
        return earningRepository
                .findByTransactionId(transactionRepository.findByPointKey(pointKey).orElseThrow().getId())
                .orElseThrow();
    }

    /** 사용 기능은 아직 없으므로 "이 적립이 주문에 쓰였다"는 상태를 데이터로 직접 만들어 둔다. */
    private void recordUsage(String memberId, PointEarning earning, long amount, String orderNo) {
        PointAccount account = accountRepository.findByMemberId(memberId).orElseThrow();
        PointTransaction use = transactionRepository
                .saveAndFlush(PointTransaction.use(account, "USE-" + orderNo, amount, orderNo, clock.instant()));
        earning.deduct(amount);
        earningRepository.saveAndFlush(earning);
        detailRepository.saveAndFlush(PointTransactionDetail.out(use, earning, amount, 1));
    }

    @Test
    @DisplayName("한 번도 쓰지 않은 적립은 취소되고 그만큼 잔액이 줄어든다")
    void unusedEarningCanBeCanceled() {
        EarnResult earned = earnService.earn(MEMBER_ID, 1_000, null);

        EarnCancelResult canceled = earnCancelService.cancel(MEMBER_ID, earned.pointKey());

        assertThat(canceled.canceledAmount()).isEqualTo(1_000);
        assertThat(canceled.balance()).isZero();

        PointEarning earning = earningOf(earned.pointKey());
        assertThat(earning.getStatus()).isEqualTo(EarningStatus.CANCELED);
        assertThat(earning.getRemainingAmount()).isZero();

        PointTransaction cancelTransaction = transactionRepository.findByPointKey(canceled.pointKey()).orElseThrow();
        assertThat(cancelTransaction.getType()).isEqualTo(TransactionType.EARN_CANCEL);
        assertThat(cancelTransaction.getAmount()).isEqualTo(1_000);
        assertThat(cancelTransaction.getRelatedTransactionId())
                .isEqualTo(transactionRepository.findByPointKey(earned.pointKey()).orElseThrow().getId());
    }

    @Test
    @DisplayName("일부라도 주문에 쓰인 적립은 취소할 수 없고 상태도 잔액도 그대로다")
    void partlyUsedEarningCannotBeCanceled() {
        EarnResult earned = earnService.earn(MEMBER_ID, 1_000, null);
        recordUsage(MEMBER_ID, earningOf(earned.pointKey()), 200, "O9");

        ApiException rejected = catchThrowableOfType(
                () -> earnCancelService.cancel(MEMBER_ID, earned.pointKey()), ApiException.class);

        assertThat(rejected.getErrorCode()).isEqualTo(ErrorCode.EARN_ALREADY_USED);
        PointEarning earning = earningOf(earned.pointKey());
        assertThat(earning.getStatus()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(earning.getRemainingAmount()).isEqualTo(800);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("썼다가 전액 돌려받은 적립도 사용 이력이 남아 있어 취소할 수 없다")
    void fullyRestoredEarningStillCountsAsUsed() {
        EarnResult earned = earnService.earn(MEMBER_ID, 1_000, null);
        PointEarning earning = earningOf(earned.pointKey());
        recordUsage(MEMBER_ID, earning, 200, "O10");

        PointAccount account = accountRepository.findByMemberId(MEMBER_ID).orElseThrow();
        PointTransaction use = transactionRepository.findByPointKey("USE-O10").orElseThrow();
        PointTransactionDetail outDetail = detailRepository.findOutgoingDetailsOfTransaction(use.getId()).get(0);
        PointTransaction useCancel = transactionRepository.saveAndFlush(
                PointTransaction.useCancel(account, "CANCEL-O10", 200, "O10", use.getId(), clock.instant()));
        PointEarning reloaded = earningOf(earned.pointKey());
        reloaded.restore(200);
        earningRepository.saveAndFlush(reloaded);
        detailRepository.saveAndFlush(
                PointTransactionDetail.in(useCancel, reloaded, 200, outDetail.getId(), 1));

        assertThat(earningOf(earned.pointKey()).getRemainingAmount()).isEqualTo(1_000);
        assertThat(catchThrowableOfType(
                () -> earnCancelService.cancel(MEMBER_ID, earned.pointKey()), ApiException.class).getErrorCode())
                .isEqualTo(ErrorCode.EARN_ALREADY_USED);
    }

    @Test
    @DisplayName("만료됐어도 쓰지 않은 적립은 취소할 수 있고 잔액은 이미 빠져 있어 변하지 않는다")
    void expiredButUnusedEarningCanStillBeCanceled() {
        EarnResult earned = earnService.earn(MEMBER_ID, 1_000, 30);
        clock.advance(Duration.ofDays(31));

        EarnCancelResult canceled = earnCancelService.cancel(MEMBER_ID, earned.pointKey());

        assertThat(canceled.canceledAmount()).isEqualTo(1_000);
        assertThat(canceled.balance()).isZero();
        assertThat(earningOf(earned.pointKey()).getStatus()).isEqualTo(EarningStatus.CANCELED);
    }

    @Test
    @DisplayName("이미 취소한 적립을 다시 취소하면 거절되고 거래도 늘지 않는다")
    void cancelingTwiceIsRejected() {
        EarnResult earned = earnService.earn(MEMBER_ID, 1_000, null);
        earnCancelService.cancel(MEMBER_ID, earned.pointKey());
        long transactionCountAfterFirstCancel = transactionRepository.count();

        assertThat(catchThrowableOfType(
                () -> earnCancelService.cancel(MEMBER_ID, earned.pointKey()), ApiException.class).getErrorCode())
                .isEqualTo(ErrorCode.EARN_ALREADY_CANCELED);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountAfterFirstCancel);
    }

    @Test
    @DisplayName("적립을 취소해도 같은 회원의 다른 적립은 그대로 남는다")
    void cancelingOneEarningLeavesTheOthersUntouched() {
        EarnResult first = earnService.earn(MEMBER_ID, 1_000, null);
        earnService.earn(MEMBER_ID, 500, null);

        assertThat(earnCancelService.cancel(MEMBER_ID, first.pointKey()).balance()).isEqualTo(500);
    }
}
