package com.musinsa.point.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnResult;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.support.TestDoubleConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointController.class)
@Import(TestDoubleConfig.class)
class EarnControllerTest {

    private static final String EARN_PATH = "/api/v1/members/M1/points/earn";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EarnService earnService;

    @MockitoBean
    private EarnCancelService earnCancelService;

    @MockitoBean
    private UseService useService;

    @MockitoBean
    private UseCancelService useCancelService;

    @Test
    @DisplayName("적립에 성공하면 발급된 포인트 키와 만료 시각과 잔액을 돌려준다")
    void earningReturnsThePointKeyExpiryAndBalance() throws Exception {
        Instant expiresAt = Instant.parse("2027-01-01T00:00:00Z");
        given(earnService.earn("M1", 1_000L, 365))
                .willReturn(new EarnResult("PK1", 1_000, EarningKind.GENERAL, expiresAt, 1_000));

        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"expireDays\":365}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointKey").value("PK1"))
                .andExpect(jsonPath("$.amount").value(1_000))
                .andExpect(jsonPath("$.kind").value("GENERAL"))
                .andExpect(jsonPath("$.expiresAt").value("2027-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.balance").value(1_000));
    }

    @Test
    @DisplayName("만료일수를 적지 않으면 기본값을 쓰도록 비운 채로 넘긴다")
    void omittingTheExpiryDaysLeavesItUnset() throws Exception {
        given(earnService.earn(eq("M1"), eq(1_000L), any()))
                .willReturn(new EarnResult("PK1", 1_000, EarningKind.GENERAL, Instant.EPOCH, 1_000));

        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isOk());

        verify(earnService).earn("M1", 1_000L, null);
    }

    @Test
    @DisplayName("적립 금액이 0이면 서비스를 부르지 않고 잘못된 요청으로 돌려보낸다")
    void zeroAmountNeverReachesTheService() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        verify(earnService, never()).earn(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("금액을 아예 빼먹은 요청도 잘못된 요청이다")
    void aRequestWithoutAnAmountIsRejected() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("적립 한도를 넘으면 서비스가 정한 코드와 상태로 그대로 나간다")
    void theServiceRejectionCodeIsCarriedThrough() throws Exception {
        willThrow(new ApiException(ErrorCode.BALANCE_LIMIT_EXCEEDED))
                .given(earnService).earn(eq("M1"), eq(1_000L), any());

        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BALANCE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("1회 적립 한도를 넘는 금액은 범위를 벗어났다는 코드로 나간다")
    void anAmountBeyondThePerEarnMaximumHasItsOwnCode() throws Exception {
        willThrow(new ApiException(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE))
                .given(earnService).earn(eq("M1"), eq(Long.MAX_VALUE), any());

        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":9223372036854775807}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EARN_AMOUNT_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("long 으로 담을 수 없이 큰 숫자는 읽어 들이는 단계에서 거절한다")
    void aNumberTooLargeForLongIsRejectedWhileReadingTheBody() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":92233720368547758070}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(earnService, never()).earn(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("깨진 JSON 은 잘못된 요청으로 돌려보낸다")
    void brokenJsonIsRejected() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("만료일수 자리에 숫자가 아닌 값이 오면 잘못된 요청이다")
    void aNonNumericExpiryDayIsRejected() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"expireDays\":\"일년\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
