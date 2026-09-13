package com.musinsa.point.account;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정을 별도 트랜잭션으로 만든다. 같은 회원의 첫 적립이 동시에 들어오면 한쪽은 유일 제약에 걸리는데,
 * 그 실패가 진행 중인 적립 트랜잭션까지 되돌리지 않도록 경계를 나눠 두었다.
 */
@Component
public class PointAccountRegistrar {

    private final PointAccountRepository accountRepository;

    public PointAccountRegistrar(PointAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(String memberId, Instant createdAt) {
        accountRepository.saveAndFlush(PointAccount.open(memberId, createdAt));
    }
}
