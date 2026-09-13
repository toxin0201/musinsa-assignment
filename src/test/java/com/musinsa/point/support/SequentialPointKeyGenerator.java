package com.musinsa.point.support;

import com.musinsa.point.common.PointKeyGenerator;
import java.util.concurrent.atomic.AtomicInteger;

/** 순서대로 A, B, C … 를 돌려주는 키 생성기. 시나리오 설명의 기호와 저장된 값을 그대로 맞춰 읽기 쉽게 한다. */
public class SequentialPointKeyGenerator implements PointKeyGenerator {

    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public String generate() {
        int index = sequence.getAndIncrement();
        if (index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        return "K" + index;
    }

    public void reset() {
        sequence.set(0);
    }
}
