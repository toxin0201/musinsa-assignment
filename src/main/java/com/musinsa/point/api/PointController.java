package com.musinsa.point.api;

import com.musinsa.point.point.command.EarnCancelService;
import com.musinsa.point.point.command.EarnService;
import com.musinsa.point.point.command.UseCancelService;
import com.musinsa.point.point.command.UseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원이 직접 일으키는 포인트 변경 네 가지: 적립 · 적립취소 · 사용 · 사용취소. */
@RestController
@RequestMapping("/api/v1/members/{memberId}/points")
@Validated
public class PointController {

    /** 저장 길이를 넘는 식별자를 DB 까지 흘려보내면 제약 위반이 "주문 중복"처럼 잘못 읽힌다. 들어오는 자리에서 막는다. */
    static final int MEMBER_ID_MAX_LENGTH = 64;
    static final int POINT_KEY_MAX_LENGTH = 32;

    private final EarnService earnService;
    private final EarnCancelService earnCancelService;
    private final UseService useService;
    private final UseCancelService useCancelService;

    public PointController(EarnService earnService, EarnCancelService earnCancelService, UseService useService,
            UseCancelService useCancelService) {
        this.earnService = earnService;
        this.earnCancelService = earnCancelService;
        this.useService = useService;
        this.useCancelService = useCancelService;
    }

    @PostMapping("/earn")
    public EarnResponse earn(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId, @Valid @RequestBody EarnRequest request) {
        return EarnResponse.from(earnService.earn(memberId, request.amount(), request.expireDays()));
    }

    @PostMapping("/earn/{pointKey}/cancel")
    public EarnCancelResponse cancelEarn(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId,
            @PathVariable @Size(max = POINT_KEY_MAX_LENGTH) String pointKey) {
        return EarnCancelResponse.from(earnCancelService.cancel(memberId, pointKey));
    }

    @PostMapping("/use")
    public UseResponse use(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId, @Valid @RequestBody UseRequest request) {
        return UseResponse.from(useService.use(memberId, request.orderNo(), request.amount()));
    }

    @PostMapping("/use/{pointKey}/cancel")
    public UseCancelResponse cancelUse(@PathVariable @Size(max = MEMBER_ID_MAX_LENGTH) String memberId,
            @PathVariable @Size(max = POINT_KEY_MAX_LENGTH) String pointKey,
            @Valid @RequestBody UseCancelRequest request) {
        return UseCancelResponse.from(useCancelService.cancel(memberId, pointKey, request.amount()));
    }
}
