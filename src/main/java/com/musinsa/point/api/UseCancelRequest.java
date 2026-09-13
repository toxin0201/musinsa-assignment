package com.musinsa.point.api;

import jakarta.validation.constraints.Min;

public record UseCancelRequest(@Min(value = 1, message = "취소 금액은 1 이상이어야 합니다.") long amount) {
}
