package com.musinsa.point.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.point.query.BalanceQueryService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import com.musinsa.point.support.ConcurrentRuns;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 같은 회원의 요청은 계정 행 잠금으로 한 줄로 세워진다. 잔액이 음수가 되거나 합이 어긋나면 안 된다. */
class ConcurrentUseSerializationTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M70";
    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private BalanceQueryService balanceQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void fillBalanceTo(long total, long perEarning) {
        for (long filled = 0; filled < total; filled += perEarning) {
            earnService.earn(MEMBER_ID, perEarning, 365);
        }
    }

    private long minimumRemainingAmount() {
        return jdbcTemplate.queryForObject("SELECT MIN(remaining_amount) FROM point_earning", Long.class);
    }

    @Test
    @DisplayName("잔액을 정확히 나눠 쓰는 동시 요청은 모두 성공하고 잔액이 0으로 맞아떨어진다")
    void concurrentRequestsThatFitExactlyAllSucceed() {
        fillBalanceTo(1_000_000, 100_000);

        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> useService.use(MEMBER_ID, "ORDER-70-" + index, 100_000));

        assertThat(outcomes).allMatch(Optional::isEmpty);
        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isZero();
        assertThat(minimumRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("잔액보다 많은 동시 요청이 몰려도 쓸 수 있는 만큼만 나가고 잔액은 음수가 되지 않는다")
    void concurrentRequestsBeyondTheBalanceStopAtZero() {
        fillBalanceTo(1_000_000, 100_000);

        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> useService.use(MEMBER_ID, "ORDER-71-" + index, 150_000));

        long succeeded = outcomes.stream().filter(Optional::isEmpty).count();
        assertThat(succeeded).isEqualTo(6);
        assertThat(outcomes.stream().flatMap(Optional::stream))
                .allSatisfy(failure -> assertThat(((ApiException) failure).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isEqualTo(100_000);
        assertThat(minimumRemainingAmount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("같은 주문번호로 한꺼번에 몰려와도 포인트는 한 번만 빠진다")
    void theSameOrderArrivingAtOnceSpendsPointsOnlyOnce() {
        fillBalanceTo(1_000_000, 100_000);

        List<Optional<Throwable>> outcomes = ConcurrentRuns.runAll(CONCURRENT_REQUESTS,
                index -> useService.use(MEMBER_ID, "ORDER-72", 100_000));

        assertThat(outcomes.stream().filter(Optional::isEmpty).count()).isEqualTo(1);
        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isEqualTo(900_000);
    }
}
