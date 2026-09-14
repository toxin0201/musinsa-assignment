package com.musinsa.point.point.command;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountLocker;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.common.PointKeyGenerator;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionDetail;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.point.TransactionType;
import com.musinsa.point.point.policy.ExpiryPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용취소. 원 사용이 적립 건별로 얼마씩 가져갔는지를 그대로 되짚어, 가져간 순서대로 돌려준다.
 * 돌아갈 적립 건이 이미 만료됐으면 그 금액만큼 새 적립을 만들어 준다.
 * 쓴 것을 돌려주는 일이므로 1회 적립 한도도 보유 한도도 보지 않는다.
 */
@Service
public class UseCancelService {

    private static final int FIRST_SEQ = 1;

    private final PointAccountLocker accountLocker;
    private final PointEarningRepository earningRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointTransactionDetailRepository detailRepository;
    private final ExpiryPolicy expiryPolicy;
    private final PointKeyGenerator pointKeyGenerator;
    private final Clock clock;

    public UseCancelService(PointAccountLocker accountLocker, PointEarningRepository earningRepository,
            PointTransactionRepository transactionRepository, PointTransactionDetailRepository detailRepository,
            ExpiryPolicy expiryPolicy, PointKeyGenerator pointKeyGenerator, Clock clock) {
        this.accountLocker = accountLocker;
        this.earningRepository = earningRepository;
        this.transactionRepository = transactionRepository;
        this.detailRepository = detailRepository;
        this.expiryPolicy = expiryPolicy;
        this.pointKeyGenerator = pointKeyGenerator;
        this.clock = clock;
    }

    @Transactional
    public UseCancelResult cancel(String memberId, String usePointKey, long amount) {
        PointAccount account = accountLocker.lockExisting(memberId);
        PointTransaction useTransaction = findUseTransactionOf(account, usePointKey);

        List<PointTransactionDetail> useDetails =
                detailRepository.findOutgoingDetailsOfTransaction(useTransaction.getId());
        long cancelableAmount = useDetails.stream().mapToLong(this::remainingCancelableAmountOf).sum();
        if (amount < 1 || amount > cancelableAmount) {
            throw new ApiException(ErrorCode.CANCEL_AMOUNT_EXCEEDED, cancelableRangeMessage(cancelableAmount));
        }

        Instant now = clock.instant();
        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.useCancel(
                account, pointKeyGenerator.generate(), amount, useTransaction.getOrderNo(),
                useTransaction.getId(), now));

        List<UseCancelRestoration> restorations = restore(account, cancelTransaction, useDetails, amount, now);

        return new UseCancelResult(cancelTransaction.getPointKey(), amount, restorations,
                earningRepository.sumAvailableBalance(account.getId(), now), cancelableAmount - amount);
    }

    /** 원 사용이 가져간 순서(seq)대로 되돌린다. 예시의 "A 몫을 먼저, 그 다음 B 몫"이 이 순서다. */
    private List<UseCancelRestoration> restore(PointAccount account, PointTransaction cancelTransaction,
            List<PointTransactionDetail> useDetails, long amount, Instant now) {
        List<UseCancelRestoration> restorations = new ArrayList<>();
        long remainingToCancel = amount;
        int seq = FIRST_SEQ;

        for (PointTransactionDetail useDetail : useDetails) {
            if (remainingToCancel == 0) {
                break;
            }
            long portion = Math.min(remainingCancelableAmountOf(useDetail), remainingToCancel);
            if (portion == 0) {
                continue;
            }
            remainingToCancel -= portion;
            restorations.add(restoreOne(account, cancelTransaction, useDetail, portion, now, seq++));
        }
        return restorations;
    }

    private UseCancelRestoration restoreOne(PointAccount account, PointTransaction cancelTransaction,
            PointTransactionDetail useDetail, long portion, Instant now, int seq) {
        PointEarning origin = useDetail.getEarning();
        String originPointKey = origin.getTransaction().getPointKey();

        if (origin.getStatus() == EarningStatus.ACTIVE && !origin.isExpiredAt(now)) {
            origin.restore(portion);
            earningRepository.save(origin);
            detailRepository.save(
                    PointTransactionDetail.in(cancelTransaction, origin, portion, useDetail.getId(), seq));
            return UseCancelRestoration.restoredInPlace(originPointKey, portion);
        }

        PointEarning reissued = reissue(account, cancelTransaction, origin, portion, now);
        detailRepository.save(
                PointTransactionDetail.in(cancelTransaction, reissued, portion, useDetail.getId(), seq));
        return UseCancelRestoration.reissuedAs(
                originPointKey, portion, reissued.getTransaction().getPointKey());
    }

    /**
     * 돌아갈 자리가 없어진 금액을 새 적립으로 지급한다. 관리자 지급분의 성격은 유지해야 하므로 종류를 물려받고,
     * 원래의 만료일은 이미 지났으므로 만료는 지금부터 기본 일수만큼 새로 센다.
     */
    private PointEarning reissue(PointAccount account, PointTransaction cancelTransaction, PointEarning origin,
            long amount, Instant now) {
        PointTransaction reissueTransaction = transactionRepository.save(PointTransaction.reissue(
                account, pointKeyGenerator.generate(), amount, cancelTransaction.getId(), now));
        return earningRepository.save(PointEarning.reissued(account, reissueTransaction, origin.getKind(),
                amount, now, expiryPolicy.resolveExpiresAt(now, null), cancelTransaction.getId()));
    }

    /** 돌려줄 것이 하나도 남지 않았는데 "1 이상 0 이하" 라고 적으면 읽는 쪽이 무엇을 고쳐야 할지 알 수 없다. */
    private static String cancelableRangeMessage(long cancelableAmount) {
        return cancelableAmount == 0
                ? "취소할 수 있는 금액이 남아 있지 않습니다."
                : "취소할 수 있는 금액은 1 이상 %d 이하입니다.".formatted(cancelableAmount);
    }

    /** 이 사용 줄에서 아직 돌려주지 않은 금액. 되돌아간 금액은 IN 상세가 이 줄을 가리키며 쌓인다. */
    private long remainingCancelableAmountOf(PointTransactionDetail useDetail) {
        return useDetail.getAmount() - detailRepository.sumRestoredAmountOf(useDetail.getId());
    }

    /** 다른 타입의 거래나 다른 회원의 거래는 "그런 사용 키가 없다"와 같게 다룬다. */
    private PointTransaction findUseTransactionOf(PointAccount account, String pointKey) {
        return transactionRepository.findByPointKey(pointKey)
                .filter(transaction -> transaction.getType() == TransactionType.USE)
                .filter(transaction -> transaction.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.POINT_KEY_NOT_FOUND));
    }
}
