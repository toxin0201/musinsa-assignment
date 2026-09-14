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

/**
 * 계정이 없는 회원에게 요청이 한꺼번에 몰리는 순간을 좁혀 본다.
 * 계정을 여는 쪽이 먼저 열린 계정을 그대로 쓰므로, 겹쳐도 요청이 실패하지 않고 계정도 하나만 남아야 한다.
 */
class FirstEarnAccountOpeningRaceTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M95";
    private static final int CONCURRENT_REQUESTS = 20;
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
    @DisplayName("같은 회원의 첫 적립 스무 건이 겹쳐도 전부 성공하고 계정은 하나만 열린다")
    void twentyFirstEarningsOfOneMemberAllSucceedOnASingleAccount() {
        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> earnService.earn(MEMBER_ID, AMOUNT_PER_REQUEST, 365));

        assertThat(outcomes).allMatch(Optional::isEmpty);
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(earningRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(balanceQueryService.balance(MEMBER_ID).balance())
                .isEqualTo(CONCURRENT_REQUESTS * AMOUNT_PER_REQUEST);
    }

    @Test
    @DisplayName("서로 다른 회원 스무 명의 첫 적립은 서로를 막지 않고 각자 계정을 얻는다")
    void twentyDifferentMembersEachGetTheirOwnAccount() {
        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> earnService.earn("M96-" + index, AMOUNT_PER_REQUEST, 365));

        assertThat(outcomes).allMatch(Optional::isEmpty);
        assertThat(accountRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(earningRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
    }
}
