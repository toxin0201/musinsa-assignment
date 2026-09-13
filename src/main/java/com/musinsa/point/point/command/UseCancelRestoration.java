package com.musinsa.point.point.command;

/**
 * 되돌린 금액 한 줄. 원 적립 건이 아직 살아 있으면 그 자리로 돌아가고,
 * 만료됐으면 새 적립으로 지급되며 그 새 적립의 pointKey 가 함께 실린다.
 */
public record UseCancelRestoration(String earningPointKey, long amount, boolean reissued,
        String newEarningPointKey) {

    public static UseCancelRestoration restoredInPlace(String earningPointKey, long amount) {
        return new UseCancelRestoration(earningPointKey, amount, false, null);
    }

    public static UseCancelRestoration reissuedAs(String earningPointKey, long amount,
            String newEarningPointKey) {
        return new UseCancelRestoration(earningPointKey, amount, true, newEarningPointKey);
    }
}
