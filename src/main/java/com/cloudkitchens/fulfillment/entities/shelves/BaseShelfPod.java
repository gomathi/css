package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.cloudkitchens.fulfillment.entities.shelves.observers.IShelfPodObserver;
import com.cloudkitchens.fulfillment.entities.shelves.util.ShelfUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of IShelfPod, this manages group of shelves. If there is an overflow shelf, that will be used for
 * regular shelves in case if the regular shelves dont have any space to store an order.
 * <p>
 * In case if CloudKitchens is growing much fast, we can spawn multiple instances of this class and handle load balancing
 * across multiple {@link IShelfPod}
 *
 * <p>
 * The following are the key functions supported
 * <p>
 * 1. {@link #addOrder(Order)}, non blocking call
 * 2. {@link #moveOrder(Order)}, blocking call
 * 3. {@link #removeOrder(Order)}, non blocking call
 * 4. {@link #pollOrder()}, non blocking call
 * <p>
 * <p>
 * Internally one queue(priority queue, priority is calculated by order's shelf time) is used as shelf
 * and that is shared across multiple shelves. The amount of orders that each shelf can add
 * is controlled by each shelf's capacity. There is a semaphore associated with each shelf.
 * Any addition/removal to the queue is controlled by these semaphores.
 * <p>
 * Sharing one queue across all shelves helps in reducing kitchen waste. If we have separate queues for each shelf,
 * then pickup wont be shared across multiple shelves, so the order which will expire sooner across multiple shelves
 * wont be delivered first. So this design chose to share one queue to deliver the order
 * which will expire sooner across multiple shelves.
 */

@Slf4j @ThreadSafe public class BaseShelfPod implements IShelfPod {

    private static final Map<Temperature, Map<ShelfOrderState, OrderState>> STORED_AND_EXPIRED_ORDER_STATE_NAMES =
        getStoredAndExpiredOrderStateNames();

    private final List<Shelf> shelves;
    private final Map<Temperature, Shelf> tempShelfInfoMap;
    private final Map<Temperature, Double> decayRateFactors;
    private final Comparator<Order> orderExpiryComparator;

    /**
     * Java doesn't have any bounded threadsafe priority queue. Given that the shelf size is finite, we want to have bounded priority queue.
     * So using semaphores to add max bound to priority queue. Any addition/removal operations to the queue should have corresponding
     * acquire/release semaphore operations respectively.
     */
    private final BlockingQueue<Order> ordersQueue;
    // A semaphore is associated with different kinds of shelves. Any order addition/removal from the shelf is controlled through these semaphores.
    private final Map<Temperature, Semaphore> spaces;

    @Inject public BaseShelfPod(@Assisted List<Shelf> shelves) {
        this.shelves = ImmutableList.copyOf(shelves);
        this.tempShelfInfoMap = ShelfUtils.getTempShelfInfoMap(shelves);
        this.decayRateFactors = ShelfUtils.getDecayRateFactors(shelves);
        this.orderExpiryComparator = new OrderExpiryComparator(decayRateFactors);
        this.ordersQueue = new PriorityBlockingQueue<>(16, orderExpiryComparator);
        this.spaces = createSpaces(shelves);
    }

    private static Map<Temperature, Semaphore> createSpaces(List<Shelf> shelves) {
        Map<Temperature, Semaphore> spaces = new HashMap<>();
        for (Shelf shelf : shelves) {
            spaces.put(shelf.getTemperature(), new Semaphore(shelf.getCapacity(), true));
        }
        return ImmutableMap.copyOf(spaces);
    }

    private double getDecayRate(Order order) {
        return order.getOrderState() == OrderState.StoredInOverflowShelf ?
            decayRateFactors.get(Temperature.Overflow) :
            decayRateFactors.get(order.getTemperature());
    }

    private double getDecayRate(Temperature shelfType) {
        return decayRateFactors.get(shelfType);
    }

    /**
     * Returns one of the shelf types of value {@link Temperature}  given the order.
     * <p>
     * An order's state can be only {@link OrderState#StoredInRegularShelf} or {@link OrderState#StoredInOverflowShelf}
     * if its stored in one of the shelves. An order's state should be one of the above before adding an order into the shelf.
     * Additionally, only after removing the order from shelf, the state can changed to expiry or delivered states.
     * <p>
     * The above contract is critical for maintaining the correctness of the shelf.
     *
     * @param order
     * @return
     * @throws IllegalStateException if the order's {@link OrderState} is not in
     *                               {{@link OrderState#StoredInRegularShelf}, {@link OrderState#StoredInOverflowShelf}}
     */
    private static Temperature getShelfTypeWhileInsideShelf(Order order) {
        if (order.getOrderState() == OrderState.StoredInRegularShelf)
            return order.getTemperature();
        else if (order.getOrderState() == OrderState.StoredInOverflowShelf)
            return Temperature.Overflow;
        throw new IllegalStateException("Given order's state is not in a valid condition.");
    }

    @Override public List<Shelf> getShelves() {
        return shelves;
    }

    /**
     * An order when its stored it may be stored in regular shelf or overflow shelf. Also it could be expired from regular shelf or overflow shelf.
     * Recording this like {@link OrderState#StoredInRegularShelf}, @link OrderState#ExpiredInRegularShelf} will help analyzing the orders and
     * shelves in the future.
     * <p>
     * This function prepares a map containing those states for different shelves for lookup purposes.
     *
     * @return
     */
    private static Map<Temperature, Map<ShelfOrderState, OrderState>> getStoredAndExpiredOrderStateNames() {
        Map<Temperature, Map<ShelfOrderState, OrderState>> storedAndExpiredOrderStateNames = new HashMap<>();
        Set<Temperature> regularShelves =
            Arrays.asList(Temperature.values()).stream().filter(temp -> temp != Temperature.Overflow).collect(Collectors.toSet());
        for (Temperature temperature : Temperature.values()) {
            Map<ShelfOrderState, OrderState> shelfMap = new HashMap<>();
            for (ShelfOrderState shelfOrderState : ShelfOrderState.values()) {
                if (regularShelves.contains(temperature)) {
                    shelfMap.put(shelfOrderState,
                        shelfOrderState == ShelfOrderState.Stored ? OrderState.StoredInRegularShelf : OrderState.ExpiredInRegularShelf);
                } else {
                    shelfMap.put(shelfOrderState,
                        shelfOrderState == ShelfOrderState.Stored ? OrderState.StoredInOverflowShelf : OrderState.ExpiredInOverflowShelf);
                }
            }
            storedAndExpiredOrderStateNames.put(temperature, ImmutableMap.copyOf(shelfMap));
        }
        return ImmutableMap.copyOf(storedAndExpiredOrderStateNames);
    }

    /**
     * Returns one of the following values from [{@link OrderState#StoredInRegularShelf},{@link OrderState#StoredInOverflowShelf},
     * {@link OrderState#ExpiredInRegularShelf}, {@link OrderState#ExpiredInOverflowShelf}] based on the parameters.
     * <p>
     * The returned value is used to track from where the items are expired, or which shelf is used for storing most often.
     *
     * @param shelfType
     * @param shelfOrderState
     * @return
     */
    private OrderState getOrderState(Temperature shelfType, ShelfOrderState shelfOrderState) {
        return STORED_AND_EXPIRED_ORDER_STATE_NAMES.get(shelfType).get(shelfOrderState);
    }

    /**
     * This function supports {@link #addOrder(Order)} and {@link #moveOrder(Order)} functions.
     * Parameters control whether it provides support for {@link #addOrder(Order)} or {@link #moveOrder(Order)}.
     * <p>
     * If prevState is {@link OrderState#StoredInOverflowShelf}, then function acts as a move function.
     * Otherwise, this function acts as an add function.
     * <p>
     * prevState can only be {@link OrderState#Created}, {@link OrderState#StoredInOverflowShelf}
     *
     * @param order
     * @param prevState
     * @param storeInOverflowShelf
     * @return
     */
    private AddResult addOrder(Order order, OrderState prevState, boolean storeInOverflowShelf) {
        Temperature shelfType = storeInOverflowShelf ? Temperature.Overflow : order.getTemperature();
        Semaphore shelfSpaces = spaces.get(shelfType);

        boolean added = false;
        boolean spaceAcquired = false;
        try {
            if (prevState == OrderState.Created && order.hasExpired(getDecayRate(shelfType))) {
                order.setOrderState(OrderState.CameExpired);
            } else {
                if (prevState == OrderState.StoredInOverflowShelf) {
                    shelfSpaces.acquire();
                    spaceAcquired = true;
                } else {
                    // tryAcquire() method does not guarantee fairness, but tryacquire(timeout, timeunit) guarantees fairness.
                    // So lets get acquire the space 0 seconds as the timeout
                    spaceAcquired = shelfSpaces.tryAcquire(0, TimeUnit.SECONDS);
                }
                if (spaceAcquired) {
                    boolean removedInOverflow = (prevState == OrderState.StoredInOverflowShelf) ? removeOrder(order) : true;
                    if (removedInOverflow && order.compareAndSet(prevState, getOrderState(shelfType, ShelfOrderState.Stored))) {
                        if (prevState == OrderState.StoredInOverflowShelf) {
                            // If an order stayed in overflow shelf, then we need to record it as in the overflow shelf
                            // orders decay faster, we need to account that for expiry time calculation in regular shelf.
                            order.setTimeSpentOnOverflowShelfInMs(System.currentTimeMillis() - order.getCreatedTimestamp());
                        }
                        ordersQueue.add(order);
                        added = true;
                    } else {
                        shelfSpaces.release();
                    }
                } else {
                    // If there are no spaces available in Overflow shelf, then lets mark it as expired.
                    if (storeInOverflowShelf) {
                        order.setOrderState(OrderState.ExpiredOnNoSpace);
                    }
                }
            }
        } catch (InterruptedException e) {
            // This should rarely happen as we are trying to acquired with 0 seconds delay.
            log.error("Interrupted while adding order to the queue, orderId={}", order.getId());
            if (spaceAcquired) {
                // If an exception happens before adding an Order to the queue, then lets make sure
                // we release the space back to the queue.
                shelfSpaces.release();
            }
        }
        return new AddResult(added, order.getOrderState(), tempShelfInfoMap.get(shelfType));
    }

    /**
     * Adds the given order to any non overflow shelf that is appropriate for the given Order's Temperature.
     * If there is no available space in the non overflow shelf, then add attempt is tried on the overflow shelf,
     * and returns the result of the addition.
     * <p>
     * Running time complexity is O(log N) as its priority queue.
     *
     * @param order
     * @return addResult, whether the add was successful or not, orderstate at the end of add operation, the shelf that was attempted for hosting the order.
     */
    @Override public AddResult addOrder(Order order) {
        log.info("Adding orderId={}", order.getId());
        AddResult addResult = addOrder(order, OrderState.Created, false);
        if (!addResult.isAdded()) {
            addResult = addOrder(order, OrderState.Created, true);
        }
        log.info("Adding orderId={} addResult={} - done.", order.getId(), addResult);
        return addResult;
    }

    /**
     * Functionally {@link #addOrder(Order)} and {@link #moveOrder(Order)} both store the order to IShelfPod.
     * But addOrder is triggered through external services like KitchenService, where as moveOrder is triggered
     * through the internal IShelfPod maintaining threads.
     * <p>
     * One more difference is {@link #addOrder(Order)} is non blocking call and {@link #moveOrder(Order)} is a blocking call.
     * This is required as overflow shelf needs to move an order from itself to regular shelf if the space is available.
     * <p>
     * Worst case running time complexity of this method is O(N), as this may trigger removing an element from the shelf.
     * Since this method is called from a background thread, this should not impact any online orders addition or pickup service.
     *
     * @param order
     * @return true if the order is successfully added into the shelf, otherwise false.
     */
    protected AddResult moveOrder(Order order) {
        log.info("Moving order to orderId={}", order.getId());
        AddResult moveResult = addOrder(order, OrderState.StoredInOverflowShelf, false);
        log.info("Moving order to orderId={} moveResult={} - done.", order.getId(), moveResult);
        return moveResult;
    }

    /**
     * @param order
     * @return
     */
    protected boolean removeOrder(Order order) {
        boolean removed = ordersQueue.remove(order);
        if (removed) {
            Temperature shelfType = getShelfTypeWhileInsideShelf(order);
            spaces.get(shelfType).release();
            log.info("Removed order, and orderId={}", order.getId());
        }
        return removed;
    }

    /**
     * This function returns an order for a pickup service to deliver it. An order can expire while on the shelf,
     * so this function makes sure the order is valid before returning it to the caller.
     * <p>
     *
     * @return an order if the order is available on the shelf and is not expired, otherwise null.
     */
    @Override public Order pollOrder() {
        while (true) {
            Order order = ordersQueue.poll();
            if (order == null)
                return null;
            Temperature shelfType = getShelfTypeWhileInsideShelf(order);
            spaces.get(shelfType).release();
            if (order.hasExpired(getDecayRate(order))) {
                order.setOrderState(getOrderState(shelfType, ShelfOrderState.Expired));
                continue;
            }
            order.setOrderState(OrderState.PickedUpForDelivery);
            return order;
        }
    }

    /**
     * This function guarantees the orders are immediately visible after any add/remove operation is executed.
     *
     * @return
     */
    @Override public List<Order> getOrders() {
        List<Order> orders = new ArrayList<>(ordersQueue);
        // Since the queue provides weak iterator, we may have got orders which were already delivered, or expired.
        // So we make a copy and filter only the orders that are currently in the shelf.
        orders = orders.stream().map(order -> ((Order) order).getDeepCopy()).filter(order -> order.isCurrentlyInAnyShelf())
            .collect(Collectors.toList());
        Collections.sort(orders, orderExpiryComparator);
        return orders;
    }

    @Override public boolean addObserver(IShelfPodObserver shelfPodObserver) {
        return false;
    }

    @Override public boolean removeObserver(IShelfPodObserver shelfPodObserver) {
        return false;
    }
}
