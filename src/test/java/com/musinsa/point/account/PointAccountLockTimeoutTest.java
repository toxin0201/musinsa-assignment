package com.musinsa.point.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 회원의 포인트를 동시에 바꾸려는 두 요청 중 뒤에 온 쪽은 무한정 기다리지 않고 정해진 시간 안에 포기해야 한다.
 * 그때 어떤 예외가 올라오는지가 응답 코드 매핑의 근거가 되므로 실제 동작으로 고정한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointAccountLockTimeoutTest {

    private static final String MEMBER_ID = "LOCK-TIMEOUT";
    private static final Duration CONFIGURED_WAIT = Duration.ofSeconds(3);

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;
    private ExecutorService holder;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        holder = Executors.newSingleThreadExecutor();
        transactionTemplate.executeWithoutResult(status ->
                accountRepository.save(PointAccount.open(MEMBER_ID, Instant.parse("2026-01-01T00:00:00Z"))));
    }

    @AfterEach
    void tearDown() {
        holder.shutdownNow();
        jdbcTemplate.update("DELETE FROM point_account WHERE member_id = ?", MEMBER_ID);
    }

    @Test
    @DisplayName("앞선 요청이 계정 행을 붙잡고 있으면 뒤에 온 요청은 설정한 대기 시간만큼만 기다리고 잠금 실패로 끝난다")
    void secondRequestGivesUpAfterConfiguredWait() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        holder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
            locked.countDown();
            sleep(Duration.ofSeconds(6));
        }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status ->
                accountRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow()));
        Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(thrown).isExactlyInstanceOf(PessimisticLockingFailureException.class);
        assertThat(waited).isBetween(CONFIGURED_WAIT.minusMillis(700), CONFIGURED_WAIT.plusMillis(1_500));
    }

    /**
     * 대기 시간을 질의마다 따로 주는 방법은 이 DB 에서 통하지 않는다. 접속 설정에 적어 둔 값 하나가 전부를 결정하므로
     * 대기 시간을 바꾸려면 접속 설정을 고쳐야 한다. 리포지토리를 거치지 않고 직접 질의하면 예외도 변환되지 않은
     * 표준 JPA 예외 그대로 올라온다는 점까지 함께 고정한다.
     */
    @Test
    @DisplayName("질의마다 대기 시간을 따로 지정해도 접속 설정의 대기 시간이 그대로 적용된다")
    void perQueryLockTimeoutHintIsIgnored() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        holder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
            locked.countDown();
            sleep(Duration.ofSeconds(6));
        }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("select a from PointAccount a where a.memberId = :memberId",
                                PointAccount.class)
                        .setParameter("memberId", MEMBER_ID)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .setHint("jakarta.persistence.lock.timeout", 1_000)
                        .getSingleResult()));
        Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(thrown).isInstanceOf(jakarta.persistence.LockTimeoutException.class);
        assertThat(waited).isBetween(CONFIGURED_WAIT.minusMillis(700), CONFIGURED_WAIT.plusMillis(1_500));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
