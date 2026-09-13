package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReissueOnExpiredRestorationTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M44";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    private PointEarning earningOf(String pointKey) {
        return earningRepository
                .findByTransactionId(transactionRepository.findByPointKey(pointKey).orElseThrow().getId())
                .orElseThrow();
    }

    /** 수기 지급 1,000(30일 만료) 과 일반 적립 500 에서 1,200 을 쓰고 수기분이 만료되도록 시계를 옮긴다. */
    private void spendManualPointsAndLetThemExpire() {
        earnService.earnByAdmin(MEMBER_ID, 1_000, 30, "admin1", "보상 지급");
        earnService.earn(MEMBER_ID, 500, 365);
        useService.use(MEMBER_ID, "ORDER-70", 1_200);
        clock.advance(Duration.ofDays(31));
    }

    @Test
    @DisplayName("만료된 수기 지급분을 되돌리면 새 적립도 수기 지급으로 남는다")
    void theReissuedEarningInheritsTheManualKind() {
        spendManualPointsAndLetThemExpire();

        useCancelService.cancel(MEMBER_ID, "C", 1_000);

        PointEarning reissued = earningOf("E");
        assertThat(reissued.getKind()).isEqualTo(EarningKind.MANUAL);
        assertThat(reissued.getOriginalAmount()).isEqualTo(1_000);
        assertThat(reissued.getExpiresAt()).isEqualTo(clock.instant().plus(365, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("되살아난 수기 지급분은 다음 주문에서 일반 적립보다 먼저 쓰인다")
    void theReissuedManualEarningIsSpentFirstAgain() {
        spendManualPointsAndLetThemExpire();
        useCancelService.cancel(MEMBER_ID, "C", 1_000);
        earnService.earn(MEMBER_ID, 300, 365);

        UseResult used = useService.use(MEMBER_ID, "ORDER-71", 1_100);

        assertThat(used.allocations())
                .extracting(UseAllocation::earningPointKey, UseAllocation::amount)
                .containsExactly(tuple("E", 1_000L), tuple("B", 100L));
    }

    @Test
    @DisplayName("재적립은 종류만 물려받을 뿐 원래의 지급 관리자와 사유까지 옮겨오지는 않는다")
    void theReissuedEarningDoesNotCarryTheOriginalGrantPaperwork() {
        spendManualPointsAndLetThemExpire();

        useCancelService.cancel(MEMBER_ID, "C", 1_000);

        PointEarning reissued = earningOf("E");
        assertThat(reissued.getAdminId()).isNull();
        assertThat(reissued.getReason()).isNull();
        assertThat(earningOf("A").getAdminId()).isEqualTo("admin1");
    }

    @Test
    @DisplayName("만료 전에 취소하면 새 적립을 만들지 않고 원래 자리로 되돌린다")
    void cancelingBeforeExpiryRestoresInPlaceWithoutANewEarning() {
        earnService.earnByAdmin(MEMBER_ID, 1_000, 30, "admin1", "보상 지급");
        earnService.earn(MEMBER_ID, 500, 365);
        useService.use(MEMBER_ID, "ORDER-72", 1_200);

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, "C", 1_000);

        assertThat(canceled.restorations())
                .extracting(UseCancelRestoration::earningPointKey, UseCancelRestoration::reissued,
                        UseCancelRestoration::newEarningPointKey)
                .containsExactly(tuple("A", false, null));
        assertThat(earningOf("A").getRemainingAmount()).isEqualTo(1_000);
        assertThat(transactionRepository.findByPointKey("E")).isEmpty();
    }

    @Test
    @DisplayName("만료 시각과 정확히 같은 순간은 이미 만료로 보고 새 적립으로 돌려준다")
    void theExpiryInstantItselfCountsAsExpired() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        useService.use(MEMBER_ID, "ORDER-73", 1_000);
        clock.advance(Duration.ofDays(30));

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, "B", 1_000);

        assertThat(canceled.restorations())
                .extracting(UseCancelRestoration::reissued)
                .containsExactly(true);
    }
}
