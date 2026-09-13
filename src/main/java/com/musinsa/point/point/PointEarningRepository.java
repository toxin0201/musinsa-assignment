package com.musinsa.point.point;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEarningRepository extends JpaRepository<PointEarning, Long> {

    Optional<PointEarning> findByTransactionId(Long transactionId);

    /**
     * 사용할 수 있는 적립 건을 쓰는 순서대로 돌려준다.
     * 수기 지급분을 먼저 소진하고, 그 다음 만료가 임박한 순, 같으면 먼저 적립된 순이다.
     */
    @Query("""
            select e from PointEarning e
            where e.account.id = :accountId
              and e.status = com.musinsa.point.point.EarningStatus.ACTIVE
              and e.remainingAmount > 0
              and e.expiresAt > :now
            order by case when e.kind = com.musinsa.point.point.EarningKind.MANUAL then 0 else 1 end,
                     e.expiresAt asc,
                     e.id asc
            """)
    List<PointEarning> findUsableOrdered(@Param("accountId") Long accountId, @Param("now") Instant now);

    /** 보유 한도 판정과 잔액 조회가 함께 쓰는 "지금 쓸 수 있는 금액"의 정의. */
    @Query("""
            select coalesce(sum(e.remainingAmount), 0) from PointEarning e
            where e.account.id = :accountId
              and e.status = com.musinsa.point.point.EarningStatus.ACTIVE
              and e.expiresAt > :now
            """)
    long sumAvailableBalance(@Param("accountId") Long accountId, @Param("now") Instant now);
}
