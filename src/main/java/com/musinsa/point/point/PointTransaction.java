package com.musinsa.point.point;

import com.musinsa.point.account.PointAccount;
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
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 포인트 거래 한 건. 외부에는 pointKey 로만 노출한다. */
@Entity
@Table(name = "point_transaction")
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_key", nullable = false, length = 32, updatable = false)
    private String pointKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private PointAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16, updatable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "order_no", length = 64, updatable = false)
    private String orderNo;

    /** 사용 거래에만 채운다. (계정, 이 값) 유일 제약이 같은 주문의 이중 사용을 막는다. */
    @Column(name = "use_order_no", length = 64, updatable = false)
    private String useOrderNo;

    @Column(name = "related_transaction_id", updatable = false)
    private Long relatedTransactionId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PointTransaction() {
    }

    private PointTransaction(String pointKey, PointAccount account, TransactionType type, long amount,
            String orderNo, String useOrderNo, Long relatedTransactionId, Instant createdAt) {
        this.pointKey = pointKey;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.orderNo = orderNo;
        this.useOrderNo = useOrderNo;
        this.relatedTransactionId = relatedTransactionId;
        this.createdAt = createdAt;
    }

    public static PointTransaction earn(PointAccount account, String pointKey, long amount, Instant createdAt) {
        return new PointTransaction(pointKey, account, TransactionType.EARN, amount, null, null, null, createdAt);
    }

    /** 사용취소로 만료분을 다시 지급하는 적립. 원인이 된 사용취소 거래를 가리킨다. */
    public static PointTransaction reissue(PointAccount account, String pointKey, long amount,
            Long useCancelTransactionId, Instant createdAt) {
        return new PointTransaction(pointKey, account, TransactionType.EARN, amount, null, null,
                useCancelTransactionId, createdAt);
    }

    public static PointTransaction earnCancel(PointAccount account, String pointKey, long amount,
            Long earnTransactionId, Instant createdAt) {
        return new PointTransaction(pointKey, account, TransactionType.EARN_CANCEL, amount, null, null,
                earnTransactionId, createdAt);
    }

    public static PointTransaction use(PointAccount account, String pointKey, long amount, String orderNo,
            Instant createdAt) {
        return new PointTransaction(pointKey, account, TransactionType.USE, amount, orderNo, orderNo, null,
                createdAt);
    }

    public static PointTransaction useCancel(PointAccount account, String pointKey, long amount, String orderNo,
            Long useTransactionId, Instant createdAt) {
        return new PointTransaction(pointKey, account, TransactionType.USE_CANCEL, amount, orderNo, null,
                useTransactionId, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getPointKey() {
        return pointKey;
    }

    public PointAccount getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getUseOrderNo() {
        return useOrderNo;
    }

    public Long getRelatedTransactionId() {
        return relatedTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
