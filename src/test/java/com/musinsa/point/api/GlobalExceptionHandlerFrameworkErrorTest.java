package com.musinsa.point.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseService;
import com.musinsa.point.support.TestDoubleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 프레임워크가 던지는 오류도 도메인 오류와 같은 본문으로 나가야 한다.
 * 스프링은 선언 순서가 아니라 예외 타입이 얼마나 가까운지로 처리기를 고르므로 각각에 전용 처리기가 필요하다.
 */
@WebMvcTest(PointController.class)
@Import(TestDoubleConfig.class)
class GlobalExceptionHandlerFrameworkErrorTest {

    private static final String USE_PATH = "/api/v1/members/M1/points/use";
    private static final String USE_BODY = "{\"orderNo\":\"A1234\",\"amount\":1200}";

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
    @DisplayName("없는 경로도 같은 오류 본문으로 404 를 돌려준다")
    void anUnknownPathAnswersWithTheSameErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/there-is-no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("지원하지 않는 요청 방식은 405 로 돌려준다")
    void anUnsupportedMethodAnswersWithMethodNotAllowed() throws Exception {
        mockMvc.perform(delete(USE_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("JSON 이 아닌 본문은 415 로 돌려준다")
    void anUnsupportedContentTypeAnswersWithUnsupportedMediaType() throws Exception {
        mockMvc.perform(post(USE_PATH).contentType(MediaType.TEXT_PLAIN).content("amount=1200"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("같은 회원의 다른 요청을 기다리다 잠금 시간이 지나면 409 로 돌려준다")
    void aLockWaitTimeoutAnswersWithUpdateConflict() throws Exception {
        willThrow(new PessimisticLockingFailureException("잠금 대기 시간 초과"))
                .given(useService).use(any(), any(), anyLong());

        mockMvc.perform(post(USE_PATH).contentType(MediaType.APPLICATION_JSON).content(USE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPDATE_CONFLICT"));
    }

    @Test
    @DisplayName("리포지토리를 거치지 않고 올라온 잠금 시간 초과도 같은 409 로 돌려준다")
    void aRawLockTimeoutAnswersWithTheSameConflict() throws Exception {
        willThrow(new jakarta.persistence.LockTimeoutException("Timeout trying to lock table"))
                .given(useService).use(any(), any(), anyLong());

        mockMvc.perform(post(USE_PATH).contentType(MediaType.APPLICATION_JSON).content(USE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPDATE_CONFLICT"));
    }

    @Test
    @DisplayName("주문번호 유일 제약에 걸리면 409 주문 중복으로 돌려준다")
    void aUniqueConstraintViolationAnswersWithDuplicateOrder() throws Exception {
        willThrow(new DataIntegrityViolationException("uk_point_transaction_use_order"))
                .given(useService).use(any(), any(), anyLong());

        mockMvc.perform(post(USE_PATH).contentType(MediaType.APPLICATION_JSON).content(USE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ORDER"));
    }

    @Test
    @DisplayName("예상하지 못한 오류는 500 으로 돌려주되 내부 사정은 밖으로 내보내지 않는다")
    void anUnexpectedFailureNeverLeaksItsInternals() throws Exception {
        willThrow(new IllegalStateException("jdbc:h2:mem:point 비밀번호가 틀렸습니다"))
                .given(useService).use(any(), any(), anyLong());

        mockMvc.perform(post(USE_PATH).contentType(MediaType.APPLICATION_JSON).content(USE_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jdbc:h2"))));
    }
}
