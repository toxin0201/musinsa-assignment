package com.musinsa.point.point.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 1회 적립 한도와 보유 한도는 코드가 아니라 설정에서 온다는 요구를 여기서 못박는다. */
@SpringBootTest
class PointPolicyPropertiesBindingTest {

    @Autowired
    private PointPolicyProperties properties;

    @Test
    @DisplayName("정책 값이 설정 파일에서 그대로 읽힌다")
    void policyValuesAreBoundFromConfiguration() {
        assertThat(properties.earn().minAmount()).isEqualTo(1);
        assertThat(properties.earn().maxAmount()).isEqualTo(100_000);
        assertThat(properties.balance().defaultMax()).isEqualTo(1_000_000);
        assertThat(properties.expiry().defaultDays()).isEqualTo(365);
        assertThat(properties.expiry().minDays()).isEqualTo(1);
        assertThat(properties.expiry().maxYears()).isEqualTo(5);
    }
}
