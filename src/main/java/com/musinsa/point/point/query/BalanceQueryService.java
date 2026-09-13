package com.musinsa.point.point.query;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountLocker;
import com.musinsa.point.point.PointEarningRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 잔액 조회. 지금 쓸 수 있는 적립 건의 남은 금액을 더한 값이며 만료분과 취소분은 빠진다. */
@Service
public class BalanceQueryService {

    private final PointAccountLocker accountLocker;
    private final PointEarningRepository earningRepository;
    private final Clock clock;

    public BalanceQueryService(PointAccountLocker accountLocker, PointEarningRepository earningRepository,
            Clock clock) {
        this.accountLocker = accountLocker;
        this.earningRepository = earningRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BalanceView balance(String memberId) {
        PointAccount account = accountLocker.findExisting(memberId);
        Instant asOf = clock.instant();
        return new BalanceView(earningRepository.sumAvailableBalance(account.getId(), asOf), asOf);
    }
}
