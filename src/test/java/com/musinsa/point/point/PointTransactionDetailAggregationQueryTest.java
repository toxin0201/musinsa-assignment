package com.musinsa.point.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.account.PointAccount;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PointTransactionDetailAggregationQueryTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private PointAccount account;
    private PointEarning earning;

    @BeforeEach
    void setUp() {
        account = testEntityManager.persist(PointAccount.open("DETAIL-M1", NOW));
        PointTransaction earnTransaction = testEntityManager
                .persist(PointTransaction.earn(account, "D-EARN", 1_000, NOW));
        earning = testEntityManager
                .persist(PointEarning.general(account, earnTransaction, 1_000, NOW, NOW.plusSeconds(86_400)));
    }

    @Test
    @DisplayName("한 번도 쓰이지 않은 적립 건에는 나간 상세가 없다")
    void untouchedEarningHasNoOutgoingDetail() {
        testEntityManager.flush();

        assertThat(detailRepository.existsByEarningIdAndDirection(earning.getId(), DetailDirection.OUT)).isFalse();
    }

    @Test
    @DisplayName("사용 이력은 전액이 되돌아온 뒤에도 나간 상세로 남는다")
    void usageHistoryRemainsAfterFullRestoration() {
        PointTransaction use = testEntityManager.persist(PointTransaction.use(account, "D-USE", 200, "O9", NOW));
        PointTransactionDetail outDetail = testEntityManager
                .persist(PointTransactionDetail.out(use, earning, 200, 1));
        PointTransaction useCancel = testEntityManager
                .persist(PointTransaction.useCancel(account, "D-CANCEL", 200, "O9", use.getId(), NOW));
        testEntityManager.persist(PointTransactionDetail.in(useCancel, earning, 200, outDetail.getId(), 1));
        testEntityManager.flush();

        assertThat(detailRepository.existsByEarningIdAndDirection(earning.getId(), DetailDirection.OUT)).isTrue();
        assertThat(detailRepository.sumRestoredAmountOf(outDetail.getId())).isEqualTo(200);
    }

    @Test
    @DisplayName("상세로 돌아온 금액은 여러 번 나눠 취소해도 누적으로 합산된다")
    void restoredAmountsAccumulateAcrossPartialCancels() {
        PointTransaction use = testEntityManager.persist(PointTransaction.use(account, "D-USE2", 500, "O10", NOW));
        PointTransactionDetail outDetail = testEntityManager
                .persist(PointTransactionDetail.out(use, earning, 500, 1));
        PointTransaction firstCancel = testEntityManager
                .persist(PointTransaction.useCancel(account, "D-C1", 100, "O10", use.getId(), NOW));
        testEntityManager.persist(PointTransactionDetail.in(firstCancel, earning, 100, outDetail.getId(), 1));
        PointTransaction secondCancel = testEntityManager
                .persist(PointTransaction.useCancel(account, "D-C2", 50, "O10", use.getId(), NOW));
        testEntityManager.persist(PointTransactionDetail.in(secondCancel, earning, 50, outDetail.getId(), 1));
        testEntityManager.flush();

        assertThat(detailRepository.sumRestoredAmountOf(outDetail.getId())).isEqualTo(150);
    }

    @Test
    @DisplayName("아직 아무것도 되돌리지 않은 사용 상세의 복원 합계는 0 이다")
    void neverCanceledDetailHasZeroRestoredAmount() {
        PointTransaction use = testEntityManager.persist(PointTransaction.use(account, "D-USE3", 300, "O11", NOW));
        PointTransactionDetail outDetail = testEntityManager
                .persist(PointTransactionDetail.out(use, earning, 300, 1));
        testEntityManager.flush();

        assertThat(detailRepository.sumRestoredAmountOf(outDetail.getId())).isZero();
    }

    @Test
    @DisplayName("한 거래의 사용 상세는 배분 순서대로 읽힌다")
    void outgoingDetailsOfOneTransactionKeepTheirOrder() {
        PointTransaction earnTransaction = testEntityManager
                .persist(PointTransaction.earn(account, "D-EARN2", 700, NOW));
        PointEarning second = testEntityManager
                .persist(PointEarning.general(account, earnTransaction, 700, NOW, NOW.plusSeconds(86_400)));
        PointTransaction use = testEntityManager.persist(PointTransaction.use(account, "D-USE4", 1_200, "O12", NOW));
        testEntityManager.persist(PointTransactionDetail.out(use, second, 200, 2));
        testEntityManager.persist(PointTransactionDetail.out(use, earning, 1_000, 1));
        testEntityManager.flush();

        assertThat(detailRepository.findOutgoingDetailsOfTransaction(use.getId()))
                .extracting(PointTransactionDetail::getSeq, PointTransactionDetail::getAmount)
                .containsExactly(tuple(1, 1_000L), tuple(2, 200L));
    }

    @Test
    @DisplayName("적립 건이 어떤 주문에서 쓰였는지 나간 상세로 되짚을 수 있다")
    void outgoingDetailsOfEarningTraceBackToOrders() {
        PointTransaction firstUse = testEntityManager.persist(PointTransaction.use(account, "D-U5", 100, "O13", NOW));
        testEntityManager.persist(PointTransactionDetail.out(firstUse, earning, 100, 1));
        PointTransaction secondUse = testEntityManager.persist(PointTransaction.use(account, "D-U6", 250, "O14", NOW));
        testEntityManager.persist(PointTransactionDetail.out(secondUse, earning, 250, 1));
        testEntityManager.flush();

        assertThat(detailRepository.findOutgoingDetailsOfEarning(earning.getId()))
                .extracting(detail -> detail.getTransaction().getOrderNo(), PointTransactionDetail::getAmount)
                .containsExactly(tuple("O13", 100L), tuple("O14", 250L));
    }
}
