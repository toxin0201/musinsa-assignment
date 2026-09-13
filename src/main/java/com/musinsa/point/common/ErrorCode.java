package com.musinsa.point.common;

import org.springframework.http.HttpStatus;

/** 외부에 내보내는 오류 코드. 코드와 HTTP 상태의 짝은 API 계약의 일부다. */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    EARN_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "1회 적립 가능 금액 범위를 벗어났습니다."),
    EXPIRY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "만료일수가 허용 범위를 벗어났습니다."),

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원의 포인트 계정을 찾을 수 없습니다."),
    POINT_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 포인트 키의 거래를 찾을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

    BALANCE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "보유 가능한 포인트 한도를 초과했습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "사용 가능한 포인트가 부족합니다."),
    EARN_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 적립은 취소할 수 없습니다."),
    EARN_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 적립입니다."),
    CANCEL_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "취소 가능한 금액을 초과했습니다."),
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "이미 포인트를 사용한 주문입니다."),
    UPDATE_CONFLICT(HttpStatus.CONFLICT, "같은 회원의 다른 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요."),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
