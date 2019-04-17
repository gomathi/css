package com.cloudkitchens.fulfillment.entities.pickup;

import com.cloudkitchens.fulfillment.common.SharedExecutorService;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.shelves.ShelfPod;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class acts as a mock PickupService.
 */
@Slf4j public class PickupService {

    private static final int MIN_DELAY_FOR_PICKUP_IN_SECS = 2;
    private static final int MAX_DELAY_FOR_PICKUP_IN_SECS = 10;
    private final Random random = new Random();
    private final ShelfPod shelfPod;

    @Inject public PickupService(ShelfPod shelfPod) {
        this.shelfPod = shelfPod;
    }

    /**
     * ShelfPod requests for pickup by providing what kind of order (Hot, Cold, or Frozen), and whether the order is stored in Overflow shelf or not.
     * <p>
     * Internally this function submits a task to executor service for the order to be removed after some random delay between (2, 10) seconds,
     * this mocks a taxi driver is picking up an order.
     */
    public Future<Order> requestForPickup() {
        // The following random generates a number between 2 and 10, assumption that taxi will take about 2-10 seconds for picking up the order.
        int delay = MIN_DELAY_FOR_PICKUP_IN_SECS + random.nextInt(MAX_DELAY_FOR_PICKUP_IN_SECS - MIN_DELAY_FOR_PICKUP_IN_SECS + 1);
        return SharedExecutorService.getScheduledExecutorService().schedule(() -> {
            Order order = shelfPod.pollOrder();
            return order;
        }, delay, TimeUnit.SECONDS);
    }
}
