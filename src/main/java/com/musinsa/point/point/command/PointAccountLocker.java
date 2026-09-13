package com.musinsa.point.point.command;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountRegistrar;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 포인트를 바꾸는 모든 명령의 첫 단추. 계정 행을 잠가 같은 회원의 요청을 한 줄로 세운다.
 */
@Component
public class PointAccountLocker {

    private final PointAccountRepository accountRepository;
    private final PointAccountRegistrar accountRegistrar;
    private final Clock clock;

    public PointAccountLocker(PointAccountRepository accountRepository, PointAccountRegistrar accountRegistrar,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.accountRegistrar = accountRegistrar;
        this.clock = clock;
    }

    public PointAccount lockExisting(String memberId) {
        return accountRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /** 첫 적립이면 계정을 열고 잠근다. 같은 순간 다른 요청이 먼저 만들었다면 그 계정을 다시 잠그고 이어 간다. */
    public PointAccount lockOrOpen(String memberId) {
        return accountRepository.findByMemberIdForUpdate(memberId).orElseGet(() -> {
            try {
                accountRegistrar.register(memberId, clock.instant());
            } catch (DataIntegrityViolationException alreadyOpenedByAnotherRequest) {
                // 아래 재조회가 그 계정을 집어 든다.
            }
            return accountRepository.findByMemberIdForUpdate(memberId)
                    .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
        });
    }
}
