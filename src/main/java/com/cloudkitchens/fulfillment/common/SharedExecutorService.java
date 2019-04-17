package com.cloudkitchens.fulfillment.common;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maintains shared executor service instance, and also registers a watcher for JVM shutdown signal, and stops the executor on receiving a shutdown signal.
 * <p>
 * NOTE: This should be used across all classes. Creating a new executor service is heavy, that may increase the load on the system, and unnecessary context switches.
 */
@Slf4j public class SharedExecutorService {

    public static final String THREAD_NAME_PREFIX = "shared-executors-", SCHEDULED_THREAD_NAME_PREFIX = "scheduled-shared-executors";
    public static final int FIXED_THREADS_COUNT = 20, SCHED_THREADS_COUNT = 30;

    public static final long WAIT_TIME_TO_SHUTDOWN_MS = 120 * 1000; // In milliseconds

    private static final ExecutorService FIXED_EXECUTOR_SERVICE;
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE;

    static {
        FIXED_EXECUTOR_SERVICE = Executors.newFixedThreadPool(FIXED_THREADS_COUNT, getThreadFactory(THREAD_NAME_PREFIX));
        SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(SCHED_THREADS_COUNT, getThreadFactory(SCHEDULED_THREAD_NAME_PREFIX));

        Runtime.getRuntime().addShutdownHook(getShutdownHook(FIXED_EXECUTOR_SERVICE));
        Runtime.getRuntime().addShutdownHook(getShutdownHook(SCHEDULED_EXECUTOR_SERVICE));
    }

    public static ExecutorService getFixedExecutorService() {
        return FIXED_EXECUTOR_SERVICE;
    }

    public static ScheduledExecutorService getScheduledExecutorService() {
        return SCHEDULED_EXECUTOR_SERVICE;
    }

    private static ThreadFactory getThreadFactory(String prefix) {
        return new ThreadFactory() {
            private AtomicLong count = new AtomicLong();

            @Override public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(prefix + count.incrementAndGet());
                return thread;
            }
        };
    }

    private static Thread getShutdownHook(ExecutorService executorService) {
        return new Thread(() -> {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(WAIT_TIME_TO_SHUTDOWN_MS, TimeUnit.MILLISECONDS)) {
                    log.warn("Executor could not stop on time. Stopping abruptly");
                    List<Runnable> unfinished = executorService.shutdownNow();
                    log.warn("No of unfinished tasks : " + unfinished.size());
                } else
                    log.info("Executor stopped safely.");
            } catch (InterruptedException e) {
                log.error("Interrupted while executor is shutting down", e);
            }
        });
    }
}
