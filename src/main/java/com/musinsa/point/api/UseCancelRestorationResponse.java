package com.musinsa.point.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.musinsa.point.point.command.UseCancelRestoration;

/** 제자리 복원이면 새 적립 키가 없다. 그 경우에는 필드 자체를 내보내지 않는다. */
public record UseCancelRestorationResponse(String earningPointKey, long amount, boolean reissued,
        @JsonInclude(JsonInclude.Include.NON_NULL) String newEarningPointKey) {

    public static UseCancelRestorationResponse from(UseCancelRestoration restoration) {
        return new UseCancelRestorationResponse(restoration.earningPointKey(), restoration.amount(),
                restoration.reissued(), restoration.newEarningPointKey());
    }
}
