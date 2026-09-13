package com.musinsa.point.common;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각은 주입해서 쓴다. 만료 판정을 테스트에서 재현할 수 있어야 하기 때문이다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
