package com.musinsa.point.api;

import static com.musinsa.point.api.PointController.MEMBER_ID_MAX_LENGTH;
import static com.musinsa.point.api.PointController.POINT_KEY_MAX_LENGTH;

import com.musinsa.point.point.query.BalanceQueryService;
import com.musinsa.point.point.query.EarningUsageQueryService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 조회 전용. 잔액과 "이 적립이 어느 주문에서 얼마나 쓰였는가"를 내보낸다. */
@RestController
@RequestMapping("/api/v1/members/{memberId}/points")
@Validated
public class PointQueryController {

    private final BalanceQueryService balanceQueryService;
    private final EarningUsageQueryService earningUsageQueryService;

    public PointQueryController(BalanceQueryService balanceQueryService,
            EarningUsageQueryService earningUsageQueryService) {
        this.balanceQueryService = balanceQueryService;
        this.earningUsageQueryService = earningUsageQueryService;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId) {
        return BalanceResponse.from(balanceQueryService.balance(memberId));
    }

    @GetMapping("/earn/{pointKey}/usages")
    public EarningUsageResponse usages(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId,
            @PathVariable @Size(max = POINT_KEY_MAX_LENGTH) String pointKey) {
        return EarningUsageResponse.from(earningUsageQueryService.usages(memberId, pointKey));
    }
}
