package com.musinsa.point.common;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 영숫자 22자 난수 키. 128비트 이상의 엔트로피를 담아 실무 규모에서 충돌을 사실상 배제한다. */
@Component
public class SecureRandomPointKeyGenerator implements PointKeyGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int KEY_LENGTH = 22;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        char[] key = new char[KEY_LENGTH];
        for (int i = 0; i < KEY_LENGTH; i++) {
            key[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(key);
    }
}
