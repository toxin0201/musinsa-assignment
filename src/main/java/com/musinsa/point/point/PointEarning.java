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

/**
 * 적립 건. 잔액을 가진 최소 단위이며 사용 · 사용취소는 이 단위로 금액을 움직인다.
 * 만료는 별도 상태나 배치가 아니라 expires_at 과 현재 시각의 비교로 판정한다.
 */
@Entity
@Table(name = "point_earning")
public class PointEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private PointAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false, unique = true)
    private PointTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private EarningKind kind;

    @Column(name = "original_amount", nullable = false, updatable = false)
    private long originalAmount;

    @Column(name = "remaining_amount", nullable = false)
    private long remainingAmount;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EarningStatus status;

    @Column(name = "admin_id", length = 64, updatable = false)
    private String adminId;

    @Column(name = "reason", length = 200, updatable = false)
    private String reason;

    /** 만료분을 되돌리며 새로 만든 적립 건이면 원인이 된 사용취소 거래를 가리킨다. */
    @Column(name = "reissued_from_transaction_id", updatable = false)
    private Long reissuedFromTransactionId;

    protected PointEarning() {
    }

    private PointEarning(PointAccount account, PointTransaction transaction, EarningKind kind, long amount,
            Instant earnedAt, Instant expiresAt, String adminId, String reason, Long reissuedFromTransactionId) {
        this.account = account;
        this.transaction = transaction;
        this.kind = kind;
        this.originalAmount = amount;
        this.remainingAmount = amount;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
        this.status = EarningStatus.ACTIVE;
        this.adminId = adminId;
        this.reason = reason;
        this.reissuedFromTransactionId = reissuedFromTransactionId;
    }

    public static PointEarning general(PointAccount account, PointTransaction transaction, long amount,
            Instant earnedAt, Instant expiresAt) {
        return new PointEarning(account, transaction, EarningKind.GENERAL, amount, earnedAt, expiresAt,
                null, null, null);
    }

    public static PointEarning manual(PointAccount account, PointTransaction transaction, long amount,
            Instant earnedAt, Instant expiresAt, String adminId, String reason) {
        return new PointEarning(account, transaction, EarningKind.MANUAL, amount, earnedAt, expiresAt,
                adminId, reason, null);
    }

    /** 만료된 몫을 사용취소로 되돌릴 때 만드는 적립 건. 종류는 원 적립 건에서 물려받는다. */
    public static PointEarning reissued(PointAccount account, PointTransaction transaction, EarningKind kind,
            long amount, Instant earnedAt, Instant expiresAt, Long useCancelTransactionId) {
        return new PointEarning(account, transaction, kind, amount, earnedAt, expiresAt,
                null, null, useCancelTransactionId);
    }

    public void deduct(long amount) {
        requirePositive(amount);
        if (amount > remainingAmount) {
            throw new IllegalArgumentException("적립 건 잔액보다 많이 차감할 수 없습니다: " + amount + " > " + remainingAmount);
        }
        this.remainingAmount -= amount;
    }

    public void restore(long amount) {
        requirePositive(amount);
        if (remainingAmount + amount > originalAmount) {
            throw new IllegalArgumentException("최초 적립액을 넘겨 복원할 수 없습니다: " + amount);
        }
        this.remainingAmount += amount;
    }

    public void cancel() {
        if (status == EarningStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 적립 건입니다.");
        }
        this.status = EarningStatus.CANCELED;
        this.remainingAmount = 0;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsableAt(Instant now) {
        return status == EarningStatus.ACTIVE && remainingAmount > 0 && !isExpiredAt(now);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 1 이상이어야 합니다: " + amount);
        }
    }

    public Long getId() {
        return id;
    }

    public PointAccount getAccount() {
        return account;
    }

    public PointTransaction getTransaction() {
        return transaction;
    }

    public EarningKind getKind() {
        return kind;
    }

    public long getOriginalAmount() {
        return originalAmount;
    }

    public long getRemainingAmount() {
        return remainingAmount;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public EarningStatus getStatus() {
        return status;
    }

    public String getAdminId() {
        return adminId;
    }

    public String getReason() {
        return reason;
    }

    public Long getReissuedFromTransactionId() {
        return reissuedFromTransactionId;
    }
}
