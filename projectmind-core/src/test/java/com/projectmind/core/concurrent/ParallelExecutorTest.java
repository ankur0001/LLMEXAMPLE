package com.projectmind.core.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelExecutorTest {

    @Test
    void preservesTaskOrder() {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int value = i;
            tasks.add(() -> value);
        }

        List<Integer> results = ParallelExecutor.invokeAll(tasks, 4);

        assertThat(results).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, 20).boxed().toList());
    }

    @Test
    void runsTasksConcurrently() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                int current = inFlight.incrementAndGet();
                maxInFlight.updateAndGet(max -> Math.max(max, current));
                Thread.sleep(20);
                inFlight.decrementAndGet();
                return 1;
            });
        }

        List<Integer> results = ParallelExecutor.invokeAll(tasks, 4);

        assertThat(results).hasSize(8);
        assertThat(maxInFlight.get()).isGreaterThan(1);
    }
}
