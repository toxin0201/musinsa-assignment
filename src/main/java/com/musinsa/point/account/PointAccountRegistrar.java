package com.musinsa.point.account;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정을 별도 트랜잭션으로 연다. 같은 회원의 첫 요청이 동시에 들어오면 한쪽은 유일 제약에 걸리는데,
 * 그 실패가 진행 중인 명령 트랜잭션까지 되돌리지 않도록 경계를 나눠 두었다.
 */
@Component
public class PointAccountRegistrar {

    private final PointAccountRepository accountRepository;

    public PointAccountRegistrar(PointAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 이미 있으면 아무것도 하지 않는다. 유일 제약 충돌은 삼키지 않고 그대로 올려 보낸다 —
     * 여기서 잡으면 이 트랜잭션이 커밋 불가 상태인 채로 정상 종료를 시도하기 때문이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void openIfAbsent(String memberId, Instant createdAt) {
        if (accountRepository.findByMemberId(memberId).isPresent()) {
            return;
        }
        accountRepository.saveAndFlush(PointAccount.open(memberId, createdAt));
    }
}
