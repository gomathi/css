package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.common.ExecutorServicesUtil;
import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.cloudkitchens.fulfillment.entities.shelves.util.ShelfUtils;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * This class extends {@link BaseShelfPod} and adds two additional critical functions to IShelfPod.
 * <p>
 * <p>
 * 1) ShelfPod has to move orders from overflow shelf to suitable regular shelf if there are spaces available in regular shelf.
 * Moving back to the regular shelf in a timely manner is very critical to well functioning fulfillment service.
 * <p>
 * Example: Say a HotShelf is full while OverflowShelf is full of cold orders.
 * Assume regular ColdShelf has been emptied up. In this case, any new Hot order will get expired,
 * as there are no spaces available in the HotShelf also OverflowShelf is full. Moving back cold items from
 * OverflowShelf to ColdShelf will provide fulfillment service to store hot items in the OverflowShelf.
 * <p>
 * Look at implementation {@link MoverThread} to know how the mover thread handles moving orders from OverflowShelf to Regular shelves.
 * <p>
 * <p>
 * 2) We need to remove orders as soon as they expire from all the shelves as it will reduce the kitchen waste. For example, overflow orders decay faster than
 * regular shelves, so if there are expired orders on the regular shelves, removing them will help overflow orders movement to regular shelves, and in turn
 * the orders will stay fresh longer, and in turn they may get picked up by the delivery service.
 */
@Slf4j public class ShelfPod extends BaseShelfPod {

    // Whenever a regular order is stored in overflow shelf, we need to keep track of them, so that they can be moved back to regular shelf
    // when there is space availability in regular shelf. The following map maintains separate queue for each regular shelf which will just
    // keep track of regular orders that are stored in overflow shelf.
    private final Map<Temperature, BlockingQueue<Order>> regularShelvesQueuesForOverflowItems;

    @Inject public ShelfPod(@Assisted List<Shelf> shelves) {
        super(shelves);
        this.regularShelvesQueuesForOverflowItems =
            createBlockingQueuesForRegularShelves(new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelves)));
    }

    /**
     * Starting background threads separately(not part of constructor initialization) to avoid partial visibility of the objects to threads
     * which may cause race conditions.
     */
    public void startBackgroundActivities() {
        for (Temperature temperature : Temperature.values()) {
            if (temperature == Temperature.Overflow)
                continue;
            MoverThread moverThread = new MoverThread(regularShelvesQueuesForOverflowItems.get(temperature), this, temperature);
            ExecutorServicesUtil.getSharedExecutorService().submit(moverThread);
        }
    }

    private Map<Temperature, BlockingQueue<Order>> createBlockingQueuesForRegularShelves(Comparator<Order> orderExpiryComparator) {
        Map<Temperature, BlockingQueue<Order>> regularShelvesQueues = new HashMap<>();
        for (Temperature temperature : Temperature.values()) {
            if (temperature == Temperature.Overflow)
                continue;
            regularShelvesQueues.put(temperature, new PriorityBlockingQueue<>(16, orderExpiryComparator));
        }
        return ImmutableMap.copyOf(regularShelvesQueues);
    }

    @Override public AddResult addOrder(Order order) {
        AddResult addResult = super.addOrder(order);
        if (addResult.isAdded() && addResult.getShelf().getTemperature() == Temperature.Overflow) {
            regularShelvesQueuesForOverflowItems.get(order.getTemperature()).add(order);
        }
        return addResult;
    }

    /**
     * This thread moves overflow orders to regular shelves. A thread needs to be created for each (OverflowShelf, RegularShelf) combination.
     * <p>
     */
    private static class MoverThread implements Runnable {

        private final BlockingQueue<Order> orders;
        private final ShelfPod shelfPod;
        private final Temperature movingTo;

        public MoverThread(BlockingQueue<Order> orders, ShelfPod shelfPod, Temperature movingTo) {
            this.orders = orders;
            this.shelfPod = shelfPod;
            this.movingTo = movingTo;
        }

        @Override public void run() {
            log.info("Launching moverThread for moving overflow shelfPod items to shelfType={}", movingTo);
            while (true) {
                try {
                    //Both take() and moveOrder(order) are blocking calls. So this thread will be suspended if there are no orders to take
                    // or no space available to move the order. So we will save the system resources using this approach.
                    Order order = orders.take();
                    if (!order.hasReachedEndState()) {
                        shelfPod.moveOrder(order);
                    }
                } catch (InterruptedException e) {
                    log.warn("Current thread is interrupted. Stopping mover thread activities further.");
                    return;
                }
            }
        }
    }
}
