package com.musinsa.point.point.query;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * "언제 적립된 포인트가 어떤 주문에서 얼마나 쓰였는가"를 1원 단위로 되짚을 수 있어야 한다.
 */
class EarningUsageHistoryInvariantTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M51";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private EarningUsageQueryService earningUsageQueryService;

    /** 적립 A 1,000 을 주문 O1 에서 300, 주문 O2 에서 200 쓰고 O1 에서 100 을 되돌린다. */
    private void spendAcrossTwoOrdersAndCancelPartOfTheFirst() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        useService.use(MEMBER_ID, "O1", 300);
        useService.use(MEMBER_ID, "O2", 200);
        useCancelService.cancel(MEMBER_ID, "B", 100);
    }

    @Test
    @DisplayName("적립 한 건이 어떤 주문에서 얼마 쓰이고 얼마 되돌아왔는지 주문별로 집계된다")
    void usageIsGroupedByOrderWithUsedCanceledAndNetAmounts() {
        spendAcrossTwoOrdersAndCancelPartOfTheFirst();

        EarningUsageView view = earningUsageQueryService.usages(MEMBER_ID, "A");

        assertThat(view.usages())
                .extracting(OrderUsage::orderNo, OrderUsage::usePointKey, OrderUsage::usedAmount,
                        OrderUsage::canceledAmount, OrderUsage::netUsedAmount)
                .containsExactly(
                        tuple("O1", "B", 300L, 100L, 200L),
                        tuple("O2", "C", 200L, 0L, 200L));
    }

    @Test
    @DisplayName("적립 요약은 최초 금액과 남은 금액을 함께 보여 주고 둘의 차이가 순사용액 합과 맞는다")
    void theSummaryBalancesAgainstTheNetUsedTotal() {
        spendAcrossTwoOrdersAndCancelPartOfTheFirst();

        EarningUsageView view = earningUsageQueryService.usages(MEMBER_ID, "A");

        EarningSummary earning = view.earning();
        assertThat(earning.pointKey()).isEqualTo("A");
        assertThat(earning.kind()).isEqualTo(EarningKind.GENERAL);
        assertThat(earning.status()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(earning.originalAmount()).isEqualTo(1_000);
        assertThat(earning.remainingAmount()).isEqualTo(600);

        long netUsedTotal = view.usages().stream().mapToLong(OrderUsage::netUsedAmount).sum();
        assertThat(earning.originalAmount() - earning.remainingAmount()).isEqualTo(netUsedTotal);
    }

    @Test
    @DisplayName("전액을 되돌린 주문은 쓴 금액과 되돌린 금액이 같고 순사용액이 0으로 남는다")
    void fullyCanceledOrderStaysInTheHistoryWithZeroNet() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        useService.use(MEMBER_ID, "O3", 300);
        useCancelService.cancel(MEMBER_ID, "B", 300);

        EarningUsageView view = earningUsageQueryService.usages(MEMBER_ID, "A");

        assertThat(view.usages())
                .extracting(OrderUsage::orderNo, OrderUsage::usedAmount, OrderUsage::canceledAmount,
                        OrderUsage::netUsedAmount)
                .containsExactly(tuple("O3", 300L, 300L, 0L));
        assertThat(view.earning().remainingAmount()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("한 번도 쓰지 않은 적립은 사용 내역이 비어 있다")
    void anUnusedEarningHasNoHistory() {
        earnService.earn(MEMBER_ID, 1_000, 365);

        EarningUsageView view = earningUsageQueryService.usages(MEMBER_ID, "A");

        assertThat(view.usages()).isEmpty();
        assertThat(view.earning().remainingAmount()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("사용 거래의 pointKey 로는 적립 내역을 조회할 수 없다")
    void aUsePointKeyIsNotAnEarnPointKey() {
        spendAcrossTwoOrdersAndCancelPartOfTheFirst();

        assertThat(rejectionCodeOf(() -> earningUsageQueryService.usages(MEMBER_ID, "B")))
                .isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회원의 적립 내역은 볼 수 없다")
    void anotherMembersEarningIsOutOfReach() {
        spendAcrossTwoOrdersAndCancelPartOfTheFirst();
        earnService.earn("M52", 100, 365);

        assertThat(rejectionCodeOf(() -> earningUsageQueryService.usages("M52", "A")))
                .isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("계정이 없는 회원의 적립 내역 조회는 회원을 찾을 수 없다고 알린다")
    void aMemberWithoutAnAccountCannotBeQueried() {
        assertThat(rejectionCodeOf(() -> earningUsageQueryService.usages("NEVER-EARNED", "A")))
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
