package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.common.ExecutorServicesUtil;
import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.DelayedOrder;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.cloudkitchens.fulfillment.entities.shelves.observers.IShelfPodObserver;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final Map<Temperature, BlockingQueue<Order>> watchQueuesForMovableOrders;
    // This is a delay queue which maintains all the orders which are not delivered, so a thread can wait on this delay queue, and expire
    // the orders when the delay queue returns an order due to the order reached zero shelf time.
    private final BlockingQueue<DelayedOrder> watchQueueForExpirableOrders;
    // This stores all the changes that are happening on all the shelves. This is read by a separate thread and updates #watchQueueForExpirableOrders and #watchQueuesForMovableOrders
    private final BlockingQueue<OrderAndShelfOperation> updatesQueue;

    /**
     * We need to store observers in a threadsafe list, so the same list can be used for notifying them while at the same time
     * they are updated/removed with observers. So using thread-safe queue here. Also this will avoid any concurrent modification exception.
     */
    private final Queue<IShelfPodObserver> observers;

    private volatile ExecutorService executorService;

    public ShelfPod(List<Shelf> shelves) {
        super(shelves);
        this.watchQueuesForMovableOrders = createWatchQueuesForMovableOrders(new OrderExpiryComparator(getDecayRateFactors(shelves)));
        this.watchQueueForExpirableOrders = new DelayQueue<>();
        this.updatesQueue = new LinkedBlockingQueue<>();
        this.observers = new ConcurrentLinkedQueue<>();
    }

    /**
     * Starting background threads separately(not part of constructor initialization) to avoid partial visibility of the objects to threads
     * which may cause race conditions.
     */
    public void startBackgroundActivities() {
        executorService = ExecutorServicesUtil.createFixedThreadPool("shelf-pod-thread-", 10, 30);
        for (Temperature temperature : Temperature.getRegularShelves()) {
            MoverThread moverThread = new MoverThread(watchQueuesForMovableOrders.get(temperature), temperature);
            executorService.submit(moverThread);
        }
        executorService.submit(new MarkExpiredThread());
        executorService.submit(new ShelfUpdatesReaderThread());
    }

    public void stopBackgroundActivities() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private Map<Temperature, BlockingQueue<Order>> createWatchQueuesForMovableOrders(Comparator<Order> orderExpiryComparator) {
        Map<Temperature, BlockingQueue<Order>> regularShelvesQueues = new HashMap<>();
        for (Temperature temperature : Temperature.getRegularShelves()) {
            regularShelvesQueues.put(temperature, new PriorityBlockingQueue<>(16, orderExpiryComparator));
        }
        return ImmutableMap.copyOf(regularShelvesQueues);
    }

    /**
     * Just overrides base class's function, and records the add event into local queue for further processing by other threads.
     *
     * @param order
     * @return
     */
    @Override public AddResult addOrder(Order order) {
        AddResult addResult = super.addOrder(order);
        if (addResult.isAdded()) {
            updatesQueue.add(new OrderAndShelfOperation(order, ShelfOperation.Add));
        }
        notifyObserversAddition(order, addResult);
        return addResult;
    }

    /**
     * Just overrides base class's function, and records the add event into local queue for further processing by other threads.
     *
     * @param order
     * @return
     */
    @Override protected AddResult moveOrder(Order order) {
        AddResult addResult = super.moveOrder(order);
        if (addResult.isAdded()) {
            updatesQueue.add(new OrderAndShelfOperation(order, ShelfOperation.Move));
        }
        return addResult;
    }

    /**
     * Just overrides base class's function, and records the remove event into local queue for further processing by other threads.
     *
     * @param order
     * @return
     */
    @Override protected boolean removeOrder(Order order) {
        boolean removed = super.removeOrder(order);
        if (removed) {
            updatesQueue.add(new OrderAndShelfOperation(order, ShelfOperation.Remove));
        }
        return removed;
    }

    /**
     * Just overrides base class's function, and records the expire event into local queue for further processing by other threads.
     *
     * @param order
     * @return
     */
    @Override protected boolean expireOrder(Order order) {
        boolean expired = super.expireOrder(order);
        if (expired) {
            updatesQueue.add(new OrderAndShelfOperation(order, ShelfOperation.Expire));
        }
        return expired;
    }

    /**
     * Just overrides base class's function, and records the poll event into local queue for further processing by other threads.
     *
     * @return
     */
    @Override public Order pollOrder() {
        Order order = super.pollOrder();
        if (order != null) {
            updatesQueue.add(new OrderAndShelfOperation(order, ShelfOperation.Poll));
        }
        return order;
    }


    @Override public boolean addObserver(IShelfPodObserver shelfPodObserver) {
        return observers.add(shelfPodObserver);
    }

    @Override public boolean removeObserver(IShelfPodObserver shelfPodObserver) {
        return observers.remove(shelfPodObserver);
    }

    private void notifyObserversAddition(Order order, AddResult addResult) {
        for (IShelfPodObserver observer : observers) {
            observer.postAddOrder(order, addResult);
        }
    }

    /**
     * This thread reads all the operations that happened on the shelf from a queue, and feeds that information into {@link MoverThread}'s queue and
     * {@link MarkExpiredThread}'s queue.
     */
    private class ShelfUpdatesReaderThread implements Runnable {

        @Override public void run() {
            while (true) {
                try {
                    OrderAndShelfOperation orderAndShelfOperation = updatesQueue.take();
                    updateMoverThreadQueue(orderAndShelfOperation);
                    updateExpireThreadQueue(orderAndShelfOperation);
                } catch (InterruptedException e) {
                    //If the thread is interrupted, lets not proceed further this task.
                    return;
                }
            }
        }

        /**
         * MarkExpiredThread watches all orders and expires them if it finds any. If an order gets delivered or moves between shelves,
         * then that thread's queue has to be updated with this information. This function takes care of that.
         *
         * @param orderAndShelfOperation
         */
        private void updateExpireThreadQueue(OrderAndShelfOperation orderAndShelfOperation) {
            DelayedOrder delayedOrder = new DelayedOrder(orderAndShelfOperation.order, decayRateFactors);

            switch (orderAndShelfOperation.shelfOperation) {
                case Add:
                case Move:
                    watchQueueForExpirableOrders.add(delayedOrder);
                    break;
                case Remove:
                case Poll:
                    watchQueueForExpirableOrders.remove(delayedOrder);
                    break;
                case Expire:
                    // If the order is already expired, then we are not interested in the order anymore for watching.
                    // no operation on this
                    break;
            }
        }

        /**
         * MoverThread watches for any order that has to be moved from OverflowShelf too RegularShelf.
         * If an order gets delivered or expires, then that thread's queue has to be updated with this information.
         * This function takes care of that.
         *
         * @param orderAndShelfOperation
         */
        private void updateMoverThreadQueue(OrderAndShelfOperation orderAndShelfOperation) {

            Order order = orderAndShelfOperation.order;
            switch (orderAndShelfOperation.shelfOperation) {
                case Add:
                    if (order.getOrderState() == OrderState.StoredInOverflowShelf) {
                        watchQueuesForMovableOrders.get(order.getTemperature()).add(order);
                    }
                    break;
                case Move:
                case Remove:
                    // Move thread handles move and remove operations, so we dont need to handle this.
                    break;
                case Expire:
                case Poll:
                    if (order.getOrderState() == OrderState.ExpiredInOverflowShelf
                        || order.getOrderState() == OrderState.DeliveredFromOverflowShelf) {
                        watchQueuesForMovableOrders.get(order.getTemperature()).remove(order);
                    }
                    break;
            }
        }
    }


    /**
     * This thread moves overflow orders to regular shelves. A thread needs to be created for each (OverflowShelf, RegularShelf) combination.
     * <p>
     */
    private class MoverThread implements Runnable {

        private final BlockingQueue<Order> orders;
        private final Temperature movingTo;

        public MoverThread(BlockingQueue<Order> orders, Temperature movingTo) {
            this.orders = orders;
            this.movingTo = movingTo;
        }

        @Override public void run() {
            log.info("Launching moverThread for moving overflow shelfPod items to shelfType={}", movingTo);
            while (true) {
                try {
                    //Both take() and moveOrder(order) are blocking calls. So this thread will be suspended if there are no orders to take
                    // or no space available on the regular shelf to move the order. So we will save the system resources using this approach.
                    Order order = orders.take();
                    if (!order.hasReachedEndState()) {
                        AddResult moveResult = moveOrder(order);
                        if (moveResult.isAdded()) {
                            log.info("Moved order to the regular shelf, order={}", order);
                        }
                    }
                } catch (InterruptedException e) {
                    //If the thread is interrupted, lets not proceed further this task.
                    return;
                }
            }
        }
    }


    /**
     * This thread watches expirable orders queue. {@link DelayQueue} is used for expirable queue, which allows an element to be removed only after the delay becomes zero.
     * If the queue returns an order, then this thread marks that order as expired.
     * Since delayQueue is a blocking queue, if the thread does not have
     */
    private class MarkExpiredThread implements Runnable {

        @Override public void run() {
            log.info("Launching mark expiring thread.");
            while (true) {
                try {
                    DelayedOrder delayedOrder = watchQueueForExpirableOrders.take();
                    Order order = delayedOrder.getOrder();
                    if (!order.hasReachedEndState()) {
                        boolean expired = expireOrder(order);
                        if (expired) {
                            log.info("Expired order from shelf, order={}", order);
                        }
                    }
                } catch (InterruptedException e) {
                    //If the thread is interrupted, lets not proceed further this task.
                    return;
                }
            }
        }
    }


    private static class OrderAndShelfOperation {
        private final Order order;
        private final ShelfOperation shelfOperation;

        public OrderAndShelfOperation(Order order, ShelfOperation shelfOperation) {
            this.order = order;
            this.shelfOperation = shelfOperation;
        }
    }
}
