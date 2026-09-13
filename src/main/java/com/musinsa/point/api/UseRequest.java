package com.musinsa.point.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UseRequest(
        @NotBlank(message = "주문번호는 필수입니다.") @Size(max = 64, message = "주문번호는 64자를 넘을 수 없습니다.") String orderNo,
        @Min(value = 1, message = "사용 금액은 1 이상이어야 합니다.") long amount) {
}
