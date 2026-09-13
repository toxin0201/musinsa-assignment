package com.musinsa.point.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.command.EarnCancelResult;
import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelRestoration;
import com.musinsa.point.point.command.UseCancelResult;
import com.musinsa.point.point.command.UseCancelService;
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
class CancelControllerTest {

    private static final String EARN_CANCEL_PATH = "/api/v1/members/M1/points/earn/PK-A/cancel";
    private static final String USE_CANCEL_PATH = "/api/v1/members/M1/points/use/PK-C/cancel";

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
    @DisplayName("적립취소는 본문 없이 요청하고 취소 거래 키와 잔액을 받는다")
    void cancelingAnEarningNeedsNoBody() throws Exception {
        given(earnCancelService.cancel("M1", "PK-A")).willReturn(new EarnCancelResult("PK-CANCEL", 1_000, 500));

        mockMvc.perform(post(EARN_CANCEL_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointKey").value("PK-CANCEL"))
                .andExpect(jsonPath("$.canceledAmount").value(1_000))
                .andExpect(jsonPath("$.balance").value(500));
    }

    @Test
    @DisplayName("이미 쓴 적립을 취소하려 하면 409 로 나간다")
    void cancelingAnAlreadyUsedEarningIsAConflict() throws Exception {
        willThrow(new ApiException(ErrorCode.EARN_ALREADY_USED))
                .given(earnCancelService).cancel("M1", "PK-A");

        mockMvc.perform(post(EARN_CANCEL_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EARN_ALREADY_USED"));
    }

    @Test
    @DisplayName("없는 포인트 키로 적립취소하면 404 로 나간다")
    void cancelingAnUnknownPointKeyIsNotFound() throws Exception {
        willThrow(new ApiException(ErrorCode.POINT_KEY_NOT_FOUND))
                .given(earnCancelService).cancel("M1", "PK-A");

        mockMvc.perform(post(EARN_CANCEL_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT_KEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("사용취소는 되돌린 내역과 남은 취소 가능액을 함께 돌려준다")
    void cancelingAUseReturnsTheRestorationsAndWhatIsLeft() throws Exception {
        given(useCancelService.cancel("M1", "PK-C", 1_100L)).willReturn(new UseCancelResult("PK-D", 1_100,
                List.of(UseCancelRestoration.reissuedAs("PK-A", 1_000, "PK-E"),
                        UseCancelRestoration.restoredInPlace("PK-B", 100)),
                1_400, 100));

        mockMvc.perform(post(USE_CANCEL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointKey").value("PK-D"))
                .andExpect(jsonPath("$.canceledAmount").value(1_100))
                .andExpect(jsonPath("$.restorations[0].earningPointKey").value("PK-A"))
                .andExpect(jsonPath("$.restorations[0].amount").value(1_000))
                .andExpect(jsonPath("$.restorations[0].reissued").value(true))
                .andExpect(jsonPath("$.restorations[0].newEarningPointKey").value("PK-E"))
                .andExpect(jsonPath("$.restorations[1].reissued").value(false))
                .andExpect(jsonPath("$.balance").value(1_400))
                .andExpect(jsonPath("$.remainingCancelableAmount").value(100));
    }

    @Test
    @DisplayName("사용취소 금액이 0 이하이면 서비스를 부르지 않는다")
    void aNonPositiveCancelAmountNeverReachesTheService() throws Exception {
        mockMvc.perform(post(USE_CANCEL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(useCancelService, never()).cancel(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("취소 가능액을 넘겨 요청하면 409 로 나간다")
    void cancelingMoreThanWasSpentIsAConflict() throws Exception {
        willThrow(new ApiException(ErrorCode.CANCEL_AMOUNT_EXCEEDED))
                .given(useCancelService).cancel("M1", "PK-C", 9_999L);

        mockMvc.perform(post(USE_CANCEL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":9999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_AMOUNT_EXCEEDED"));
    }
}
