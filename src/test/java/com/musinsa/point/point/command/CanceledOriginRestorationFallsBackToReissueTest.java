package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 정상 API 흐름에서는 사용된 적립 건을 적립취소로 CANCELED 로 만들 수 없다(이미 사용된 적립은 취소를 거절한다).
 * 그래서 "사용취소 복원 대상이 CANCELED 인 경우"는 API 로는 재현할 수 없고, 방어 분기가 실제로 만료 케이스와
 * 같은 길(재적립)을 타는지는 리포지토리 수준에서 상태를 직접 만들어 확인해야 한다.
 */
class CanceledOriginRestorationFallsBackToReissueTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M90";

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

    @Test
    @DisplayName("적립취소된 적립 건에서 사용된 이력은 존재할 수 없으므로, 사용취소 복원 대상이 ACTIVE 가 아니면 재적립으로 처리한다")
    void restorationOfACanceledOriginIsReissuedInsteadOfRestoredInPlace() {
        earnService.earn(MEMBER_ID, 1_000, 365);
        useService.use(MEMBER_ID, "ORDER-90", 1_000);

        // 정상 흐름으로는 도달할 수 없는 상태(사용된 적립 건이 CANCELED)를 리포지토리 수준에서 직접 만든다.
        PointEarning usedEarning = earningOf("A");
        usedEarning.cancel();
        earningRepository.save(usedEarning);

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, "B", 1_000);

        assertThat(canceled.restorations()).hasSize(1);
        UseCancelRestoration restoration = canceled.restorations().get(0);
        assertThat(restoration.reissued()).isTrue();
        assertThat(restoration.earningPointKey()).isEqualTo("A");
        assertThat(restoration.newEarningPointKey()).isNotNull();

        PointEarning reissued = earningOf(restoration.newEarningPointKey());
        assertThat(reissued.getStatus()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(reissued.getOriginalAmount()).isEqualTo(1_000);

        // 원래 적립 건은 CANCELED 로 그대로 남고, 이 경로 때문에 되살아나지 않는다.
        PointEarning origin = earningOf("A");
        assertThat(origin.getStatus()).isEqualTo(EarningStatus.CANCELED);
        assertThat(origin.getRemainingAmount()).isEqualTo(0);
    }
}
