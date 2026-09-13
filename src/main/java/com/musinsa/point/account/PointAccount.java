package com.musinsa.point.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 회원 포인트 계정. 포인트를 바꾸는 모든 명령이 이 행을 잠그고 진행한다. */
@Entity
@Table(name = "point_account")
public class PointAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, length = 64, updatable = false)
    private String memberId;

    @Column(name = "max_balance")
    private Long maxBalance;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PointAccount() {
    }

    private PointAccount(String memberId, Instant createdAt) {
        this.memberId = memberId;
        this.createdAt = createdAt;
    }

    /** 첫 적립 시점에 계정을 연다. 개인 보유 한도는 비워 두고 설정 기본값을 따른다. */
    public static PointAccount open(String memberId, Instant createdAt) {
        return new PointAccount(memberId, createdAt);
    }

    /** null 로 바꾸면 설정 기본값으로 되돌아간다. */
    public void changeMaxBalance(Long maxBalance) {
        this.maxBalance = maxBalance;
    }

    public Long getId() {
        return id;
    }

    public String getMemberId() {
        return memberId;
    }

    public Long getMaxBalance() {
        return maxBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
