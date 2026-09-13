package com.musinsa.point.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.musinsa.point.account.AccountLimitResult;

/** maxBalance 가 비어 나가면 설정 기본값을 따른다는 뜻이다. */
public record AccountLimitResponse(String memberId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long maxBalance) {

    public static AccountLimitResponse from(AccountLimitResult result) {
        return new AccountLimitResponse(result.memberId(), result.maxBalance());
    }
}
