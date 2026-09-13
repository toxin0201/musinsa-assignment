package com.musinsa.point.point.command;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountLocker;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.common.PointKeyGenerator;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionDetail;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문에서 포인트를 쓴다. 수기 지급분을 먼저, 그 다음 만료가 가까운 순으로 소진한다.
 * 모자라면 한 푼도 쓰지 않고 전체를 거절한다 — "일부만 빠져나간" 중간 상태를 남기지 않기 위해서다.
 */
@Service
public class UseService {

    private static final int FIRST_SEQ = 1;

    private final PointAccountLocker accountLocker;
    private final PointEarningRepository earningRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointTransactionDetailRepository detailRepository;
    private final PointKeyGenerator pointKeyGenerator;
    private final Clock clock;

    public UseService(PointAccountLocker accountLocker, PointEarningRepository earningRepository,
            PointTransactionRepository transactionRepository, PointTransactionDetailRepository detailRepository,
            PointKeyGenerator pointKeyGenerator, Clock clock) {
        this.accountLocker = accountLocker;
        this.earningRepository = earningRepository;
        this.transactionRepository = transactionRepository;
        this.detailRepository = detailRepository;
        this.pointKeyGenerator = pointKeyGenerator;
        this.clock = clock;
    }

    @Transactional
    public UseResult use(String memberId, String orderNo, long amount) {
        // 형식이 틀린 요청은 계정 행을 잠그기 전에 돌려보낸다. 성공할 수 없는 요청이 같은 회원의 다른 요청을 기다리게 할 이유가 없다.
        requireUsableAmount(amount);
        requireOrderNo(orderNo);

        PointAccount account = accountLocker.lockExisting(memberId);
        if (transactionRepository.existsUseByAccountIdAndOrderNo(account.getId(), orderNo)) {
            throw new ApiException(ErrorCode.DUPLICATE_ORDER);
        }

        Instant now = clock.instant();
        List<PointEarning> usable = earningRepository.findUsableOrdered(account.getId(), now);
        // 차감하기 전에 세어 둔다. 아래에서 같은 엔티티들의 잔액이 바뀌기 때문이다.
        long balanceBefore = usable.stream().mapToLong(PointEarning::getRemainingAmount).sum();
        List<Deduction> plan = planDeductions(usable, amount);

        PointTransaction useTransaction = openUseTransaction(account, orderNo, amount, now);
        applyDeductions(useTransaction, plan);

        return new UseResult(useTransaction.getPointKey(), amount, toAllocations(plan), balanceBefore - amount);
    }

    /** 쓸 수 있는 적립 건을 순서대로 훑어 배분안을 짠다. 총액이 모자라면 여기서 끝나고 아무것도 바뀌지 않는다. */
    private List<Deduction> planDeductions(List<PointEarning> usable, long amount) {
        List<Deduction> plan = new ArrayList<>();
        long remainingToSpend = amount;
        for (PointEarning earning : usable) {
            if (remainingToSpend == 0) {
                break;
            }
            long taken = Math.min(earning.getRemainingAmount(), remainingToSpend);
            plan.add(new Deduction(earning, taken));
            remainingToSpend -= taken;
        }
        if (remainingToSpend > 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE,
                    "사용 가능한 포인트가 %d 원 모자랍니다.".formatted(remainingToSpend));
        }
        return plan;
    }

    /**
     * 사전 조회를 통과했더라도 같은 주문이 동시에 들어오면 여기서 유일 제약에 걸린다.
     * 즉시 밀어 넣어 그 충돌을 이 자리에서 주문 중복으로 바꾼다.
     */
    private PointTransaction openUseTransaction(PointAccount account, String orderNo, long amount, Instant now) {
        try {
            return transactionRepository.saveAndFlush(
                    PointTransaction.use(account, pointKeyGenerator.generate(), amount, orderNo, now));
        } catch (DataIntegrityViolationException orderAlreadySpent) {
            throw new ApiException(ErrorCode.DUPLICATE_ORDER);
        }
    }

    private void applyDeductions(PointTransaction useTransaction, List<Deduction> plan) {
        int seq = FIRST_SEQ;
        for (Deduction deduction : plan) {
            deduction.earning().deduct(deduction.amount());
            earningRepository.save(deduction.earning());
            detailRepository.save(PointTransactionDetail.out(
                    useTransaction, deduction.earning(), deduction.amount(), seq++));
        }
    }

    private static List<UseAllocation> toAllocations(List<Deduction> plan) {
        return plan.stream()
                .map(deduction -> new UseAllocation(
                        deduction.earning().getTransaction().getPointKey(), deduction.amount()))
                .toList();
    }

    private static void requireUsableAmount(long amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사용 금액은 1 이상이어야 합니다.");
        }
    }

    private static void requireOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "주문번호는 필수입니다.");
        }
    }

    /** 적립 건 하나에서 얼마를 뺄지 정해 둔 한 줄. 계획을 다 세운 뒤에야 실제로 적용한다. */
    private record Deduction(PointEarning earning, long amount) {
    }
}
