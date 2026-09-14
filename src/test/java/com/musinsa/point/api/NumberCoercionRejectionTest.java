package com.musinsa.point.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseResult;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.support.TestDoubleConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 금액과 일수는 정수다. 소수나 따옴표에 싼 숫자를 말없이 정수로 바꿔 받으면
 * 보낸 쪽이 적은 값과 다른 금액이 처리되므로, 본문을 읽어 들이는 자리에서 거절한다.
 */
@WebMvcTest(PointController.class)
@Import(TestDoubleConfig.class)
class NumberCoercionRejectionTest {

    private static final String EARN_PATH = "/api/v1/members/M1/points/earn";
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
    @DisplayName("소수점이 붙은 적립 금액은 잘라 담지 않고 거절한다")
    void aFractionalEarnAmountIsRejectedInsteadOfTruncated() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(earnService, never()).earn(any(), anyLong(), any());
    }

    @Test
    @DisplayName("소수점 아래가 0이어도 정수 자리에 소수를 받지 않는다")
    void anAmountWrittenAsAWholeFloatIsStillRejected() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(earnService, never()).earn(any(), anyLong(), any());
    }

    @Test
    @DisplayName("따옴표에 싼 숫자는 숫자로 바꿔 받지 않는다")
    void aQuotedNumberIsNotCoercedIntoANumber() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"100\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(earnService, never()).earn(any(), anyLong(), any());
    }

    @Test
    @DisplayName("소수점이 붙은 만료일수도 하루로 깎아 받지 않는다")
    void aFractionalExpiryDayIsRejectedInsteadOfFloored() throws Exception {
        mockMvc.perform(post(EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"expireDays\":1.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(earnService, never()).earn(any(), anyLong(), any());
    }

    @Test
    @DisplayName("사용 금액도 같은 기준으로 읽는다")
    void theUseAmountFollowsTheSameRule() throws Exception {
        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":\"1200\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":1200.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(useService, never()).use(any(), any(), anyLong());
    }

    @Test
    @DisplayName("정수로 적은 금액은 그대로 서비스까지 간다")
    void anIntegerAmountStillReachesTheService() throws Exception {
        BDDMockito.given(useService.use("M1", "A1234", 1_200L))
                .willReturn(new UseResult("PK-USE", 1_200, List.of(), 0));

        mockMvc.perform(post(USE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"A1234\",\"amount\":1200}"))
                .andExpect(status().isOk());

        verify(useService).use("M1", "A1234", 1_200L);
    }
}
