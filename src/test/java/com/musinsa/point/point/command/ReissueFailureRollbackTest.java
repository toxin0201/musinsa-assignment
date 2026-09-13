package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 만료분 재적립이 실패하면 되돌리기 전체가 없던 일이 되어야 한다.
 * 되돌아간 금액만 남고 새 적립은 없는 상태가 보이면 장부가 어긋난다.
 */
class ReissueFailureRollbackTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M46";

    @MockitoSpyBean
    private PointEarningRepository earningRepository;

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    private long remainingOf(String pointKey) {
        return earningRepository
                .findByTransactionId(transactionRepository.findByPointKey(pointKey).orElseThrow().getId())
                .orElseThrow()
                .getRemainingAmount();
    }

    @Test
    @DisplayName("만료분 재적립이 실패하면 살아 있던 몫의 복원도 함께 없던 일이 된다")
    void aFailedReissueUndoesTheWholeCancel() {
        earnService.earn(MEMBER_ID, 1_000, 30);
        earnService.earn(MEMBER_ID, 500, 365);
        useService.use(MEMBER_ID, "ORDER-90", 1_200);
        clock.advance(Duration.ofDays(31));
        doThrow(new DataIntegrityViolationException("재적립 저장 실패"))
                .when(earningRepository)
                .save(argThat((PointEarning earning) ->
                        earning != null && earning.getReissuedFromTransactionId() != null));

        assertThatThrownBy(() -> useCancelService.cancel(MEMBER_ID, "C", 1_200))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(remainingOf("A")).isZero();
        assertThat(remainingOf("B")).isEqualTo(300);
        assertThat(transactionRepository.findByPointKey("D")).isEmpty();
        assertThat(transactionRepository.findByPointKey("E")).isEmpty();
        assertThat(detailRepository.count()).isEqualTo(2);
    }
}
