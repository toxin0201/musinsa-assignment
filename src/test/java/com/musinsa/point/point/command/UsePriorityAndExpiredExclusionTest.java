package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionDetail;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.point.TransactionType;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UsePriorityAndExpiredExclusionTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M20";

    @Autowired
    private EarnService earnService;

    @Autowired
    private EarnCancelService earnCancelService;

    @Autowired
    private UseService useService;

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

    @Test
    @DisplayName("수기 지급분을 먼저 쓰고 그 다음 만료가 가까운 순서로 쓴다")
    void manualPointsAreSpentFirstThenTheSoonestToExpire() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(MEMBER_ID, 500, 50);
        earnService.earnByAdmin(MEMBER_ID, 300, 200, "admin1", "보상");

        UseResult used = useService.use(MEMBER_ID, "ORDER-20", 900);

        assertThat(used.allocations())
                .extracting(UseAllocation::earningPointKey, UseAllocation::amount)
                .containsExactly(tuple("C", 300L), tuple("B", 500L), tuple("A", 100L));
        assertThat(used.amount()).isEqualTo(900);
        assertThat(used.balance()).isEqualTo(900);
        assertThat(earningOf("A").getRemainingAmount()).isEqualTo(900);
        assertThat(earningOf("B").getRemainingAmount()).isZero();
        assertThat(earningOf("C").getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("만료된 적립은 잔액이 남아 있어도 사용 대상에서 빠진다")
    void expiredEarningsAreNeverSpent() {
        earnService.earn(MEMBER_ID, 200, 10);
        earnService.earn(MEMBER_ID, 1_000, 100);
        clock.advance(Duration.ofDays(11));

        UseResult used = useService.use(MEMBER_ID, "ORDER-21", 700);

        assertThat(used.allocations())
                .extracting(UseAllocation::earningPointKey)
                .containsExactly("B");
        assertThat(used.balance()).isEqualTo(300);
        assertThat(earningOf("A").getRemainingAmount()).isEqualTo(200);
    }

    @Test
    @DisplayName("만료일이 같으면 먼저 적립된 건부터 쓴다")
    void equalExpiryFallsBackToEarnOrder() {
        earnService.earn(MEMBER_ID, 100, 30);
        earnService.earn(MEMBER_ID, 100, 30);

        UseResult used = useService.use(MEMBER_ID, "ORDER-22", 150);

        assertThat(used.allocations())
                .extracting(UseAllocation::earningPointKey, UseAllocation::amount)
                .containsExactly(tuple("A", 100L), tuple("B", 50L));
    }

    @Test
    @DisplayName("사용 거래에는 주문번호가 남고 적립 건별 차감 내역이 배분 순서대로 기록된다")
    void useTransactionKeepsOrderNumberAndPerEarningDetails() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(MEMBER_ID, 500, 50);

        UseResult used = useService.use(MEMBER_ID, "ORDER-23", 1_200);

        PointTransaction useTransaction = transactionRepository.findByPointKey(used.pointKey()).orElseThrow();
        assertThat(useTransaction.getType()).isEqualTo(TransactionType.USE);
        assertThat(useTransaction.getAmount()).isEqualTo(1_200);
        assertThat(useTransaction.getOrderNo()).isEqualTo("ORDER-23");
        assertThat(useTransaction.getUseOrderNo()).isEqualTo("ORDER-23");
        assertThat(useTransaction.getRelatedTransactionId()).isNull();

        List<PointTransactionDetail> details =
                detailRepository.findOutgoingDetailsOfTransaction(useTransaction.getId());
        assertThat(details)
                .extracting(PointTransactionDetail::getSeq, PointTransactionDetail::getAmount)
                .containsExactly(tuple(1, 500L), tuple(2, 700L));
        assertThat(details.get(0).getEarning().getId()).isEqualTo(earningOf("B").getId());
        assertThat(details.get(1).getEarning().getId()).isEqualTo(earningOf("A").getId());
    }

    @Test
    @DisplayName("적립취소된 건은 잔액이 남아 있던 것처럼 보여도 사용 대상에 오르지 않는다")
    void canceledEarningsAreNotSpent() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(MEMBER_ID, 500, 50);
        earnCancelService.cancel(MEMBER_ID, "B");

        UseResult used = useService.use(MEMBER_ID, "ORDER-24", 1_000);

        assertThat(used.allocations())
                .extracting(UseAllocation::earningPointKey, UseAllocation::amount)
                .containsExactly(tuple("A", 1_000L));
        assertThat(used.balance()).isZero();
    }
}
