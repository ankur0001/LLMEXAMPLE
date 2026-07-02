package com.projectmind.core.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Runs tasks in parallel using virtual threads when available (Java 21+),
 * otherwise a fixed platform thread pool. Concurrency is always bounded.
 */
public final class ParallelExecutor {

    private ParallelExecutor() {
    }

    /**
     * Executes tasks preserving result order.
     */
    public static <T> List<T> invokeAll(List<Callable<T>> tasks, int maxConcurrency) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        int concurrency = Math.max(1, Math.min(maxConcurrency, tasks.size()));
        if (concurrency == 1) {
            List<T> results = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                results.add(callUnchecked(task));
            }
            return results;
        }

        ExecutorService executor = createExecutor(concurrency);
        try {
            List<Future<T>> futures = executor.invokeAll(tasks);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ParallelExecutionException("Parallel execution interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ParallelExecutionException("Parallel execution failed", cause);
        } finally {
            executor.shutdown();
        }
    }

    private static <T> T callUnchecked(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ParallelExecutionException("Task execution failed", ex);
        }
    }

    private static ExecutorService createExecutor(int maxConcurrency) {
        ExecutorService delegate = tryVirtualThreadExecutor();
        if (delegate != null) {
            return new SemaphoreExecutor(delegate, maxConcurrency);
        }
        return Executors.newFixedThreadPool(maxConcurrency);
    }

    private static ExecutorService tryVirtualThreadExecutor() {
        try {
            var factoryMethod = Thread.class.getMethod("ofVirtual");
            Object builder = factoryMethod.invoke(null);
            var factory = builder.getClass().getMethod("factory").invoke(builder);
            var executorMethod = Executors.class.getMethod("newThreadPerTaskExecutor", java.util.concurrent.ThreadFactory.class);
            return (ExecutorService) executorMethod.invoke(null, factory);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static final class SemaphoreExecutor implements ExecutorService {

        private final ExecutorService delegate;
        private final Semaphore semaphore;

        private SemaphoreExecutor(ExecutorService delegate, int maxConcurrency) {
            this.delegate = delegate;
            this.semaphore = new Semaphore(maxConcurrency);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(wrap(command));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(wrap(task));
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(wrap(task));
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(this::wrap).toList());
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit)
                throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(wrap(task), result);
        }

        @Override
        public <T> java.util.List<Future<T>> invokeAll(
                java.util.Collection<? extends Callable<T>> tasks,
                long timeout,
                java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(this::wrap).toList(), timeout, unit);
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks.stream().map(this::wrap).toList());
        }

        @Override
        public <T> T invokeAny(
                java.util.Collection<? extends Callable<T>> tasks,
                long timeout,
                java.util.concurrent.TimeUnit unit)
                throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
            return delegate.invokeAny(tasks.stream().map(this::wrap).toList(), timeout, unit);
        }

        private Runnable wrap(Runnable task) {
            return () -> {
                try {
                    semaphore.acquire();
                    task.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ParallelExecutionException("Parallel execution interrupted", ex);
                } finally {
                    semaphore.release();
                }
            };
        }

        private <T> Callable<T> wrap(Callable<T> task) {
            return () -> {
                try {
                    semaphore.acquire();
                    return task.call();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ParallelExecutionException("Parallel execution interrupted", ex);
                } finally {
                    semaphore.release();
                }
            };
        }
    }
}
