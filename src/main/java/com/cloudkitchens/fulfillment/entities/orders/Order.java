package com.cloudkitchens.fulfillment.entities.orders;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains order information, order state information, and shelf value of the order.
 */
@ThreadSafe @Slf4j public class Order {

    private static final Set<OrderState> END_STATES = ImmutableSet
        .of(OrderState.ExpiredInRegularShelf, OrderState.ExpiredInOverflowShelf, OrderState.ExpiredOnNoSpace, OrderState.CameExpired,
            OrderState.DeliveredFromRegularShelf, OrderState.DeliveredFromOverflowShelf);

    private final String name;
    private final Temperature temperature;
    private final int shelfLifeInSecs;
    private final double decayRate;
    private final String id;
    private final long createdTimestampInMs;
    private final AtomicReference<OrderState> orderStateAtomicReference;
    private volatile long timeSpentOnOverflowShelfInMs;

    public Order(String id, String name, Temperature temperature, int shelfLifeInSecs, double decayRate) {
        this(id, name, temperature, shelfLifeInSecs, decayRate, OrderState.Created, 0l);
    }

    private Order(String id, String name, Temperature temperature, int shelfLifeInSecs, double decayRate, OrderState orderState,
        long timeSpentOnOverflowShelfInMs) {
        this.id = id;
        this.name = name;
        this.temperature = temperature;
        this.shelfLifeInSecs = shelfLifeInSecs;
        this.decayRate = decayRate;
        this.createdTimestampInMs = System.currentTimeMillis();
        this.orderStateAtomicReference = new AtomicReference<>(orderState);
        this.timeSpentOnOverflowShelfInMs = timeSpentOnOverflowShelfInMs;
    }

    public Order getDeepCopy() {
        return new Order(id, name, temperature, shelfLifeInSecs, decayRate, orderStateAtomicReference.get(), timeSpentOnOverflowShelfInMs);
    }

    /**
     * Gets the name of the order.
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the kind of the order.
     *
     * @return
     */
    public Temperature getTemperature() {
        return temperature;
    }

    /**
     * Returns the shelf life in seconds.
     *
     * @return
     */
    public int getShelfLifeInSecs() {
        return shelfLifeInSecs;
    }


    /**
     * Each order has an associated decayRate which controls the shelf life in a shelf.
     *
     * @return
     */
    public double getDecayRate() {
        return decayRate;
    }

    /**
     * Returns an identifier to track the order.
     *
     * @return
     */
    public String getId() {
        return id;
    }

    /**
     * Timestamp at which a kitchen has sent an order to shelf for delivery.
     * This timestamp is used in calculating shelf life value also will be available .
     *
     * @return timestamp in milliseconds
     */
    public long getCreatedTimestamp() {
        return createdTimestampInMs;
    }

    /**
     * Provides the shelf life value of this order. A value zero or less than zero indicates this order is expired.
     * The unit of returned value is in milliseconds.
     *
     * <p>
     *
     * @param decayRateFactor, decayRateFactor determines how fast the order will lose its value in a shelf(depending upon the shelf type).
     *                         A value of 2 means the order will decay 2 times faster. This parameter is passed by the IShelfPod which stores this
     *                         Order.
     * @return
     */
    public long getCurrShelfValueInMs(double decayRateFactor) {
        long orderAgeInMs = System.currentTimeMillis() - createdTimestampInMs;
        long decayedOrderAgeInMs = new Double(decayRate * decayRateFactor * orderAgeInMs).longValue();
        long shelfValue = (shelfLifeInSecs * 1000 - orderAgeInMs) - decayedOrderAgeInMs;
        return shelfValue;
    }

    /**
     * Indicates whether the order is expired or not.
     * <p>
     *
     * @param decayRateFactor
     * @return
     */
    public boolean hasExpired(double decayRateFactor) {
        long expiredTime = getExpiryTimestampInMs(decayRateFactor);
        return (expiredTime - System.currentTimeMillis()) <= 0;
    }

    /**
     * Returns the normalized value of each order in the shelf.
     *
     * @param decayRateFactor
     * @return
     */
    public double getNormalizedValue(double decayRateFactor) {
        double result = getCurrShelfValueInMs(decayRateFactor) / getShelfLifeInSecs() / 1000.0;
        return result;
    }

    /**
     * Gets the expiry timestamp, this will change specific to each shelf.
     *
     * @param decayRateFactor
     * @return
     */
    public long getExpiryTimestampInMs(double decayRateFactor) {
        return getCreatedTimestamp() + getCurrShelfValueInMs(decayRateFactor) - getTimeSpentOnOverflowShelfInMs();
    }

    /**
     * Gets the orderState of the given order.
     *
     * @return
     */
    public OrderState getOrderState() {
        return orderStateAtomicReference.get();
    }

    /**
     * Sets the orderState to the given value.
     *
     * @param orderState
     */
    public void setOrderState(OrderState orderState) {
        orderStateAtomicReference.set(orderState);
    }

    /**
     * Sets the current orderState to newState if the oldState matches.
     *
     * @param oldState
     * @param newState
     * @return
     */
    public boolean compareAndSet(OrderState oldState, OrderState newState) {
        return orderStateAtomicReference.compareAndSet(oldState, newState);
    }

    /**
     * If the order is reached one of the stages {Expired, PickedUp} then this value returns true, otherwise false.
     *
     * @return
     */
    public boolean hasReachedEndState() {
        return END_STATES.contains(getOrderState());
    }

    /**
     * If the order is currently stored in any of the regular shelf or overflow shelf, then this returns true.
     * Otherwise returns false.
     *
     * @return
     */
    public boolean isCurrentlyInAnyShelf() {
        return getOrderState() == OrderState.StoredInRegularShelf || getOrderState() == OrderState.StoredInOverflowShelf;
    }

    /**
     * If an order stays in overflow shelf for a period of time, and then if it moves back to regular shelf, then we need to account the time
     * spent on overflow shelf, and subtract that value from shelf value of this order. This function provides the time spent on the
     * overflow shelf.
     *
     * @return
     */
    public long getTimeSpentOnOverflowShelfInMs() {
        return timeSpentOnOverflowShelfInMs;
    }

    /**
     * If an order stays in overflow shelf for a period of time, and then if it moves back to regular shelf, then we need to account the time
     * spent on overflow shelf, and subtract that value from shelf value of this order. This provides the ability to set that value.
     *
     * @param timeSpentOnOverflowShelfInMs
     */
    public void setTimeSpentOnOverflowShelfInMs(long timeSpentOnOverflowShelfInMs) {
        this.timeSpentOnOverflowShelfInMs = timeSpentOnOverflowShelfInMs;
    }

    public int hashCode() {
        return Objects.hash(name, temperature, shelfLifeInSecs, id);
    }

    public boolean equals(Object other) {
        if (other == this)
            return true;
        if (!(other instanceof Order))
            return false;
        Order that = (Order) other;
        return Objects.equals(name, this.name) && Objects.equals(this.temperature, that.temperature) && Objects
            .equals(this.shelfLifeInSecs, that.shelfLifeInSecs) && Objects.equals(id, that.id);
    }

    public String toString() {
        return MoreObjects.toStringHelper(Order.class).add("orderId", id).add("name", name).add("temperature", temperature)
            .add("shelfLifeInSecs", shelfLifeInSecs).add("decayRate", decayRate).add("orderState", getOrderState()).toString();
    }
}
