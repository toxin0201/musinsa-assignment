package com.musinsa.point.point.command;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.common.PointKeyGenerator;
import com.musinsa.point.point.EarningKind;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.point.policy.BalanceLimitPolicy;
import com.musinsa.point.point.policy.EarnAmountPolicy;
import com.musinsa.point.point.policy.ExpiryPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포인트 적립. 일반 적립과 관리자 수기 지급은 기록되는 종류만 다르고 검사 규칙은 같다. */
@Service
public class EarnService {

    private final PointAccountLocker accountLocker;
    private final PointEarningRepository earningRepository;
    private final PointTransactionRepository transactionRepository;
    private final EarnAmountPolicy earnAmountPolicy;
    private final ExpiryPolicy expiryPolicy;
    private final BalanceLimitPolicy balanceLimitPolicy;
    private final PointKeyGenerator pointKeyGenerator;
    private final Clock clock;

    public EarnService(PointAccountLocker accountLocker, PointEarningRepository earningRepository,
            PointTransactionRepository transactionRepository, EarnAmountPolicy earnAmountPolicy,
            ExpiryPolicy expiryPolicy, BalanceLimitPolicy balanceLimitPolicy,
            PointKeyGenerator pointKeyGenerator, Clock clock) {
        this.accountLocker = accountLocker;
        this.earningRepository = earningRepository;
        this.transactionRepository = transactionRepository;
        this.earnAmountPolicy = earnAmountPolicy;
        this.expiryPolicy = expiryPolicy;
        this.balanceLimitPolicy = balanceLimitPolicy;
        this.pointKeyGenerator = pointKeyGenerator;
        this.clock = clock;
    }

    @Transactional
    public EarnResult earn(String memberId, long amount, Integer expireDays) {
        return doEarn(memberId, amount, expireDays, EarningKind.GENERAL, null, null);
    }

    @Transactional
    public EarnResult earnByAdmin(String memberId, long amount, Integer expireDays, String adminId, String reason) {
        requireText(adminId, "관리자 식별자");
        requireText(reason, "지급 사유");
        return doEarn(memberId, amount, expireDays, EarningKind.MANUAL, adminId, reason);
    }

    private EarnResult doEarn(String memberId, long amount, Integer expireDays, EarningKind kind,
            String adminId, String reason) {
        // 금액 범위를 먼저 본다. 이 검사를 통과한 값만 잔액과 더하므로 덧셈이 넘칠 일이 없다.
        earnAmountPolicy.validate(amount);

        PointAccount account = accountLocker.lockOrOpen(memberId);
        Instant now = clock.instant();
        Instant expiresAt = expiryPolicy.resolveExpiresAt(now, expireDays);

        long availableBalance = earningRepository.sumAvailableBalance(account.getId(), now);
        balanceLimitPolicy.validate(availableBalance, amount, account.getMaxBalance());

        PointTransaction transaction = transactionRepository
                .save(PointTransaction.earn(account, pointKeyGenerator.generate(), amount, now));
        PointEarning earning = kind == EarningKind.MANUAL
                ? PointEarning.manual(account, transaction, amount, now, expiresAt, adminId, reason)
                : PointEarning.general(account, transaction, amount, now, expiresAt);
        earningRepository.save(earning);

        return new EarnResult(transaction.getPointKey(), amount, kind, expiresAt, availableBalance + amount);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "%s 는 필수입니다.".formatted(label));
        }
    }
}
