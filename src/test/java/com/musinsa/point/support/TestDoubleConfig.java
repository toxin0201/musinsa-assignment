package com.musinsa.point.support;

import com.musinsa.point.common.PointKeyGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 테스트가 시각과 키 생성을 손에 쥐도록 운영 빈을 대신한다. */
@TestConfiguration
public class TestDoubleConfig {

    /** MutableClock 은 Clock 이기도 하므로 이 한 개가 운영 Clock 빈을 대신한다. */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public PointKeyGenerator sequentialPointKeyGenerator() {
        return new SequentialPointKeyGenerator();
    }
}
