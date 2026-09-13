package com.musinsa.point.point.command;

import java.util.List;

public record UseResult(String pointKey, long amount, List<UseAllocation> allocations, long balance) {
}
