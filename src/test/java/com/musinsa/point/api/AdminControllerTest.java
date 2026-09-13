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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.account.AccountLimitResult;
import com.musinsa.point.account.AccountLimitService;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.command.EarnResult;
import com.musinsa.point.point.command.EarnService;
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

@WebMvcTest(AdminPointController.class)
@Import(TestDoubleConfig.class)
class AdminControllerTest {

    private static final String ADMIN_EARN_PATH = "/api/v1/admin/members/M1/points/earn";
    private static final String ADMIN_LIMIT_PATH = "/api/v1/admin/members/M1/points/limit";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EarnService earnService;

    @MockitoBean
    private AccountLimitService accountLimitService;

    @Test
    @DisplayName("관리자 수기 지급은 수기 지급으로 표시되어 돌아온다")
    void aManualGrantIsMarkedAsManual() throws Exception {
        given(earnService.earnByAdmin("M1", 1_000L, 365, "admin1", "보상"))
                .willReturn(new EarnResult("PK1", 1_000, EarningKind.MANUAL,
                        Instant.parse("2027-01-01T00:00:00Z"), 1_000));

        mockMvc.perform(post(ADMIN_EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"expireDays\":365,\"adminId\":\"admin1\",\"reason\":\"보상\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointKey").value("PK1"))
                .andExpect(jsonPath("$.kind").value("MANUAL"));
    }

    @Test
    @DisplayName("관리자 식별자나 사유가 없으면 수기 지급을 받지 않는다")
    void aManualGrantWithoutAnAdminOrReasonIsRejected() throws Exception {
        mockMvc.perform(post(ADMIN_EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"reason\":\"보상\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post(ADMIN_EARN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"adminId\":\"admin1\",\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(earnService, never()).earnByAdmin(anyString(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("개인 보유 한도를 바꾸면 바뀐 값을 돌려준다")
    void changingThePersonalLimitEchoesTheNewValue() throws Exception {
        given(accountLimitService.setMaxBalance("M1", 500_000L))
                .willReturn(new AccountLimitResult("M1", 500_000L));

        mockMvc.perform(put(ADMIN_LIMIT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxBalance\":500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value("M1"))
                .andExpect(jsonPath("$.maxBalance").value(500_000));
    }

    @Test
    @DisplayName("한도를 null 로 보내면 설정 기본값으로 되돌린다는 뜻으로 전달한다")
    void sendingANullLimitMeansFallBackToTheDefault() throws Exception {
        given(accountLimitService.setMaxBalance("M1", null)).willReturn(new AccountLimitResult("M1", null));

        mockMvc.perform(put(ADMIN_LIMIT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxBalance\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBalance").doesNotExist());

        verify(accountLimitService).setMaxBalance("M1", null);
    }

    @Test
    @DisplayName("한도 값이 0 이하이면 서비스가 정한 대로 잘못된 요청이 된다")
    void aNonPositiveLimitIsRejected() throws Exception {
        willThrow(new ApiException(ErrorCode.INVALID_REQUEST))
                .given(accountLimitService).setMaxBalance(eq("M1"), eq(0L));

        mockMvc.perform(put(ADMIN_LIMIT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxBalance\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
