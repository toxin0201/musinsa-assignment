package com.musinsa.point.common;

import java.time.Instant;

/** 모든 실패 응답의 유일한 모양. 프레임워크가 낸 오류도 이 본문으로 나간다. */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public static ErrorResponse of(ErrorCode errorCode, String message, Instant timestamp) {
        return new ErrorResponse(errorCode.name(), message, timestamp);
    }
}
