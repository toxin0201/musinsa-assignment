package com.musinsa.point.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseAllocation;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseResult;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.support.TestDoubleConfig;
import java.util.List;
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
class UseControllerTest {

    private static final String USE_PATH = "/api/v1/members/M1/points/use";

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
    @DisplayName("사용에 성공하면 어떤 적립에서 얼마씩 빠졌는지까지 돌려준다")
    void usingPointsReturnsThePerEarningBreakdown() throws Exception {
        given(useService.use("M1", "A1234", 1_200L)).willReturn(new UseResult("PK-USE", 1_200,
                List.of(new UseAllocation("PK-A", 1_000), new UseAllocation("PK-B", 200)), 300));

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":1200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointKey").value("PK-USE"))
                .andExpect(jsonPath("$.amount").value(1_200))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].earningPointKey").value("PK-A"))
                .andExpect(jsonPath("$.allocations[0].amount").value(1_000))
                .andExpect(jsonPath("$.allocations[1].earningPointKey").value("PK-B"))
                .andExpect(jsonPath("$.allocations[1].amount").value(200))
                .andExpect(jsonPath("$.balance").value(300));
    }

    @Test
    @DisplayName("주문번호가 비어 있으면 서비스를 부르지 않는다")
    void aBlankOrderNumberNeverReachesTheService() throws Exception {
        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"   \",\"amount\":1200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(useService, never()).use(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("주문번호 자리가 아예 없으면 잘못된 요청이다")
    void aMissingOrderNumberIsRejected() throws Exception {
        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("사용 금액이 0 이하이면 잘못된 요청이다")
    void aNonPositiveAmountIsRejected() throws Exception {
        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("64자를 넘는 주문번호는 저장하기 전에 거절한다")
    void anOverlongOrderNumberIsRejectedUpFront() throws Exception {
        String tooLong = "O".repeat(65);

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + tooLong + "\",\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(useService, never()).use(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("잔액이 모자라면 409 로 나간다")
    void anInsufficientBalanceComesBackAsAConflict() throws Exception {
        willThrow(new ApiException(ErrorCode.INSUFFICIENT_BALANCE))
                .given(useService).use(eq("M1"), eq("A1234"), anyLong());

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":1200}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("이미 포인트를 쓴 주문이면 409 로 나간다")
    void aRepeatedOrderComesBackAsAConflict() throws Exception {
        willThrow(new ApiException(ErrorCode.DUPLICATE_ORDER))
                .given(useService).use(eq("M1"), eq("A1234"), anyLong());

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":1200}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ORDER"));
    }

    @Test
    @DisplayName("계정이 없는 회원의 사용은 404 로 나간다")
    void usingPointsOfAnUnknownMemberIsNotFound() throws Exception {
        willThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND))
                .given(useService).use(any(), any(), anyLong());

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":1200}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }
}
