package com.musinsa.point.api;

import com.musinsa.point.point.query.EarningUsageView;
import java.util.List;

public record EarningUsageResponse(EarningSummaryResponse earning, List<OrderUsageResponse> usages) {

    public static EarningUsageResponse from(EarningUsageView view) {
        return new EarningUsageResponse(EarningSummaryResponse.from(view.earning()),
                view.usages().stream().map(OrderUsageResponse::from).toList());
    }
}
