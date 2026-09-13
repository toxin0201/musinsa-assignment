package com.musinsa.point.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.account.PointAccount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PointEntityMappingTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-12-31T00:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("계정 · 거래 · 적립 건 · 거래 상세를 저장하고 그대로 다시 읽어온다")
    void persistsAndReadsBackTheWholePointGraph() {
        PointAccount account = PointAccount.open("M1", NOW);
        entityManager.persist(account);

        PointTransaction earnTransaction = PointTransaction.earn(account, "EARN-KEY", 1_000, NOW);
        entityManager.persist(earnTransaction);

        PointEarning earning = PointEarning.general(account, earnTransaction, 1_000, NOW, EXPIRES_AT);
        entityManager.persist(earning);

        PointTransaction useTransaction = PointTransaction.use(account, "USE-KEY", 400, "A1234", NOW);
        entityManager.persist(useTransaction);

        earning.deduct(400);
        PointTransactionDetail detail = PointTransactionDetail.out(useTransaction, earning, 400, 1);
        entityManager.persist(detail);

        entityManager.flush();
        entityManager.clear();

        PointEarning reloaded = entityManager.find(PointEarning.class, earning.getId());
        assertThat(reloaded.getAccount().getMemberId()).isEqualTo("M1");
        assertThat(reloaded.getTransaction().getPointKey()).isEqualTo("EARN-KEY");
        assertThat(reloaded.getOriginalAmount()).isEqualTo(1_000);
        assertThat(reloaded.getRemainingAmount()).isEqualTo(600);
        assertThat(reloaded.getEarnedAt()).isEqualTo(NOW);
        assertThat(reloaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(reloaded.getStatus()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(reloaded.getReissuedFromTransactionId()).isNull();

        PointTransactionDetail reloadedDetail = entityManager.find(PointTransactionDetail.class, detail.getId());
        assertThat(reloadedDetail.getDirection()).isEqualTo(DetailDirection.OUT);
        assertThat(reloadedDetail.getAmount()).isEqualTo(400);
        assertThat(reloadedDetail.getSeq()).isEqualTo(1);
        assertThat(reloadedDetail.getEarning().getId()).isEqualTo(earning.getId());
        assertThat(reloadedDetail.getTransaction().getPointKey()).isEqualTo("USE-KEY");
        assertThat(reloadedDetail.getReversedDetailId()).isNull();
    }

    @Test
    @DisplayName("사용 거래에만 주문번호 유일 제약용 컬럼이 채워지고 적립 거래에는 비어 있다")
    void onlyUseTransactionFillsTheUniqueOrderColumn() {
        PointAccount account = PointAccount.open("M2", NOW);
        entityManager.persist(account);

        PointTransaction earnTransaction = PointTransaction.earn(account, "EARN-KEY-2", 1_000, NOW);
        PointTransaction useTransaction = PointTransaction.use(account, "USE-KEY-2", 100, "B5678", NOW);
        entityManager.persist(earnTransaction);
        entityManager.persist(useTransaction);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(PointTransaction.class, earnTransaction.getId()).getUseOrderNo()).isNull();
        assertThat(entityManager.find(PointTransaction.class, earnTransaction.getId()).getOrderNo()).isNull();
        assertThat(entityManager.find(PointTransaction.class, useTransaction.getId()).getUseOrderNo())
                .isEqualTo("B5678");
        assertThat(entityManager.find(PointTransaction.class, useTransaction.getId()).getOrderNo())
                .isEqualTo("B5678");
    }

    @Test
    @DisplayName("계정의 개인 보유 한도는 비어 있을 수 있고 나중에 바꿀 수 있다")
    void personalMaxBalanceIsNullableAndChangeable() {
        PointAccount account = PointAccount.open("M3", NOW);
        entityManager.persist(account);
        entityManager.flush();

        assertThat(account.getMaxBalance()).isNull();

        account.changeMaxBalance(2_000L);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(PointAccount.class, account.getId()).getMaxBalance()).isEqualTo(2_000L);
    }
}
