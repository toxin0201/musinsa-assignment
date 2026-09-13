package com.musinsa.point.point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 거래가 적립 건별로 움직인 금액. 이 행들이 있어야 "어떤 적립이 어떤 주문에서 얼마 쓰였나"를 1원 단위로 되짚을 수 있다.
 */
@Entity
@Table(name = "point_transaction_detail")
public class PointTransactionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private PointTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "earning_id", nullable = false, updatable = false)
    private PointEarning earning;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8, updatable = false)
    private DetailDirection direction;

    /** 복원 상세가 되돌리는 원 사용 상세. 상세별 남은 취소 가능액은 이 연결로 계산한다. */
    @Column(name = "reversed_detail_id", updatable = false)
    private Long reversedDetailId;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    protected PointTransactionDetail() {
    }

    private PointTransactionDetail(PointTransaction transaction, PointEarning earning, long amount,
            DetailDirection direction, Long reversedDetailId, int seq) {
        this.transaction = transaction;
        this.earning = earning;
        this.amount = amount;
        this.direction = direction;
        this.reversedDetailId = reversedDetailId;
        this.seq = seq;
    }

    public static PointTransactionDetail out(PointTransaction transaction, PointEarning earning, long amount,
            int seq) {
        return new PointTransactionDetail(transaction, earning, amount, DetailDirection.OUT, null, seq);
    }

    public static PointTransactionDetail in(PointTransaction transaction, PointEarning earning, long amount,
            Long reversedDetailId, int seq) {
        return new PointTransactionDetail(transaction, earning, amount, DetailDirection.IN, reversedDetailId, seq);
    }

    public Long getId() {
        return id;
    }

    public PointTransaction getTransaction() {
        return transaction;
    }

    public PointEarning getEarning() {
        return earning;
    }

    public long getAmount() {
        return amount;
    }

    public DetailDirection getDirection() {
        return direction;
    }

    public Long getReversedDetailId() {
        return reversedDetailId;
    }

    public int getSeq() {
        return seq;
    }
}
