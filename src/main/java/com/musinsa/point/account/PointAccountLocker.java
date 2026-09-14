package com.musinsa.point.account;

import com.musinsa.point.common.ApiException;
import com.musinsa.point.common.ErrorCode;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 회원 계정을 집어 오는 한 곳. 포인트를 바꾸는 명령은 이 행을 잠가 같은 회원의 요청을 한 줄로 세운다. */
@Component
public class PointAccountLocker {

    private static final Logger log = LoggerFactory.getLogger(PointAccountLocker.class);

    private final PointAccountRepository accountRepository;
    private final PointAccountRegistrar accountRegistrar;
    private final Clock clock;

    public PointAccountLocker(PointAccountRepository accountRepository, PointAccountRegistrar accountRegistrar,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.accountRegistrar = accountRegistrar;
        this.clock = clock;
    }

    /** 조회 전용 경로. 값을 바꾸지 않으므로 행을 잠그지 않는다. */
    public PointAccount findExisting(String memberId) {
        return accountRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public PointAccount lockExisting(String memberId) {
        return accountRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 계정이 없으면 연다. <strong>반드시 명령 트랜잭션을 열기 전에 부른다.</strong>
     *
     * <p>계정 만들기는 그 자체로 트랜잭션 하나다. 이미 트랜잭션이 열린 안에서 부르면 요청 하나가
     * 커넥션을 두 개(바깥 것과 안쪽 것) 동시에 쥐게 되고, 동시 요청 수가 커넥션 풀 크기에 이르는 순간
     * 모두가 서로의 두 번째 커넥션을 기다리며 멈춘다. 트랜잭션 밖에서 부르면 커넥션을 하나 잠깐 쓰고
     * 돌려주므로 그 교착이 생길 수 없다.
     *
     * <p>같은 회원의 첫 요청이 겹치면 한쪽은 유일 제약에 걸린다. 이는 고칠 것이 있는 오류가 아니라
     * 먼저 연 쪽이 이긴 정상 경합이므로, 그 계정이 실제로 생겼는지 확인한 뒤 그대로 쓴다.
     */
    public void openIfAbsent(String memberId) {
        try {
            accountRegistrar.openIfAbsent(memberId, clock.instant());
        } catch (DataIntegrityViolationException openedByAnotherRequest) {
            accountRepository.findByMemberId(memberId).orElseThrow(() -> openedByAnotherRequest);
            log.debug("계정을 여는 도중 다른 요청과 겹쳤다. 먼저 열린 계정을 그대로 쓴다. memberId={}", memberId);
        }
    }
}
