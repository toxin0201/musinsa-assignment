package com.musinsa.point.common;

/** 외부에 노출하는 거래 식별자를 만든다. 값은 예측 불가해야 하고 서로 겹치지 않아야 한다. */
public interface PointKeyGenerator {

    String generate();
}
