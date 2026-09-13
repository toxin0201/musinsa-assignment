package com.musinsa.point.api;

import com.musinsa.point.point.query.BalanceView;
import java.time.Instant;

public record BalanceResponse(long balance, Instant asOf) {

    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(view.balance(), view.asOf());
    }
}
