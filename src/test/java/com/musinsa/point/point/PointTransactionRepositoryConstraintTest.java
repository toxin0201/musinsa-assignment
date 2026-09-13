package com.musinsa.point.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.musinsa.point.account.PointAccount;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PointTransactionRepositoryConstraintTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private PointTransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private PointAccount account;
    private PointAccount otherAccount;

    @BeforeEach
    void setUp() {
        account = testEntityManager.persist(PointAccount.open("TX-M1", NOW));
        otherAccount = testEntityManager.persist(PointAccount.open("TX-M2", NOW));
    }

    @Test
    @DisplayName("포인트 키로 거래를 찾는다")
    void findsTransactionByPointKey() {
        transactionRepository.saveAndFlush(PointTransaction.earn(account, "KEY-1", 1_000, NOW));

        assertThat(transactionRepository.findByPointKey("KEY-1"))
                .get()
                .extracting(PointTransaction::getType)
                .isEqualTo(TransactionType.EARN);
        assertThat(transactionRepository.findByPointKey("KEY-NONE")).isEmpty();
    }

    @Test
    @DisplayName("같은 계정에서 같은 주문번호로 두 번 사용할 수 없다")
    void sameOrderCannotBeUsedTwiceInOneAccount() {
        transactionRepository.saveAndFlush(PointTransaction.use(account, "USE-1", 100, "A1234", NOW));

        assertThatThrownBy(() -> transactionRepository
                .saveAndFlush(PointTransaction.use(account, "USE-2", 200, "A1234", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("주문번호가 같아도 계정이 다르면 각자 사용할 수 있다")
    void differentAccountsMayShareAnOrderNumber() {
        transactionRepository.saveAndFlush(PointTransaction.use(account, "USE-3", 100, "B1234", NOW));
        transactionRepository.saveAndFlush(PointTransaction.use(otherAccount, "USE-4", 100, "B1234", NOW));

        assertThat(transactionRepository.existsUseByAccountIdAndOrderNo(account.getId(), "B1234")).isTrue();
        assertThat(transactionRepository.existsUseByAccountIdAndOrderNo(otherAccount.getId(), "B1234")).isTrue();
        assertThat(transactionRepository.existsUseByAccountIdAndOrderNo(account.getId(), "NONE")).isFalse();
    }

    @Test
    @DisplayName("주문번호가 없는 적립 · 적립취소 거래는 한 계정에 여러 건 쌓인다")
    void transactionsWithoutOrderNumberAreNotConstrained() {
        PointTransaction earn = transactionRepository
                .saveAndFlush(PointTransaction.earn(account, "KEY-2", 1_000, NOW));
        transactionRepository.saveAndFlush(PointTransaction.earn(account, "KEY-3", 2_000, NOW));
        transactionRepository.saveAndFlush(
                PointTransaction.earnCancel(account, "KEY-4", 1_000, earn.getId(), NOW));

        assertThat(transactionRepository.existsUseByAccountIdAndOrderNo(account.getId(), null)).isFalse();
        assertThat(transactionRepository.findByPointKey("KEY-4"))
                .get()
                .extracting(PointTransaction::getRelatedTransactionId)
                .isEqualTo(earn.getId());
    }

    @Test
    @DisplayName("사용취소 거래는 원 주문번호를 남기지만 중복 사용 방지 대상은 아니다")
    void useCancelKeepsOrderNumberWithoutBlockingTheConstraint() {
        PointTransaction use = transactionRepository
                .saveAndFlush(PointTransaction.use(account, "USE-5", 300, "C1234", NOW));

        PointTransaction useCancel = transactionRepository.saveAndFlush(
                PointTransaction.useCancel(account, "CANCEL-1", 100, "C1234", use.getId(), NOW));

        assertThat(useCancel.getOrderNo()).isEqualTo("C1234");
        assertThat(useCancel.getUseOrderNo()).isNull();
    }
}
