package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class RemainingCancelableAmountExhaustionTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M41";
    private static final String ORDER_NO = "ORDER-60";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private UseCancelService useCancelService;

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    private String spend(long amount) {
        earnService.earn(MEMBER_ID, 1_000, 365);
        return useService.use(MEMBER_ID, ORDER_NO, amount).pointKey();
    }

    @Test
    @DisplayName("나눠서 취소해도 합이 사용액을 넘지 못하고 마지막 요청에서 남은 금액이 0이 된다")
    void repeatedPartialCancelsStopExactlyAtTheUsedAmount() {
        String usePointKey = spend(600);

        assertThat(useCancelService.cancel(MEMBER_ID, usePointKey, 100).remainingCancelableAmount())
                .isEqualTo(500);
        assertThat(useCancelService.cancel(MEMBER_ID, usePointKey, 250).remainingCancelableAmount())
                .isEqualTo(250);
        assertThat(useCancelService.cancel(MEMBER_ID, usePointKey, 250).remainingCancelableAmount())
                .isZero();
        assertThat(rejectionCodeOf(() -> useCancelService.cancel(MEMBER_ID, usePointKey, 1)))
                .isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDED);
    }

    @Test
    @DisplayName("사용액보다 1원이라도 많이 취소하려 하면 아무것도 되돌리지 않고 거절한다")
    void askingForOneMoreThanWasSpentIsRejectedWholesale() {
        String usePointKey = spend(600);

        assertThat(rejectionCodeOf(() -> useCancelService.cancel(MEMBER_ID, usePointKey, 601)))
                .isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDED);
        assertThat(detailRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}원 취소 요청은 거절된다")
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("취소 금액은 1원 이상이어야 한다")
    void cancelAmountMustBeAtLeastOne(long amount) {
        String usePointKey = spend(600);

        assertThat(rejectionCodeOf(() -> useCancelService.cancel(MEMBER_ID, usePointKey, amount)))
                .isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDED);
    }

    @Test
    @DisplayName("사용액 전부를 한 번에 취소하면 남은 취소 가능액이 바로 0이 된다")
    void cancelingEverythingAtOnceLeavesNothingToCancel() {
        String usePointKey = spend(600);

        UseCancelResult canceled = useCancelService.cancel(MEMBER_ID, usePointKey, 600);

        assertThat(canceled.canceledAmount()).isEqualTo(600);
        assertThat(canceled.remainingCancelableAmount()).isZero();
        assertThat(canceled.balance()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("돌려줄 수 있는 범위를 벗어나면 그 범위를 그대로 알려 준다")
    void theRejectionNamesTheRangeThatIsStillCancelable() {
        String usePointKey = spend(600);

        assertThat(catchThrowableOfType(ApiException.class,
                () -> useCancelService.cancel(MEMBER_ID, usePointKey, 601)))
                .hasMessage("취소할 수 있는 금액은 1 이상 600 이하입니다.");
    }

    @Test
    @DisplayName("더 돌려줄 금액이 없으면 범위 대신 남은 것이 없다고 알린다")
    void anExhaustedUseSaysThereIsNothingLeftToCancel() {
        String usePointKey = spend(600);
        useCancelService.cancel(MEMBER_ID, usePointKey, 600);

        assertThat(catchThrowableOfType(ApiException.class,
                () -> useCancelService.cancel(MEMBER_ID, usePointKey, 1)))
                .hasMessage("취소할 수 있는 금액이 남아 있지 않습니다.");
    }

    @Test
    @DisplayName("적립 거래의 pointKey 로는 사용취소할 수 없다")
    void anEarnPointKeyIsNotAUsePointKey() {
        spend(600);

        assertThat(rejectionCodeOf(() -> useCancelService.cancel(MEMBER_ID, "A", 100)))
                .isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회원의 사용은 취소할 수 없다")
    void anotherMembersUseIsOutOfReach() {
        String usePointKey = spend(600);
        earnService.earn("M42", 1_000, 365);

        assertThat(rejectionCodeOf(() -> useCancelService.cancel("M42", usePointKey, 100)))
                .isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 pointKey 로 사용취소하면 찾을 수 없다고 알린다")
    void unknownPointKeyIsReported() {
        spend(600);

        assertThat(rejectionCodeOf(() -> useCancelService.cancel(MEMBER_ID, "ZZZZ", 100)))
                .isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }
}
