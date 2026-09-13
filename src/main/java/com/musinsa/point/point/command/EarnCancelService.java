package com.musinsa.point.point.command;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountLocker;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.common.PointKeyGenerator;
import com.musinsa.point.point.DetailDirection;
import com.musinsa.point.point.EarningStatus;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.point.TransactionType;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립취소. 취소할 수 있는지는 지금 잔액이 아니라 "한 번이라도 주문에 쓰였는가"로 판정한다.
 * 만료됐더라도 쓰인 적이 없으면 취소할 수 있다.
 */
@Service
public class EarnCancelService {

    private final PointAccountLocker accountLocker;
    private final PointEarningRepository earningRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointTransactionDetailRepository detailRepository;
    private final PointKeyGenerator pointKeyGenerator;
    private final Clock clock;

    public EarnCancelService(PointAccountLocker accountLocker, PointEarningRepository earningRepository,
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
    public EarnCancelResult cancel(String memberId, String pointKey) {
        PointAccount account = accountLocker.lockExisting(memberId);
        PointTransaction earnTransaction = findEarnTransactionOf(account, pointKey);
        PointEarning earning = earningRepository.findByTransactionId(earnTransaction.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.POINT_KEY_NOT_FOUND));

        if (earning.getStatus() == EarningStatus.CANCELED) {
            throw new ApiException(ErrorCode.EARN_ALREADY_CANCELED);
        }
        if (detailRepository.existsByEarningIdAndDirection(earning.getId(), DetailDirection.OUT)) {
            throw new ApiException(ErrorCode.EARN_ALREADY_USED);
        }

        long canceledAmount = earning.getOriginalAmount();
        earning.cancel();

        Instant now = clock.instant();
        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.earnCancel(
                account, pointKeyGenerator.generate(), canceledAmount, earnTransaction.getId(), now));

        return new EarnCancelResult(cancelTransaction.getPointKey(), canceledAmount,
                earningRepository.sumAvailableBalance(account.getId(), now));
    }

    /** 다른 타입의 거래나 다른 회원의 거래는 "그런 적립 키가 없다"와 같게 다룬다. */
    private PointTransaction findEarnTransactionOf(PointAccount account, String pointKey) {
        return transactionRepository.findByPointKey(pointKey)
                .filter(transaction -> transaction.getType() == TransactionType.EARN)
                .filter(transaction -> transaction.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.POINT_KEY_NOT_FOUND));
    }
}
