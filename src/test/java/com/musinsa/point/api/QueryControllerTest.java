package com.musinsa.point.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.query.BalanceQueryService;
import com.musinsa.point.point.query.BalanceView;
import com.musinsa.point.point.query.EarningSummary;
import com.musinsa.point.point.query.EarningUsageQueryService;
import com.musinsa.point.point.query.EarningUsageView;
import com.musinsa.point.point.query.OrderUsage;
import com.musinsa.point.support.TestDoubleConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointQueryController.class)
@Import(TestDoubleConfig.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceQueryService balanceQueryService;

    @MockitoBean
    private EarningUsageQueryService earningUsageQueryService;

    @Test
    @DisplayName("잔액 조회는 금액과 기준 시각을 돌려준다")
    void theBalanceEndpointReturnsTheAmountAndTheMomentItWasRead() throws Exception {
        given(balanceQueryService.balance("M1"))
                .willReturn(new BalanceView(1_400, Instant.parse("2026-02-01T00:00:00Z")));

        mockMvc.perform(get("/api/v1/members/M1/points/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1_400))
                .andExpect(jsonPath("$.asOf").value("2026-02-01T00:00:00Z"));
    }

    @Test
    @DisplayName("계정이 없는 회원의 잔액 조회는 404 로 나간다")
    void readingTheBalanceOfAnUnknownMemberIsNotFound() throws Exception {
        willThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND)).given(balanceQueryService).balance("M1");

        mockMvc.perform(get("/api/v1/members/M1/points/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("적립별 사용 내역은 적립 요약과 주문별 사용액을 함께 돌려준다")
    void theUsageEndpointReturnsTheEarningAndItsOrders() throws Exception {
        given(earningUsageQueryService.usages("M1", "PK-A")).willReturn(new EarningUsageView(
                new EarningSummary("PK-A", EarningKind.MANUAL, EarningStatus.ACTIVE, 1_000, 600,
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")),
                List.of(new OrderUsage("O1", "PK-B", 300, 100, 200),
                        new OrderUsage("O2", "PK-C", 200, 0, 200))));

        mockMvc.perform(get("/api/v1/members/M1/points/earn/PK-A/usages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earning.pointKey").value("PK-A"))
                .andExpect(jsonPath("$.earning.kind").value("MANUAL"))
                .andExpect(jsonPath("$.earning.status").value("ACTIVE"))
                .andExpect(jsonPath("$.earning.originalAmount").value(1_000))
                .andExpect(jsonPath("$.earning.remainingAmount").value(600))
                .andExpect(jsonPath("$.earning.expiresAt").value("2027-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.usages.length()").value(2))
                .andExpect(jsonPath("$.usages[0].orderNo").value("O1"))
                .andExpect(jsonPath("$.usages[0].usePointKey").value("PK-B"))
                .andExpect(jsonPath("$.usages[0].usedAmount").value(300))
                .andExpect(jsonPath("$.usages[0].canceledAmount").value(100))
                .andExpect(jsonPath("$.usages[0].netUsedAmount").value(200));
    }

    @Test
    @DisplayName("없는 적립 키로 사용 내역을 물으면 404 로 나간다")
    void readingTheUsageOfAnUnknownEarningIsNotFound() throws Exception {
        willThrow(new ApiException(ErrorCode.POINT_KEY_NOT_FOUND))
                .given(earningUsageQueryService).usages("M1", "PK-A");

        mockMvc.perform(get("/api/v1/members/M1/points/earn/PK-A/usages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT_KEY_NOT_FOUND"));
    }
}
