package com.musinsa.point.api;

import static com.musinsa.point.api.PointController.MEMBER_ID_MAX_LENGTH;

import com.musinsa.point.account.AccountLimitService;
import com.musinsa.point.point.command.EarnService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 기능. 인증·인가는 과제 범위 밖이라 붙이지 않았고 경로로만 갈라 두었다.
 */
@RestController
@RequestMapping("/api/v1/admin/members/{memberId}/points")
@Validated
public class AdminPointController {

    private final EarnService earnService;
    private final AccountLimitService accountLimitService;

    public AdminPointController(EarnService earnService, AccountLimitService accountLimitService) {
        this.earnService = earnService;
        this.accountLimitService = accountLimitService;
    }

    @PostMapping("/earn")
    public EarnResponse earnByAdmin(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId,
            @Valid @RequestBody AdminEarnRequest request) {
        return EarnResponse.from(earnService.earnByAdmin(memberId, request.amount(), request.expireDays(),
                request.adminId(), request.reason()));
    }

    @PutMapping("/limit")
    public AccountLimitResponse changeLimit(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId,
            @Valid @RequestBody AccountLimitRequest request) {
        return AccountLimitResponse.from(accountLimitService.setMaxBalance(memberId, request.maxBalance()));
    }
}
