package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ManualEarnIdentificationTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M7";

    @Autowired
    private EarnService earnService;

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
    @DisplayName("관리자 수기 적립은 수기 종류로 남고 누가 왜 지급했는지가 함께 기록된다")
    void manualEarningRecordsWhoGrantedItAndWhy() {
        EarnResult result = earnService.earnByAdmin(MEMBER_ID, 5_000, null, "admin01", "보상 지급");

        assertThat(result.kind()).isEqualTo(EarningKind.MANUAL);
        PointEarning earning = earningOf(result.pointKey());
        assertThat(earning.getKind()).isEqualTo(EarningKind.MANUAL);
        assertThat(earning.getAdminId()).isEqualTo("admin01");
        assertThat(earning.getReason()).isEqualTo("보상 지급");
    }

    @Test
    @DisplayName("일반 적립은 수기 적립과 구분되고 관리자 정보를 갖지 않는다")
    void generalEarningIsDistinguishableFromManualOne() {
        EarnResult result = earnService.earn(MEMBER_ID, 5_000, null);

        assertThat(result.kind()).isEqualTo(EarningKind.GENERAL);
        PointEarning earning = earningOf(result.pointKey());
        assertThat(earning.getKind()).isEqualTo(EarningKind.GENERAL);
        assertThat(earning.getAdminId()).isNull();
        assertThat(earning.getReason()).isNull();
    }

    @Test
    @DisplayName("관리자 정보 없이는 수기 적립을 만들 수 없다")
    void manualEarningRequiresAdminIdAndReason() {
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earnByAdmin(MEMBER_ID, 5_000, null, null, "보상"))
                .getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earnByAdmin(MEMBER_ID, 5_000, null, " ", "보상"))
                .getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earnByAdmin(MEMBER_ID, 5_000, null, "admin01", null))
                .getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earnByAdmin(MEMBER_ID, 5_000, null, "admin01", " "))
                .getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(earningRepository.count()).isZero();
    }

    @Test
    @DisplayName("수기 적립도 1회 적립 금액과 만료일수 규칙을 똑같이 지킨다")
    void manualEarningObeysTheSameAmountAndExpiryRules() {
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earnByAdmin(MEMBER_ID, 100_001, null, "admin01", "보상"))
                .getErrorCode()).isEqualTo(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE);
        assertThat(catchThrowableOfType(ApiException.class, () -> earnService.earnByAdmin(MEMBER_ID, 100, 0, "admin01", "보상"))
                .getErrorCode()).isEqualTo(ErrorCode.EXPIRY_OUT_OF_RANGE);
    }
}
