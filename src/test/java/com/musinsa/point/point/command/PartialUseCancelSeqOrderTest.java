package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PartialUseCancelSeqOrderTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M43";
    private static final String ORDER_NO = "ORDER-61";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    private long remainingOf(String pointKey) {
        return earningRepository
                .findByTransactionId(transactionRepository.findByPointKey(pointKey).orElseThrow().getId())
                .orElseThrow()
                .getRemainingAmount();
    }

    /** A(만료 30일) 1,000 과 B(만료 365일) 500 에서 1,200 을 쓴다. 배분은 A 1,000 · B 200. */
    private String spendAcrossTwoEarnings() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        earnService.earn(MEMBER_ID, 500, 365);
        return useService.use(MEMBER_ID, ORDER_NO, 1_200).pointKey();
    }

    @Test
    @DisplayName("일부만 취소하면 먼저 쓴 적립부터 되돌아가고 뒤 순서는 건드리지 않는다")
    void partialCancelWalksTheUseOrderFromTheFront() {
        String usePointKey = spendAcrossTwoEarnings();

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, usePointKey, 300);

        assertThat(canceled.restorations())
                .extracting(UseCancelRestoration::earningPointKey, UseCancelRestoration::amount)
                .containsExactly(tuple("A", 300L));
        assertThat(remainingOf("A")).isEqualTo(300);
        assertThat(remainingOf("B")).isEqualTo(300);
        assertThat(canceled.remainingCancelableAmount()).isEqualTo(900);
    }

    @Test
    @DisplayName("앞 순서를 다 돌려받은 뒤에야 다음 적립으로 넘어간다")
    void theSecondEarningIsTouchedOnlyAfterTheFirstIsFullyRestored() {
        String usePointKey = spendAcrossTwoEarnings();
        useCancelService.cancel(MEMBER_ID, usePointKey, 900);

        UseCancelResult second = useCancelService.cancel(MEMBER_ID, usePointKey, 200);

        assertThat(second.restorations())
                .extracting(UseCancelRestoration::earningPointKey, UseCancelRestoration::amount)
                .containsExactly(tuple("A", 100L), tuple("B", 100L));
        assertThat(remainingOf("A")).isEqualTo(1_000);
        assertThat(remainingOf("B")).isEqualTo(400);
        assertThat(second.remainingCancelableAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("전액을 돌려받으면 두 적립 모두 처음 금액으로 돌아온다")
    void cancelingEverythingPutsBothEarningsBackToTheirOriginalAmount() {
        String usePointKey = spendAcrossTwoEarnings();

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, usePointKey, 1_200);

        assertThat(remainingOf("A")).isEqualTo(1_000);
        assertThat(remainingOf("B")).isEqualTo(500);
        assertThat(canceled.balance()).isEqualTo(1_500);
        assertThat(canceled.remainingCancelableAmount()).isZero();
    }
}
