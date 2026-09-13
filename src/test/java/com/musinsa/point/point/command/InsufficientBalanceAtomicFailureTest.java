package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InsufficientBalanceAtomicFailureTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M21";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    private long remainingOf(String pointKey) {
        return earningRepository
                .findByTransactionId(transactionRepository.findByPointKey(pointKey).orElseThrow().getId())
                .orElseThrow()
                .getRemainingAmount();
    }

    @Test
    @DisplayName("1원이라도 모자라면 거절되고 앞 순서의 적립도 그대로 남는다")
    void shortByOneLeavesEveryEarningUntouched() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(MEMBER_ID, 500, 50);

        ErrorCode rejected = rejectionCodeOf(() -> useService.use(MEMBER_ID, "ORDER-30", 1_501));

        assertThat(rejected).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(remainingOf("A")).isEqualTo(1_000);
        assertThat(remainingOf("B")).isEqualTo(500);
        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(detailRepository.count()).isZero();
    }

    @Test
    @DisplayName("잔액과 정확히 같은 금액은 사용되고 잔액이 0이 된다")
    void spendingExactlyTheWholeBalanceSucceeds() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(MEMBER_ID, 500, 50);

        UseResult used = useService.use(MEMBER_ID, "ORDER-31", 1_500);

        assertThat(used.balance()).isZero();
        assertThat(used.allocations()).hasSize(2);
    }

    @Test
    @DisplayName("만료된 포인트만 남은 회원은 1원도 쓸 수 없다")
    void expiredPointsDoNotCountTowardTheBalance() {
        earnService.earn(MEMBER_ID, 1_000, 10);
        clock.advance(Duration.ofDays(11));

        assertThat(rejectionCodeOf(() -> useService.use(MEMBER_ID, "ORDER-32", 1)))
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(remainingOf("A")).isEqualTo(1_000);
    }

    @Test
    @DisplayName("잔액을 모두 쓴 뒤 다시 사용하면 거절된다")
    void usingAgainAfterTheBalanceIsGoneIsRejected() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        useService.use(MEMBER_ID, "ORDER-33", 1_000);

        assertThat(rejectionCodeOf(() -> useService.use(MEMBER_ID, "ORDER-34", 1)))
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(detailRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 번도 적립한 적 없는 회원의 사용은 계정이 없다는 이유로 거절된다")
    void memberWithoutAnyEarningHasNoAccountYet() {
        assertThat(rejectionCodeOf(() -> useService.use("NEVER-EARNED", "ORDER-35", 100)))
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
