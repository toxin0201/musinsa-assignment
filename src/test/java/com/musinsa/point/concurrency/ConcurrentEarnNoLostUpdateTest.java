package com.musinsa.point.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.query.BalanceQueryService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import com.musinsa.point.support.ConcurrentRuns;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConcurrentEarnNoLostUpdateTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M71";
    private static final int CONCURRENT_REQUESTS = 10;
    private static final long AMOUNT_PER_REQUEST = 1_000;

    @Autowired
    private EarnService earnService;

    @Autowired
    private BalanceQueryService balanceQueryService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PointEarningRepository earningRepository;

    @Test
    @DisplayName("첫 적립이 한꺼번에 몰려도 계정은 하나만 열리고 적립은 하나도 유실되지 않는다")
    void concurrentFirstEarningsOpenOneAccountAndKeepEveryEarning() {
        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> earnService.earn(MEMBER_ID, AMOUNT_PER_REQUEST, 365));

        assertThat(outcomes).allMatch(Optional::isEmpty);
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(earningRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(balanceQueryService.balance(MEMBER_ID).balance())
                .isEqualTo(CONCURRENT_REQUESTS * AMOUNT_PER_REQUEST);
    }

    @Test
    @DisplayName("이미 계정이 있는 회원에게 동시에 적립해도 합계가 그대로 맞는다")
    void concurrentEarningsOnAnExistingAccountAddUpExactly() {
        earnService.earn(MEMBER_ID, AMOUNT_PER_REQUEST, 365);

        ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> earnService.earn(MEMBER_ID, AMOUNT_PER_REQUEST, 365));

        assertThat(balanceQueryService.balance(MEMBER_ID).balance())
                .isEqualTo((CONCURRENT_REQUESTS + 1) * AMOUNT_PER_REQUEST);
    }

    @Test
    @DisplayName("서로 다른 회원의 적립은 서로를 기다리지 않는다")
    void earningsOfDifferentMembersDoNotBlockEachOther() {
        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> earnService.earn("M72-" + index, AMOUNT_PER_REQUEST, 365));

        assertThat(outcomes).allMatch(Optional::isEmpty);
        assertThat(accountRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
    }
}
