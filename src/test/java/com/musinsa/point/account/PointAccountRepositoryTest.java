package com.musinsa.point.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PointAccountRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    @DisplayName("회원 식별자로 계정을 잠그고 읽어온다")
    void locksAccountByMemberId() {
        accountRepository.save(PointAccount.open("LOCK-M1", NOW));
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(accountRepository.findByMemberIdForUpdate("LOCK-M1"))
                .get()
                .extracting(PointAccount::getMemberId)
                .isEqualTo("LOCK-M1");
    }

    @Test
    @DisplayName("계정이 없는 회원을 잠그려 하면 빈 결과가 나온다")
    void returnsEmptyWhenAccountIsAbsent() {
        assertThat(accountRepository.findByMemberIdForUpdate("LOCK-NONE")).isEmpty();
        assertThat(accountRepository.findByMemberId("LOCK-NONE")).isEmpty();
    }

    @Test
    @DisplayName("같은 회원의 계정은 두 번 만들 수 없다")
    void memberCanHaveOnlyOneAccount() {
        accountRepository.saveAndFlush(PointAccount.open("LOCK-M2", NOW));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(PointAccount.open("LOCK-M2", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 푼도 담을 수 없는 개인 한도는 표가 받지 않는다")
    void aLimitThatAllowsNothingIsRefusedByTheTable() {
        PointAccount account = PointAccount.open("LOCK-M3", NOW);
        account.changeMaxBalance(0L);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("가장 낮은 개인 한도 1 과 비워 둔 한도는 그대로 받는다")
    void theLowestLimitAndAnEmptyLimitAreBothAccepted() {
        PointAccount lowest = PointAccount.open("LOCK-M4", NOW);
        lowest.changeMaxBalance(1L);
        accountRepository.saveAndFlush(lowest);

        accountRepository.saveAndFlush(PointAccount.open("LOCK-M5", NOW));

        assertThat(accountRepository.findByMemberId("LOCK-M4")).get()
                .extracting(PointAccount::getMaxBalance).isEqualTo(1L);
        assertThat(accountRepository.findByMemberId("LOCK-M5")).get()
                .extracting(PointAccount::getMaxBalance).isNull();
    }
}
