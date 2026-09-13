package com.musinsa.point.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * 여러 요청을 실제로 같은 순간에 부딪히게 한다. 모든 스레드가 출발 신호를 함께 기다렸다가 동시에 뛴다.
 * 결과는 스레드별로 "성공(비어 있음)" 또는 "터진 예외"로 돌려준다.
 */
public final class ConcurrentRuns {

    private static final int COMPLETION_TIMEOUT_SECONDS = 30;

    private ConcurrentRuns() {
    }

    public static List<Optional<Throwable>> runAll(int threadCount, IntConsumer task) {
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Optional<Throwable>>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int index = 0; index < threadCount; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    try {
                        task.accept(taskIndex);
                        return Optional.<Throwable>empty();
                    } catch (Throwable failure) {
                        return Optional.of(failure);
                    }
                }));
            }
            startSignal.countDown();
            return collect(futures);
        }
    }

    private static List<Optional<Throwable>> collect(List<Future<Optional<Throwable>>> futures) {
        List<Optional<Throwable>> outcomes = new ArrayList<>();
        for (Future<Optional<Throwable>> future : futures) {
            try {
                outcomes.add(future.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (Exception interrupted) {
                throw new IllegalStateException("동시 실행 결과를 거두지 못했습니다.", interrupted);
            }
        }
        return outcomes;
    }
}
