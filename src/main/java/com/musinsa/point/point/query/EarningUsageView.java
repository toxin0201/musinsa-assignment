package com.musinsa.point.point.query;

import java.util.List;

public record EarningUsageView(EarningSummary earning, List<OrderUsage> usages) {
}
