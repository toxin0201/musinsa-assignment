package com.musinsa.point.concurrency;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 회원의 다른 요청이 계정 행을 오래 쥐고 있으면 뒤따라온 요청은 기다리다 포기한다.
 * 그 포기가 500 이 아니라 "잠시 후 다시"라는 뜻의 409 로 나가야 한다.
 */
@AutoConfigureMockMvc
class LockWaitTimeoutConflictTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M73";
    private static final Duration LOCK_HOLD = Duration.ofSeconds(4);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EarnService earnService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("앞선 요청이 잠금을 오래 쥐고 있으면 뒤따라온 요청은 409 로 돌아간다")
    void aRequestThatWaitsTooLongForTheLockGetsAConflict() throws Exception {
        earnService.earn(MEMBER_ID, 1_000, 365);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        Thread holder = startLockHolder(lockAcquired);

        try {
            lockAcquired.await(5, TimeUnit.SECONDS);

            mockMvc.perform(post("/api/v1/members/" + MEMBER_ID + "/points/use")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderNo\":\"ORDER-73\",\"amount\":100}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("UPDATE_CONFLICT"));
        } finally {
            holder.join();
        }
    }

    /** 계정 행을 잠근 채로 잠금 대기 상한보다 오래 붙들고 있는 요청을 흉내 낸다. */
    private Thread startLockHolder(CountDownLatch lockAcquired) {
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
            lockAcquired.countDown();
            try {
                Thread.sleep(LOCK_HOLD.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        return holder;
    }
}
