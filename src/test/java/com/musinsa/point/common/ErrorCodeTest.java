package com.musinsa.point.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 오류 코드와 HTTP 상태의 짝은 외부와의 약속이라 코드를 늘리거나 상태를 바꾸면 여기서 먼저 걸린다. */
class ErrorCodeTest {

    private static final Map<ErrorCode, HttpStatus> AGREED_STATUSES = Map.ofEntries(
            Map.entry(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.EXPIRY_OUT_OF_RANGE, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.MEMBER_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.POINT_KEY_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED),
            Map.entry(ErrorCode.BALANCE_LIMIT_EXCEEDED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.EARN_ALREADY_USED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.EARN_ALREADY_CANCELED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.CANCEL_AMOUNT_EXCEEDED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.DUPLICATE_ORDER, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.UPDATE_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
            Map.entry(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

    @Test
    @DisplayName("합의한 오류 코드 16개가 전부 있고 그 이상은 없다")
    void errorCodesMatchTheAgreedSet() {
        assertThat(ErrorCode.values()).containsExactlyInAnyOrderElementsOf(AGREED_STATUSES.keySet());
    }

    @Test
    @DisplayName("모든 오류 코드가 합의한 HTTP 상태를 갖는다")
    void everyErrorCodeKeepsItsAgreedHttpStatus() {
        AGREED_STATUSES.forEach((code, status) -> assertThat(code.getStatus()).isEqualTo(status));
    }

    @Test
    @DisplayName("모든 오류 코드에 사람이 읽을 기본 메시지가 있다")
    void everyErrorCodeCarriesADefaultMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getDefaultMessage()).isNotBlank();
        }
    }

    @Test
    @DisplayName("예외는 자신이 어떤 오류인지와 무엇이 문제였는지를 함께 들고 다닌다")
    void exceptionCarriesCodeAndMessage() {
        ApiException plain = new ApiException(ErrorCode.MEMBER_NOT_FOUND);
        ApiException detailed = new ApiException(ErrorCode.EARN_AMOUNT_OUT_OF_RANGE, "적립 금액은 1 이상 100000 이하여야 합니다.");

        assertThat(plain.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(plain.getMessage()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getDefaultMessage());
        assertThat(detailed.getMessage()).isEqualTo("적립 금액은 1 이상 100000 이하여야 합니다.");
    }
}
