package com.musinsa.point.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByMemberId(String memberId);

    /**
     * 포인트를 바꾸는 모든 명령의 시작점. 같은 회원의 요청을 계정 행 하나로 직렬화한다.
     * 대기 상한은 접속 URL 의 LOCK_TIMEOUT 이 정한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointAccount a where a.memberId = :memberId")
    Optional<PointAccount> findByMemberIdForUpdate(@Param("memberId") String memberId);
}
