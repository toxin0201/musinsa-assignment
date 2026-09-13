package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningKind;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 과제 예시 그대로의 한 줄기: A 적립 → B 적립 → C 사용 → A 만료 → D 사용취소(E 재적립).
 * 적립 A 는 만료가 먼저 오도록 30일, B 는 기본 만료를 준다.
 */
class FullFlowScenarioABCDETest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M40";
    private static final String ORDER_NO = "A1234";

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

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    private PointTransaction transactionOf(String pointKey) {
        return transactionRepository.findByPointKey(pointKey).orElseThrow();
    }

    private PointEarning earningOf(String pointKey) {
        return earningRepository.findByTransactionId(transactionOf(pointKey).getId()).orElseThrow();
    }

    private void runScenarioUpToUse() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        earnService.earn(MEMBER_ID, 500, 365);
        useService.use(MEMBER_ID, ORDER_NO, 1_200);
    }

    @Test
    @DisplayName("주문 A1234 의 1,200원은 만료가 먼저 오는 1,000원을 다 쓰고 나머지 200원을 다음 적립에서 가져간다")
    void theOrderSpendsTheSoonestExpiringEarningFirst() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        earnService.earn(MEMBER_ID, 500, 365);

        UseResult used = useService.use(MEMBER_ID, ORDER_NO, 1_200);

        assertThat(used.pointKey()).isEqualTo("C");
        assertThat(used.allocations())
                .extracting(UseAllocation::earningPointKey, UseAllocation::amount)
                .containsExactly(tuple("A", 1_000L), tuple("B", 200L));
        assertThat(used.balance()).isEqualTo(300);
    }

    @Test
    @DisplayName("만료된 1,000원 몫은 새 적립으로 돌려주고 살아 있는 100원 몫은 원래 적립으로 되돌아간다")
    void expiredShareComesBackAsANewEarningAndTheLivingShareGoesHome() {
        runScenarioUpToUse();
        clock.advance(Duration.ofDays(31));
        Instant canceledAt = clock.instant();

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, "C", 1_100);

        assertThat(canceled.pointKey()).isEqualTo("D");
        assertThat(canceled.canceledAmount()).isEqualTo(1_100);
        assertThat(canceled.restorations())
                .extracting(UseCancelRestoration::earningPointKey, UseCancelRestoration::amount,
                        UseCancelRestoration::reissued, UseCancelRestoration::newEarningPointKey)
                .containsExactly(
                        tuple("A", 1_000L, true, "E"),
                        tuple("B", 100L, false, null));

        assertThat(earningOf("A").getRemainingAmount()).isZero();
        assertThat(earningOf("B").getRemainingAmount()).isEqualTo(400);

        PointEarning reissued = earningOf("E");
        assertThat(reissued.getOriginalAmount()).isEqualTo(1_000);
        assertThat(reissued.getRemainingAmount()).isEqualTo(1_000);
        assertThat(reissued.getKind()).isEqualTo(EarningKind.GENERAL);
        assertThat(reissued.getStatus()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(reissued.getEarnedAt()).isEqualTo(canceledAt);
        assertThat(reissued.getExpiresAt()).isEqualTo(canceledAt.plus(365, ChronoUnit.DAYS));
        assertThat(reissued.getReissuedFromTransactionId()).isEqualTo(transactionOf("D").getId());

        assertThat(canceled.balance()).isEqualTo(1_400);
        assertThat(canceled.remainingCancelableAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("사용취소 거래는 원 사용과 같은 주문번호를 달고 재적립 거래는 그 사용취소를 가리킨다")
    void theCancelTransactionCarriesTheOrderNumberAndTheNewEarningPointsBackToIt() {
        runScenarioUpToUse();
        clock.advance(Duration.ofDays(31));

        useCancelService.cancel(MEMBER_ID, "C", 1_100);

        PointTransaction cancelTransaction = transactionOf("D");
        assertThat(cancelTransaction.getType()).isEqualTo(TransactionType.USE_CANCEL);
        assertThat(cancelTransaction.getAmount()).isEqualTo(1_100);
        assertThat(cancelTransaction.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(cancelTransaction.getUseOrderNo()).isNull();
        assertThat(cancelTransaction.getRelatedTransactionId()).isEqualTo(transactionOf("C").getId());

        PointTransaction reissueTransaction = transactionOf("E");
        assertThat(reissueTransaction.getType()).isEqualTo(TransactionType.EARN);
        assertThat(reissueTransaction.getAmount()).isEqualTo(1_000);
        assertThat(reissueTransaction.getRelatedTransactionId()).isEqualTo(cancelTransaction.getId());
    }

    @Test
    @DisplayName("복원 내역은 원 사용의 어느 줄을 되돌린 것인지까지 남긴다")
    void everyRestorationRemembersWhichUseLineItReverses() {
        runScenarioUpToUse();
        clock.advance(Duration.ofDays(31));
        List<PointTransactionDetail> useDetails =
                detailRepository.findOutgoingDetailsOfTransaction(transactionOf("C").getId());

        useCancelService.cancel(MEMBER_ID, "C", 1_100);

        List<PointTransactionDetail> cancelDetails = detailRepository.findAll().stream()
                .filter(detail -> detail.getTransaction().getId().equals(transactionOf("D").getId()))
                .sorted((left, right) -> Integer.compare(left.getSeq(), right.getSeq()))
                .toList();
        assertThat(cancelDetails)
                .extracting(PointTransactionDetail::getSeq, PointTransactionDetail::getAmount,
                        PointTransactionDetail::getReversedDetailId)
                .containsExactly(
                        tuple(1, 1_000L, useDetails.get(0).getId()),
                        tuple(2, 100L, useDetails.get(1).getId()));
        assertThat(cancelDetails.get(0).getEarning().getId()).isEqualTo(earningOf("E").getId());
        assertThat(cancelDetails.get(1).getEarning().getId()).isEqualTo(earningOf("B").getId());
    }

    @Test
    @DisplayName("남은 100원까지 취소하면 더는 취소할 것이 없고 1원만 더 요청해도 거절된다")
    void theLastHundredCanBeCanceledAndNothingBeyondIt() {
        runScenarioUpToUse();
        clock.advance(Duration.ofDays(31));
        useCancelService.cancel(MEMBER_ID, "C", 1_100);

        UseCancelResult lastCancel = useCancelService.cancel(MEMBER_ID, "C", 100);

        assertThat(lastCancel.canceledAmount()).isEqualTo(100);
        assertThat(lastCancel.remainingCancelableAmount()).isZero();
        assertThat(lastCancel.restorations())
                .extracting(UseCancelRestoration::earningPointKey, UseCancelRestoration::amount,
                        UseCancelRestoration::reissued)
                .containsExactly(tuple("B", 100L, false));
        assertThat(earningOf("B").getRemainingAmount()).isEqualTo(500);
        assertThat(lastCancel.balance()).isEqualTo(1_500);

        assertThat(rejectionCodeOf(() -> useCancelService.cancel(MEMBER_ID, "C", 1)))
                .isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDED);
        assertThat(earningRepository.sumAvailableBalance(
                transactionOf("A").getAccount().getId(), clock.instant())).isEqualTo(1_500);
    }
}
