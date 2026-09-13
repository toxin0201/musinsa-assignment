package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EarnAmountBoundaryTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M2";

    @Autowired
    private EarnService earnService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PointEarningRepository earningRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    private long balanceOf(String memberId) {
        return accountRepository.findByMemberId(memberId)
                .map(account -> earningRepository.sumAvailableBalance(account.getId(), clock.instant()))
                .orElse(0L);
    }

    @Test
    @DisplayName("1회 적립 하한과 상한에 딱 맞는 금액은 적립된다")
    void amountsAtTheInclusiveBoundsAreAccepted() {
        EarnResult smallest = earnService.earn(MEMBER_ID, 1, null);
        EarnResult largest = earnService.earn(MEMBER_ID, 100_000, null);

        assertThat(smallest.amount()).isEqualTo(1);
        assertThat(smallest.balance()).isEqualTo(1);
        assertThat(largest.amount()).isEqualTo(100_000);
        assertThat(largest.balance()).isEqualTo(100_001);
        assertThat(balanceOf(MEMBER_ID)).isEqualTo(100_001);
    }

    @Test
    @DisplayName("1회 적립 상한을 넘는 금액은 거절되고 적립 건도 거래도 남지 않는다")
    void amountAboveTheUpperBoundLeavesNothingBehind() {
        earnService.earn(MEMBER_ID, 1_000, null);

        ApiException rejected = catchThrowableOfType(ApiException.class, () -> earnService.earn(MEMBER_ID, 100_001, null));

        assertThat(rejected.getErrorCode()).isEqualTo(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE);
        assertThat(balanceOf(MEMBER_ID)).isEqualTo(1_000);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(earningRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("0 이하 금액은 잘못된 요청으로 거절한다")
    void zeroOrNegativeAmountIsARequestError() {
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earn(MEMBER_ID, 0, null))
                .getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earn(MEMBER_ID, -100, null))
                .getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(accountRepository.findByMemberId(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("표현할 수 있는 가장 큰 금액도 잔액에 더해 보기 전에 거절한다")
    void hugeAmountIsRejectedBeforeTouchingTheBalance() {
        earnService.earn(MEMBER_ID, 1_000, null);

        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earn(MEMBER_ID, Long.MAX_VALUE, null))
                .getErrorCode()).isEqualTo(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE);
        assertThat(balanceOf(MEMBER_ID)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("실패한 적립을 섞어도 총잔액은 성공한 적립의 합과 같다")
    void balanceEqualsTheSumOfSuccessfulEarningsOnly() {
        earnService.earn(MEMBER_ID, 1, null);
        catchThrowableOfType(ApiException.class, () -> earnService.earn(MEMBER_ID, 0, null));
        earnService.earn(MEMBER_ID, 100_000, null);
        catchThrowableOfType(ApiException.class, () -> earnService.earn(MEMBER_ID, 100_001, null));
        earnService.earn(MEMBER_ID, 999, null);

        assertThat(balanceOf(MEMBER_ID)).isEqualTo(100_001 + 999);
        assertThat(earningRepository.count()).isEqualTo(3);
    }
}
