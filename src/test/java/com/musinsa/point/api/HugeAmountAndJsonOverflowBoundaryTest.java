package com.musinsa.point.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 큰 수가 들어왔을 때 덧셈이 넘치거나 500 이 나가지 않고 정해진 코드로 거절되는지 본다. */
@AutoConfigureMockMvc
class HugeAmountAndJsonOverflowBoundaryTest extends AbstractPointIntegrationTest {

    private static final String EARN_PATH = "/api/v1/members/M61/points/earn";
    private static final String USE_PATH = "/api/v1/members/M61/points/use";

    @Autowired
    private MockMvc mockMvc;

    private void earn(String body) throws Exception {
        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("1회 적립 상한과 똑같은 금액은 받아 주고 1원 넘기면 범위를 벗어났다고 답한다")
    void thePerEarnCeilingIsInclusive() throws Exception {
        earn("{\"amount\":100000}");

        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EARN_AMOUNT_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("long 의 최댓값을 적립하려 해도 덧셈이 넘치기 전에 범위에서 걸린다")
    void theLargestPossibleLongIsCaughtByTheRangeCheck() throws Exception {
        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":9223372036854775807}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EARN_AMOUNT_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("long 에 담을 수 없는 숫자는 본문을 읽는 단계에서 거절한다")
    void aNumberBeyondLongIsRejectedWhileReadingTheBody() throws Exception {
        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99999999999999999999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("만료일수가 int 범위를 넘으면 본문을 읽는 단계에서 거절한다")
    void anExpiryDayBeyondIntIsRejectedWhileReadingTheBody() throws Exception {
        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"expireDays\":99999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("만료일수가 int 안에 있어도 5년을 넘기면 만료 범위에서 걸린다")
    void anExpiryDayInsideIntButBeyondFiveYearsIsRejected() throws Exception {
        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"expireDays\":2147483647}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPIRY_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("만료일수 0은 만료 범위를 벗어난 값이다")
    void zeroExpiryDaysIsOutOfRange() throws Exception {
        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"expireDays\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPIRY_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("가진 것보다 터무니없이 많이 쓰려 해도 잔액 부족으로 답한다")
    void spendingAnAbsurdAmountIsJustAnInsufficientBalance() throws Exception {
        earn("{\"amount\":1000}");

        mockMvc.perform(post(USE_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"O-BIG\",\"amount\":9223372036854775807}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("기본 보유 한도를 넘기는 적립은 한도 초과로 답한다")
    void earningPastTheDefaultBalanceCeilingIsRejected() throws Exception {
        for (int i = 0; i < 10; i++) {
            earn("{\"amount\":100000}");
        }

        mockMvc.perform(post(EARN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BALANCE_LIMIT_EXCEEDED"));
    }
}
