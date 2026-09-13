package com.musinsa.point.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 수기 지급은 누가 왜 줬는지가 기록의 일부라 둘 다 필수다. */
public record AdminEarnRequest(
        @Min(value = 1, message = "적립 금액은 1 이상이어야 합니다.") long amount,
        Integer expireDays,
        @NotBlank(message = "관리자 식별자는 필수입니다.") @Size(max = 64) String adminId,
        @NotBlank(message = "지급 사유는 필수입니다.") @Size(max = 200) String reason) {
}
