package com.musinsa.point.support;

import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * 거절된 호출을 오류 코드 하나로 좁혀 읽는다. 테스트가 "왜 거절됐는가"만 말하고
 * 예외를 꺼내 오는 절차는 감춘다.
 */
public final class ApiFailures {

    private ApiFailures() {
    }

    public static ErrorCode rejectionCodeOf(ThrowingCallable call) {
        return catchThrowableOfType(ApiException.class, call).getErrorCode();
    }
}
