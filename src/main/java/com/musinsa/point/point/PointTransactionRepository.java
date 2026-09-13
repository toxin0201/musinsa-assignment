package com.musinsa.point.point;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Optional<PointTransaction> findByPointKey(String pointKey);

    /** 같은 주문의 이중 사용을 요청 단계에서 걸러낸다. 최종 방어선은 유일 제약이다. */
    @Query("""
            select count(t) > 0 from PointTransaction t
            where t.account.id = :accountId
              and t.useOrderNo = :orderNo
            """)
    boolean existsUseByAccountIdAndOrderNo(@Param("accountId") Long accountId, @Param("orderNo") String orderNo);
}
