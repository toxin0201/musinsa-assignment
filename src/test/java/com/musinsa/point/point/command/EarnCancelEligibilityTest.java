package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
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
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    private PointEarning earningOf(String pointKey) {
        return earningRepository
                .findByTransactionId(transactionRepository.findByPointKey(pointKey).orElseThrow().getId())
                .orElseThrow();
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
        useService.use(MEMBER_ID, "O9", 200);

        assertThat(rejectionCodeOf(() -> earnCancelService.cancel(MEMBER_ID, earned.pointKey())))
                .isEqualTo(ErrorCode.EARN_ALREADY_USED);
        PointEarning earning = earningOf(earned.pointKey());
        assertThat(earning.getStatus()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(earning.getRemainingAmount()).isEqualTo(800);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("썼다가 전액 돌려받은 적립도 사용 이력이 남아 있어 취소할 수 없다")
    void fullyRestoredEarningStillCountsAsUsed() {
        EarnResult earned = earnService.earn(MEMBER_ID, 1_000, null);
        UseResult used = useService.use(MEMBER_ID, "O10", 200);
        useCancelService.cancel(MEMBER_ID, used.pointKey(), 200);

        assertThat(earningOf(earned.pointKey()).getRemainingAmount()).isEqualTo(1_000);
        assertThat(rejectionCodeOf(() -> earnCancelService.cancel(MEMBER_ID, earned.pointKey())))
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

        assertThat(rejectionCodeOf(() -> earnCancelService.cancel(MEMBER_ID, earned.pointKey())))
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
