package com.cloudkitchens.fulfillment.entities.pickup;

import com.cloudkitchens.fulfillment.common.ExecutorServicesUtil;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.shelves.AddResult;
import com.cloudkitchens.fulfillment.entities.shelves.IShelfPod;
import com.cloudkitchens.fulfillment.entities.shelves.observers.IShelfPodObserver;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class listens to ShelfPod's addOrder events, and dispatches a message to drive for order pickup.
 * Since we dont have any real integration with taxi service, this class will submit a task with scheduled delay of 2-10 seconds
 * (mimicking that in realtime it may take that many seconds to pickup the order).
 */
@Slf4j @Singleton public class Dispatcher implements IShelfPodObserver {

    private static final int MIN_DELAY_FOR_PICKUP_IN_SECS = 2;
    private static final int MAX_DELAY_FOR_PICKUP_IN_SECS = 10;
    private static final String THREAD_NAME_PREFIX = "dispatcher-threads";
    private static final int THREAD_COUNT = 30;

    private final Random random = new Random();
    private final IShelfPod shelfPod;
    private final ExecutorCompletionService<Boolean> completionService;
    private final ScheduledExecutorService scheduledExecutorService;

    @Inject public Dispatcher(IShelfPod shelfPod) {
        this.shelfPod = shelfPod;
        this.scheduledExecutorService =
            ExecutorServicesUtil.createScheduledThreadPool(THREAD_NAME_PREFIX, THREAD_COUNT, ExecutorServicesUtil.WAIT_TIME_TO_SHUTDOWN_MS);
        this.completionService = new ExecutorCompletionService<>(scheduledExecutorService);
    }

    public void startBackgroundActivities() {
        shelfPod.addObserver(this);
        ExecutorServicesUtil.getSharedExecutorService().submit(new PickupTaskResultsReader(completionService));
        log.info("Started background activities - done.");
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
            log.info("Order picked up orderId = {} ", (order == null) ? null : order.getId());
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
        int delay = MIN_DELAY_FOR_PICKUP_IN_SECS + random.nextInt(MAX_DELAY_FOR_PICKUP_IN_SECS - MIN_DELAY_FOR_PICKUP_IN_SECS + 1);
        scheduledExecutorService.schedule(new PickupTask(shelfPod), delay, TimeUnit.SECONDS);
        log.info("Dispatched a message for pickup.");
    }

    @Override public void postAddOrder(Order order, AddResult addResult) {
        if (addResult.isAdded()) {
            dispatch();
        }
    }
}
