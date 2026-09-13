package com.musinsa.point.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.account.PointAccount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PointEarningRepositoryQueryTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private PointAccount account;
    private int keySequence;

    @BeforeEach
    void setUp() {
        account = testEntityManager.persist(PointAccount.open("EARN-M1", NOW));
        keySequence = 0;
    }

    private PointEarning general(long amount, Instant expiresAt) {
        return persist(EarningKind.GENERAL, amount, expiresAt);
    }

    private PointEarning manual(long amount, Instant expiresAt) {
        return persist(EarningKind.MANUAL, amount, expiresAt);
    }

    private PointEarning persist(EarningKind kind, long amount, Instant expiresAt) {
        PointTransaction transaction = testEntityManager
                .persist(PointTransaction.earn(account, "EK-" + (++keySequence), amount, NOW));
        PointEarning earning = kind == EarningKind.MANUAL
                ? PointEarning.manual(account, transaction, amount, NOW, expiresAt, "admin01", "보상")
                : PointEarning.general(account, transaction, amount, NOW, expiresAt);
        return testEntityManager.persist(earning);
    }

    @Test
    @DisplayName("사용 대상은 수기 지급분이 먼저 오고 그 다음 만료가 임박한 순, 같으면 먼저 적립된 순이다")
    void usableEarningsComeInSpendingOrder() {
        PointEarning generalSoon = general(100, NOW.plusSeconds(3_600));
        PointEarning generalLate = general(100, NOW.plusSeconds(7_200));
        PointEarning generalSameExpiry = general(100, NOW.plusSeconds(3_600));
        PointEarning manualLate = manual(100, NOW.plusSeconds(86_400));
        PointEarning manualSoon = manual(100, NOW.plusSeconds(1_800));
        testEntityManager.flush();

        List<PointEarning> usable = earningRepository.findUsableOrdered(account.getId(), NOW);

        assertThat(usable).extracting(PointEarning::getId).containsExactly(
                manualSoon.getId(), manualLate.getId(),
                generalSoon.getId(), generalSameExpiry.getId(), generalLate.getId());
    }

    @Test
    @DisplayName("만료됐거나 취소됐거나 잔액이 0 인 적립 건은 사용 대상에서 빠진다")
    void unusableEarningsAreExcluded() {
        PointEarning usable = general(500, NOW.plusSeconds(3_600));
        general(500, NOW.minusSeconds(1));
        PointEarning exactlyAtExpiry = general(500, NOW);
        PointEarning drained = general(500, NOW.plusSeconds(3_600));
        drained.deduct(500);
        PointEarning canceled = general(500, NOW.plusSeconds(3_600));
        canceled.cancel();
        testEntityManager.flush();

        List<PointEarning> result = earningRepository.findUsableOrdered(account.getId(), NOW);

        assertThat(result).extracting(PointEarning::getId).containsExactly(usable.getId());
        assertThat(result).extracting(PointEarning::getId)
                .doesNotContain(exactlyAtExpiry.getId(), drained.getId(), canceled.getId());
    }

    @Test
    @DisplayName("사용 가능 잔액은 만료 · 취소분을 뺀 합계이고 적립이 없으면 0 이다")
    void availableBalanceSumsOnlyUsableEarnings() {
        assertThat(earningRepository.sumAvailableBalance(account.getId(), NOW)).isZero();

        general(1_000, NOW.plusSeconds(3_600));
        general(500_000, NOW.minusSeconds(1));
        PointEarning partlyUsed = general(500, NOW.plusSeconds(3_600));
        partlyUsed.deduct(200);
        PointEarning canceled = general(9_999, NOW.plusSeconds(3_600));
        canceled.cancel();
        testEntityManager.flush();

        assertThat(earningRepository.sumAvailableBalance(account.getId(), NOW)).isEqualTo(1_300);
    }

    @Test
    @DisplayName("적립 건은 그 적립을 만든 거래로 찾을 수 있다")
    void findsEarningByItsEarnTransaction() {
        PointEarning earning = general(1_000, NOW.plusSeconds(3_600));
        testEntityManager.flush();

        assertThat(earningRepository.findByTransactionId(earning.getTransaction().getId()))
                .get()
                .extracting(PointEarning::getId)
                .isEqualTo(earning.getId());
        assertThat(earningRepository.findByTransactionId(-1L)).isEmpty();
    }

    @Test
    @DisplayName("다른 회원의 적립 건은 섞이지 않는다")
    void otherMembersEarningsAreNotIncluded() {
        general(1_000, NOW.plusSeconds(3_600));
        PointAccount other = testEntityManager.persist(PointAccount.open("EARN-M2", NOW));
        PointTransaction otherTransaction = testEntityManager
                .persist(PointTransaction.earn(other, "EK-OTHER", 7_000, NOW));
        testEntityManager.persist(PointEarning.general(other, otherTransaction, 7_000, NOW, NOW.plusSeconds(3_600)));
        testEntityManager.flush();

        assertThat(earningRepository.sumAvailableBalance(account.getId(), NOW)).isEqualTo(1_000);
        assertThat(earningRepository.sumAvailableBalance(other.getId(), NOW)).isEqualTo(7_000);
    }
}
