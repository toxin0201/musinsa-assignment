package com.musinsa.point.common;

/** 도메인이 거절 사유를 표현하는 유일한 방법. 어떤 오류 코드로 내보낼지를 예외 자신이 안다. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
