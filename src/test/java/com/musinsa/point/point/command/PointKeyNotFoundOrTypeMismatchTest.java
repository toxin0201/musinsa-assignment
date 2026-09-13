package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PointKeyNotFoundOrTypeMismatchTest extends AbstractPointIntegrationTest {

    @Autowired
    private EarnService earnService;

    @Autowired
    private EarnCancelService earnCancelService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    private ErrorCode cancelRejectionCode(String memberId, String pointKey) {
        return catchThrowableOfType(() -> earnCancelService.cancel(memberId, pointKey), ApiException.class)
                .getErrorCode();
    }

    @Test
    @DisplayName("계정이 없는 회원의 적립취소는 회원을 찾을 수 없다는 응답이다")
    void cancelingForAMemberWithoutAnAccountReportsMissingMember() {
        assertThat(cancelRejectionCode("NO-SUCH-MEMBER", "ANY")).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("어디에도 없는 포인트 키로 적립취소하면 거래를 찾을 수 없다는 응답이다")
    void unknownPointKeyIsReportedAsMissingTransaction() {
        earnService.earn("MK1", 1_000, null);

        assertThat(cancelRejectionCode("MK1", "NOPE")).isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("사용 거래의 포인트 키를 적립취소에 쓰면 그 타입의 거래가 없다는 응답이다")
    void useTransactionKeyIsNotAnEarningKey() {
        earnService.earn("MK2", 1_000, null);
        PointAccount account = accountRepository.findByMemberId("MK2").orElseThrow();
        transactionRepository.saveAndFlush(
                PointTransaction.use(account, "USE-KEY", 100, "O-MK2", clock.instant()));

        assertThat(cancelRejectionCode("MK2", "USE-KEY")).isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회원의 적립을 내 포인트 키처럼 취소할 수 없다")
    void anotherMembersEarningIsNotVisible() {
        EarnResult otherMembersEarning = earnService.earn("MK3", 1_000, null);
        earnService.earn("MK4", 1_000, null);

        assertThat(cancelRejectionCode("MK4", otherMembersEarning.pointKey()))
                .isEqualTo(ErrorCode.POINT_KEY_NOT_FOUND);
    }
}
