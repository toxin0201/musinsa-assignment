package com.musinsa.point.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.support.AbstractPointIntegrationTest;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 저장 길이를 넘는 식별자가 DB 제약까지 내려가면 엉뚱한 오류로 읽힌다. 들어오는 자리에서 걸러야 한다. */
@AutoConfigureMockMvc
class BlankAndOversizedIdentifierBoundaryTest extends AbstractPointIntegrationTest {

    private static final int MEMBER_ID_MAX = 64;
    private static final int ORDER_NO_MAX = 64;

    @Autowired
    private MockMvc mockMvc;

    private String earnPathFor(String memberId) {
        return "/api/v1/members/" + memberId + "/points/earn";
    }

    @Test
    @DisplayName("저장 한계와 똑같은 길이의 회원 식별자는 받아 준다")
    void aMemberIdExactlyAtTheStorageLimitIsAccepted() throws Exception {
        mockMvc.perform(post(earnPathFor("M".repeat(MEMBER_ID_MAX)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1_000));
    }

    @Test
    @DisplayName("저장 한계를 한 글자 넘긴 회원 식별자는 400 으로 거절한다")
    void aMemberIdOneCharacterTooLongIsRejected() throws Exception {
        mockMvc.perform(post(earnPathFor("M".repeat(MEMBER_ID_MAX + 1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("회원 식별자 자리가 비어 있으면 그런 경로가 없다고 답한다")
    void anEmptyMemberIdSegmentIsNotAPath() throws Exception {
        mockMvc.perform(get("/api/v1/members//points/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("공백뿐인 회원 식별자는 계정을 만들지 않고 400 으로 거절한다")
    void aWhitespaceOnlyMemberIdIsRejected() throws Exception {
        mockMvc.perform(post(URI.create("/api/v1/members/%20/points/earn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("저장 한계와 똑같은 길이의 주문번호는 받아 준다")
    void anOrderNumberExactlyAtTheStorageLimitIsAccepted() throws Exception {
        mockMvc.perform(post(earnPathFor("M60"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/members/M60/points/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + "O".repeat(ORDER_NO_MAX) + "\",\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(900));
    }

    @Test
    @DisplayName("저장 한계를 넘긴 주문번호는 400 으로 거절한다")
    void anOrderNumberTooLongIsRejected() throws Exception {
        mockMvc.perform(post(earnPathFor("M60"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/members/M60/points/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + "O".repeat(ORDER_NO_MAX + 1) + "\",\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("발급될 수 없는 길이의 포인트 키로 취소하면 400 으로 거절한다")
    void aPointKeyLongerThanAnyIssuedKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/members/M60/points/earn/" + "K".repeat(33) + "/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("지급 사유가 저장 한계를 넘으면 400 으로 거절한다")
    void aReasonTooLongForStorageIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/members/M60/points/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"adminId\":\"admin1\",\"reason\":\"" + "사".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
