package com.musinsa.point.point;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionDetailRepository extends JpaRepository<PointTransactionDetail, Long> {

    /** 적립취소 가능 여부의 판정 기준. 현재 잔액이 아니라 사용 이력의 존재로 본다. */
    @Query("""
            select count(d) > 0 from PointTransactionDetail d
            where d.earning.id = :earningId
              and d.direction = :direction
            """)
    boolean existsByEarningIdAndDirection(@Param("earningId") Long earningId,
            @Param("direction") DetailDirection direction);

    /** 이 사용 상세로 지금까지 되돌아온 금액. 상세별 남은 취소 가능액 = 상세 금액 - 이 합계. */
    @Query("""
            select coalesce(sum(d.amount), 0) from PointTransactionDetail d
            where d.reversedDetailId = :outDetailId
              and d.direction = com.musinsa.point.point.DetailDirection.IN
            """)
    long sumRestoredAmountOf(@Param("outDetailId") Long outDetailId);

    @Query("""
            select d from PointTransactionDetail d
            where d.transaction.id = :transactionId
              and d.direction = com.musinsa.point.point.DetailDirection.OUT
            order by d.seq asc
            """)
    List<PointTransactionDetail> findOutgoingDetailsOfTransaction(@Param("transactionId") Long transactionId);

    /** 적립 건이 어떤 주문에서 얼마나 쓰였는지 되짚는 출발점. */
    @Query("""
            select d from PointTransactionDetail d
            join fetch d.transaction
            where d.earning.id = :earningId
              and d.direction = com.musinsa.point.point.DetailDirection.OUT
            order by d.id asc
            """)
    List<PointTransactionDetail> findOutgoingDetailsOfEarning(@Param("earningId") Long earningId);
}
