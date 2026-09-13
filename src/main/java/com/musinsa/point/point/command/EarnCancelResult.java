package com.musinsa.point.point.command;

public record EarnCancelResult(String pointKey, long canceledAmount, long balance) {
}
