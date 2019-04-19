package com.cloudkitchens.fulfillment.entities.pickup;

import com.cloudkitchens.fulfillment.common.ExecutorServicesUtil;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.shelves.AddResult;
import com.cloudkitchens.fulfillment.entities.shelves.IShelfPod;
import com.cloudkitchens.fulfillment.entities.shelves.observers.IShelfPodObserver;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class listens to ShelfPod's addOrder events, and dispatches a message for order pickup.
 * Since we dont have any real integration with cab service, this class will submit a task with scheduled delay of 2-10 seconds
 * which pickups order from the shelf (mimicking that in realtime it may take that many seconds to pickup the order).
 */
@Slf4j @Singleton public class Dispatcher implements IShelfPodObserver {

    private static final String PICKUP_THREAD_NAME_PREFIX = "pickup-threads-";
    private static final String PICKUP_RESULTS_READER_THREAD_NAME_PRE = "pickup-results-reader-";
    private static final int THREAD_COUNT = 30;
    private final Random random = new Random();

    private final int minDelayForPickupInSecs, maxDelayForPickupInSecs;
    private final IShelfPod shelfPod;
    private volatile ExecutorCompletionService<Boolean> completionService;
    private volatile ScheduledExecutorService scheduledExecutorService;
    private volatile ExecutorService executorService;

    @Inject public Dispatcher(IShelfPod shelfPod, @Named("minDelayForPickupInSecs") int minDelayForPickupInSecs,
        @Named("maxDelayForPickupInSecs") int maxDelayForPickupInSecs) {
        this.minDelayForPickupInSecs = minDelayForPickupInSecs;
        this.maxDelayForPickupInSecs = maxDelayForPickupInSecs;
        this.shelfPod = shelfPod;

    }

    public void startBackgroundActivities() {
        this.scheduledExecutorService = ExecutorServicesUtil
            .createScheduledThreadPool(PICKUP_THREAD_NAME_PREFIX, THREAD_COUNT, ExecutorServicesUtil.WAIT_TIME_TO_SHUTDOWN_MS);
        this.executorService = ExecutorServicesUtil
            .createScheduledThreadPool(PICKUP_RESULTS_READER_THREAD_NAME_PRE, 1, ExecutorServicesUtil.WAIT_TIME_TO_SHUTDOWN_MS);

        this.completionService = new ExecutorCompletionService<>(scheduledExecutorService);

        shelfPod.addObserver(this);
        executorService.submit(new PickupTaskResultsReader(completionService));
        log.info("Started background activities - done.");
    }

    public void stopBackgroundActivities() {
        if (executorService != null)
            executorService.shutdownNow();
        if (scheduledExecutorService != null)
            scheduledExecutorService.shutdownNow();
    }

    /**
     * This task reads the results of PickupTask. This threads waits on the completion service until the result is available.
     * If there are no results available, then this thread gets suspended, so this method saves resources.
     */
    private static class PickupTaskResultsReader implements Runnable {

        private final ExecutorCompletionService<Boolean> completionService;

        public PickupTaskResultsReader(ExecutorCompletionService<Boolean> completionService) {
            this.completionService = completionService;
        }

        @Override public void run() {
            log.info("Launching pickup task results reader.");
            while (true) {
                try {
                    Future<Boolean> result = completionService.take();
                    log.info("Pickup task completed, isSuccessful={}", result.get());
                } catch (InterruptedException e) {
                    log.warn("Got interrupted while waiting for the results from completion service. Quitting the thread.");
                    return;
                } catch (ExecutionException e) {
                    log.error("Exception occurred while reading the results from future. Continuing with the nex result.", e);
                }
            }
        }
    }


    private static class PickupTask implements Callable<Boolean> {

        private final IShelfPod shelfPod;

        public PickupTask(IShelfPod shelfPod) {
            this.shelfPod = shelfPod;
        }

        @Override public Boolean call() {
            Order order = shelfPod.pollOrder();
            log.info("Order picked up and the order={} ", order);
            if (order != null)
                return true;
            return false;
        }
    }


    /**
     * This submits a pickup task to pickup the order with same random delay(mimicking real driver's arrival time).
     */
    private void dispatch() {
        // The following random generates a number between 2 and 10, assumption that taxi will take about 2-10 seconds for picking up the order.
        int delay = minDelayForPickupInSecs + random.nextInt(minDelayForPickupInSecs - maxDelayForPickupInSecs + 1);
        scheduledExecutorService.schedule(new PickupTask(shelfPod), delay, TimeUnit.SECONDS);
        log.info("Dispatched a message for pickup.");
    }

    @Override public void postAddOrder(Order order, AddResult addResult) {
        if (addResult.isAdded()) {
            dispatch();
        }
    }
}
