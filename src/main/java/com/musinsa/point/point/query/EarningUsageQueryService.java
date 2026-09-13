package com.musinsa.point.point.query;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountLocker;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import com.musinsa.point.point.PointEarning;
import com.musinsa.point.point.PointEarningRepository;
import com.musinsa.point.point.PointTransaction;
import com.musinsa.point.point.PointTransactionDetail;
import com.musinsa.point.point.PointTransactionDetailRepository;
import com.musinsa.point.point.PointTransactionRepository;
import com.musinsa.point.point.TransactionType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립 한 건이 어떤 주문에서 얼마나 쓰였는지 되짚는다.
 * 사용으로 나간 금액(OUT)과 그 줄로 되돌아온 금액(IN)을 주문별로 나란히 보여 준다.
 */
@Service
public class EarningUsageQueryService {

    private final PointAccountLocker accountLocker;
    private final PointEarningRepository earningRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointTransactionDetailRepository detailRepository;

    public EarningUsageQueryService(PointAccountLocker accountLocker, PointEarningRepository earningRepository,
            PointTransactionRepository transactionRepository, PointTransactionDetailRepository detailRepository) {
        this.accountLocker = accountLocker;
        this.earningRepository = earningRepository;
        this.transactionRepository = transactionRepository;
        this.detailRepository = detailRepository;
    }

    @Transactional(readOnly = true)
    public EarningUsageView usages(String memberId, String earnPointKey) {
        PointAccount account = accountLocker.findExisting(memberId);
        PointTransaction earnTransaction = findEarnTransactionOf(account, earnPointKey);
        PointEarning earning = earningRepository.findByTransactionId(earnTransaction.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.POINT_KEY_NOT_FOUND));

        List<OrderUsage> usages = detailRepository.findOutgoingDetailsOfEarning(earning.getId()).stream()
                .map(this::toOrderUsage)
                .toList();

        return new EarningUsageView(summaryOf(earnTransaction.getPointKey(), earning), usages);
    }

    /** 이 줄로 되돌아온 금액이 "취소된 금액"이고, 나머지가 아직 그 주문에 묶여 있는 금액이다. */
    private OrderUsage toOrderUsage(PointTransactionDetail outDetail) {
        long canceledAmount = detailRepository.sumRestoredAmountOf(outDetail.getId());
        PointTransaction useTransaction = outDetail.getTransaction();
        return new OrderUsage(useTransaction.getOrderNo(), useTransaction.getPointKey(),
                outDetail.getAmount(), canceledAmount, outDetail.getAmount() - canceledAmount);
    }

    private static EarningSummary summaryOf(String pointKey, PointEarning earning) {
        return new EarningSummary(pointKey, earning.getKind(), earning.getStatus(), earning.getOriginalAmount(),
                earning.getRemainingAmount(), earning.getEarnedAt(), earning.getExpiresAt());
    }

    private PointTransaction findEarnTransactionOf(PointAccount account, String pointKey) {
        return transactionRepository.findByPointKey(pointKey)
                .filter(transaction -> transaction.getType() == TransactionType.EARN)
                .filter(transaction -> transaction.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.POINT_KEY_NOT_FOUND));
    }
}
