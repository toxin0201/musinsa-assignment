package com.musinsa.point.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseResult;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.point.query.BalanceQueryService;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 적립 건 하나하나가 언제나 "최초 금액 − 나간 금액 + 돌아온 금액" 만큼 남아 있어야 한다.
 * 여러 씨앗으로 서로 다른 순서의 시나리오를 돌린 뒤 모든 적립 건에서 이 식을 확인한다.
 *
 * <p>만료분을 되돌리며 생긴 적립 건은 예외를 하나 둔다. 그 건을 만든 복원 내역은 이미 최초 금액 안에
 * 들어 있으므로, 되돌아온 금액에 다시 더하면 같은 돈을 두 번 세게 된다. 그래서 "자신을 만든 사용취소
 * 거래의 복원 내역"만 빼고 센다.
 */
class EarningBalanceInvariantAllScenariosTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M74";
    private static final int OPERATIONS_PER_RUN = 60;

    @Autowired
    private EarnService earnService;

    @Autowired
    private EarnCancelService earnCancelService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private BalanceQueryService balanceQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "씨앗 {0} 으로 만든 시나리오에서도 적립 건 잔액 식이 성립한다")
    @ValueSource(longs = {1, 7, 42, 2026})
    @DisplayName("어떤 순서로 적립 · 사용 · 취소가 섞여도 적립 건 잔액 식은 깨지지 않는다")
    void everyEarningKeepsItsBalanceEquation(long seed) {
        runRandomScenario(seed);

        assertThat(earningLedgerRows()).isNotEmpty().allSatisfy(row -> {
            long original = asLong(row.get("ORIGINAL_AMOUNT"));
            long remaining = asLong(row.get("REMAINING_AMOUNT"));
            long spent = asLong(row.get("SPENT"));
            long restored = asLong(row.get("RESTORED"));
            if ("CANCELED".equals(row.get("STATUS"))) {
                assertThat(remaining).isZero();
                assertThat(spent).isZero();
            } else {
                assertThat(remaining).isEqualTo(original - spent + restored);
            }
        });
    }

    @ParameterizedTest(name = "씨앗 {0} 으로 만든 시나리오에서도 잔액은 쓸 수 있는 적립의 합이다")
    @ValueSource(longs = {1, 7, 42, 2026})
    @DisplayName("조회한 잔액은 지금 쓸 수 있는 적립 건의 남은 금액 합과 같다")
    void theReportedBalanceMatchesTheSumOfUsableEarnings(long seed) {
        runRandomScenario(seed);

        long usableSum = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(remaining_amount), 0) FROM point_earning
                WHERE status = 'ACTIVE' AND expires_at > ?
                """, Long.class, java.sql.Timestamp.from(clock.instant()));

        assertThat(balanceQueryService.balance(MEMBER_ID).balance()).isEqualTo(usableSum);
    }

    /** 적립 · 수기 지급 · 사용 · 사용취소 · 적립취소 · 시간 경과를 섞는다. 거절은 정상적인 결과이므로 삼킨다. */
    private void runRandomScenario(long seed) {
        Random random = new Random(seed);
        List<String> earnKeys = new ArrayList<>();
        List<String> useKeys = new ArrayList<>();

        for (int step = 0; step < OPERATIONS_PER_RUN; step++) {
            try {
                switch (random.nextInt(6)) {
                    case 0 -> earnKeys.add(earnService
                            .earn(MEMBER_ID, 1 + random.nextInt(50_000), 1 + random.nextInt(400)).pointKey());
                    case 1 -> earnKeys.add(earnService
                            .earnByAdmin(MEMBER_ID, 1 + random.nextInt(50_000), 1 + random.nextInt(400),
                                    "admin1", "무작위 지급").pointKey());
                    case 2 -> {
                        UseResult used = useService.use(MEMBER_ID, "ORDER-74-" + step, 1 + random.nextInt(60_000));
                        useKeys.add(used.pointKey());
                    }
                    case 3 -> useCancelService.cancel(MEMBER_ID, pick(random, useKeys),
                            1 + random.nextInt(60_000));
                    case 4 -> earnCancelService.cancel(MEMBER_ID, pick(random, earnKeys));
                    default -> clock.advance(Duration.ofDays(1 + random.nextInt(120)));
                }
            } catch (ApiException expectedRejection) {
                // 한도 초과 · 잔액 부족 · 이미 사용됨 같은 거절도 시나리오의 일부다.
            }
        }
    }

    /** H2 의 SUM 은 BigDecimal 로, 컬럼 값은 Long 으로 온다. 둘 다 숫자로만 다룬다. */
    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static String pick(Random random, List<String> keys) {
        if (keys.isEmpty()) {
            return "NONE";
        }
        return keys.get(random.nextInt(keys.size()));
    }

    private List<Map<String, Object>> earningLedgerRows() {
        return jdbcTemplate.queryForList("""
                SELECT e.original_amount AS ORIGINAL_AMOUNT,
                       e.remaining_amount AS REMAINING_AMOUNT,
                       e.status           AS STATUS,
                       COALESCE((SELECT SUM(d.amount) FROM point_transaction_detail d
                                 WHERE d.earning_id = e.id AND d.direction = 'OUT'), 0) AS SPENT,
                       COALESCE((SELECT SUM(d.amount) FROM point_transaction_detail d
                                 WHERE d.earning_id = e.id AND d.direction = 'IN'
                                   AND (e.reissued_from_transaction_id IS NULL
                                        OR d.transaction_id <> e.reissued_from_transaction_id)), 0) AS RESTORED
                FROM point_earning e
                """);
    }
}
