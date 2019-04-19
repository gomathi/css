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
 * Util class to spawn different executor services. This class makes sure that executor services are registered shutdown hook with
 * Runtime, so they can shutdown safely in case of JVM shutdown signal.
 */
@Slf4j public class ExecutorServicesUtil {

    public static final long WAIT_TIME_TO_SHUTDOWN_MS = 120 * 1000; // In milliseconds

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

    private static Thread getShutdownHook(ExecutorService executorService, long waitTimeToShutdownInMs) {
        return new Thread(() -> {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(waitTimeToShutdownInMs, TimeUnit.MILLISECONDS)) {
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

    public static ExecutorService createFixedThreadPool(String threadNamePrefix, int threadsCount, long waitTimeToShutdownInMs) {
        ExecutorService executorService = Executors.newFixedThreadPool(threadsCount, getThreadFactory(threadNamePrefix));
        Runtime.getRuntime().addShutdownHook(getShutdownHook(executorService, waitTimeToShutdownInMs));
        return executorService;
    }

    public static ScheduledExecutorService createScheduledThreadPool(String threadNamePrefix, int threadsCount,
        long waitTimeToShutdownInMs) {
        ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(threadsCount, getThreadFactory(threadNamePrefix));
        Runtime.getRuntime().addShutdownHook(getShutdownHook(scheduledExecutorService, waitTimeToShutdownInMs));
        return scheduledExecutorService;
    }
}
