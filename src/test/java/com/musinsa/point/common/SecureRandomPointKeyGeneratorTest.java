package com.musinsa.point.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureRandomPointKeyGeneratorTest {

    private static final Pattern URL_SAFE = Pattern.compile("^[0-9A-Za-z]{22}$");

    private final PointKeyGenerator generator = new SecureRandomPointKeyGenerator();

    @Test
    @DisplayName("외부에 노출하는 포인트 키는 22자 영숫자다")
    void generatesTwentyTwoCharacterAlphanumericKey() {
        assertThat(generator.generate()).matches(URL_SAFE);
    }

    @Test
    @DisplayName("연속 생성한 키 10,000 개가 모두 다르다")
    void generatedKeysDoNotRepeat() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            keys.add(generator.generate());
        }

        assertThat(keys).hasSize(10_000);
    }
}
