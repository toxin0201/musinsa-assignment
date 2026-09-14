package com.musinsa.point.account;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정을 여는 한 걸음. 명령 트랜잭션을 열기 <strong>전에</strong> 불리므로 여기서 트랜잭션이 시작되고
 * 곧바로 끝난다. 덕분에 요청 하나가 커넥션을 두 개 쥐는 일이 없고, 계정 만들기가 실패해도
 * 뒤이어 열릴 명령 트랜잭션과는 서로 얽히지 않는다.
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
     * 겹친 요청을 어떻게 다룰지는 {@link PointAccountLocker#openIfAbsent(String)} 가 정한다.
     */
    @Transactional
    public void openIfAbsent(String memberId, Instant createdAt) {
        if (accountRepository.findByMemberId(memberId).isPresent()) {
            return;
        }
        accountRepository.saveAndFlush(PointAccount.open(memberId, createdAt));
    }
}
