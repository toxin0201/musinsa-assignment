package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class UseRequestValidationTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M24";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @ParameterizedTest(name = "{0}원 사용 요청은 거절된다")
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("사용 금액은 1원 이상이어야 한다")
    void amountMustBeAtLeastOne(long amount) {
        earnService.earn(MEMBER_ID, 1_000, 100);

        assertThat(rejectionCodeOf(() -> useService.use(MEMBER_ID, "ORDER-50", amount)))
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("주문번호 없이는 포인트를 쓸 수 없다")
    void orderNumberIsRequired() {
        earnService.earn(MEMBER_ID, 1_000, 100);

        assertThat(rejectionCodeOf(() -> useService.use(MEMBER_ID, null, 100)))
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest(name = "주문번호가 \"{0}\" 이면 거절된다")
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("공백만 있는 주문번호는 주문번호로 보지 않는다")
    void blankOrderNumberIsRejected(String orderNo) {
        earnService.earn(MEMBER_ID, 1_000, 100);

        assertThat(rejectionCodeOf(() -> useService.use(MEMBER_ID, orderNo, 100)))
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("형식이 틀린 요청은 회원 계정을 찾기 전에 거절된다")
    void malformedRequestsAreRejectedBeforeTouchingTheAccount() {
        assertThat(rejectionCodeOf(() -> useService.use("NO-SUCH-MEMBER", "ORDER-51", 0)))
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
