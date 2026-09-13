package com.musinsa.point.point.command;

import static com.musinsa.point.support.ApiFailures.rejectionCodeOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DuplicateOrderRejectionTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M22";
    private static final String OTHER_MEMBER_ID = "M23";
    private static final String ORDER_NO = "ORDER-40";

    @Autowired
    private EarnService earnService;

    @Autowired
    private UseService useService;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @Autowired
    private PointTransactionDetailRepository detailRepository;

    @Test
    @DisplayName("같은 주문번호로 두 번 사용하면 거절되고 포인트도 다시 빠지지 않는다")
    void theSameOrderCannotSpendPointsTwice() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        UseResult first = useService.use(MEMBER_ID, ORDER_NO, 300);

        ErrorCode rejected = rejectionCodeOf(() -> useService.use(MEMBER_ID, ORDER_NO, 300));

        assertThat(rejected).isEqualTo(ErrorCode.DUPLICATE_ORDER);
        assertThat(first.balance()).isEqualTo(700);
        assertThat(detailRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문번호가 다르면 같은 회원도 이어서 사용할 수 있다")
    void differentOrdersFromTheSameMemberBothSucceed() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        useService.use(MEMBER_ID, ORDER_NO, 300);

        UseResult second = useService.use(MEMBER_ID, "ORDER-41", 200);

        assertThat(second.balance()).isEqualTo(500);
    }

    @Test
    @DisplayName("주문번호 중복은 회원 안에서만 따지므로 다른 회원의 같은 주문번호는 막지 않는다")
    void theSameOrderNumberFromAnotherMemberIsAccepted() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(OTHER_MEMBER_ID, 1_000, 100);
        useService.use(MEMBER_ID, ORDER_NO, 300);

        UseResult other = useService.use(OTHER_MEMBER_ID, ORDER_NO, 400);

        assertThat(other.balance()).isEqualTo(600);
    }

    @Test
    @DisplayName("적립과 적립취소 거래는 주문번호를 쓰지 않아 중복 제약에 걸리지 않는다")
    void earningsDoNotOccupyTheOrderNumberSlot() {
        earnService.earn(MEMBER_ID, 1_000, 100);
        earnService.earn(MEMBER_ID, 1_000, 100);

        UseResult used = useService.use(MEMBER_ID, ORDER_NO, 100);

        assertThat(used.balance()).isEqualTo(1_900);
        assertThat(transactionRepository.count()).isEqualTo(3);
    }
}
