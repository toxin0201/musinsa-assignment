package com.musinsa.point.account;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원별 보유 한도 변경. 한도는 적립 시점에만 보므로 지금 잔액보다 낮게 잡아도 이미 쌓인 포인트는 건드리지 않는다.
 * 한도만 먼저 걸어 두는 일도 있으므로 계정이 없으면 여기서 연다.
 */
@Service
public class AccountLimitService {

    private static final long MIN_MAX_BALANCE = 1;

    private final PointAccountLocker accountLocker;
    private final PointAccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;

    public AccountLimitService(PointAccountLocker accountLocker, PointAccountRepository accountRepository,
            PlatformTransactionManager transactionManager) {
        this.accountLocker = accountLocker;
        this.accountRepository = accountRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 계정 열기를 트랜잭션 밖에서 먼저 끝낸다 — 이유는 {@link PointAccountLocker#openIfAbsent(String)} 참조. */
    public AccountLimitResult setMaxBalance(String memberId, Long maxBalance) {
        if (maxBalance != null && maxBalance < MIN_MAX_BALANCE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "보유 한도는 %d 이상이거나 비워 두어야 합니다.".formatted(MIN_MAX_BALANCE));
        }
        accountLocker.openIfAbsent(memberId);

        return transactionTemplate.execute(status -> {
            PointAccount account = accountLocker.lockExisting(memberId);
            account.changeMaxBalance(maxBalance);
            accountRepository.save(account);
            return new AccountLimitResult(memberId, maxBalance);
        });
    }
}
