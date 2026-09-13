package com.musinsa.point.point;

/** 적립 건의 생애 상태. 만료는 상태가 아니라 expires_at 비교로 판정한다. */
public enum EarningStatus {
    ACTIVE,
    CANCELED
}
