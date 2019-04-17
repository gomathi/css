package com.cloudkitchens.fulfillment.entities.orders;

import com.cloudkitchens.fulfillment.entities.Temp;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@ThreadSafe @Slf4j public class OrderImpl implements Order {

    private static final Set<OrderState> END_STATES = ImmutableSet
        .of(OrderState.ExpiredInRegularShelf, OrderState.ExpiredInOverflowShelf, OrderState.ExpiredOnNoSpace, OrderState.CameExpired,
            OrderState.PickedUpForDelivery);

    private final String name;
    private final Temp temp;
    private final int shelfLifeInSecs;
    private final double decayRate;
    private final String id;
    private final long createdTimestampInMs;
    private final AtomicReference<OrderState> orderStateAtomicReference;
    private volatile long timeSpentOnOverflowShelfInMs;

    public OrderImpl(String id, String name, Temp temp, int shelfLifeInSecs, double decayRate) {
        this(id, name, temp, shelfLifeInSecs, decayRate, OrderState.Created, 0l);
    }

    private OrderImpl(String id, String name, Temp temp, int shelfLifeInSecs, double decayRate, OrderState orderState,
        long timeSpentOnOverflowShelfInMs) {
        this.id = id;
        this.name = name;
        this.temp = temp;
        this.shelfLifeInSecs = shelfLifeInSecs;
        this.decayRate = decayRate;
        this.createdTimestampInMs = System.currentTimeMillis();
        this.orderStateAtomicReference = new AtomicReference<>(orderState);
        this.timeSpentOnOverflowShelfInMs = timeSpentOnOverflowShelfInMs;
    }

    public OrderImpl getDeepCopy() {
        return new OrderImpl(id, name, temp, shelfLifeInSecs, decayRate, orderStateAtomicReference.get(), timeSpentOnOverflowShelfInMs);
    }

    @Override public String getName() {
        return name;
    }

    @Override public Temp getTemp() {
        return temp;
    }

    @Override public int getShelfLifeInSecs() {
        return shelfLifeInSecs;
    }

    @Override public double getDecayRate() {
        return decayRate;
    }

    @Override public String getId() {
        return id;
    }

    @Override public long getCreatedTimestamp() {
        return createdTimestampInMs;
    }

    @Override public long getCurrShelfValueInMs(double decayRateFactor) {
        long orderAgeInMs = System.currentTimeMillis() - createdTimestampInMs;
        double value = (shelfLifeInSecs * 1000 - orderAgeInMs) - (decayRate * decayRateFactor * orderAgeInMs);
        return new Double(value).longValue();
    }

    @Override public boolean isExpired(double decayRateFactor) {
        long expiredTime = getCreatedTimestamp() + getCurrShelfValueInMs(decayRateFactor);
        return (expiredTime - System.currentTimeMillis()) <= 0;
    }

    @Override public long getNormalizedValue(double decayRateFactor) {
        return getCurrShelfValueInMs(decayRateFactor) / getShelfLifeInSecs() / 1000;
    }

    @Override public long getExpiryTimestampInMs(double decayRateFactor) {
        return getCreatedTimestamp() + getCurrShelfValueInMs(decayRateFactor) - getTimeSpentOnOverflowShelfInMs();
    }


    @Override public OrderState getOrderState() {
        return orderStateAtomicReference.get();
    }

    @Override public void setOrderState(OrderState orderState) {
        orderStateAtomicReference.set(orderState);
    }

    @Override public boolean compareAndSet(OrderState oldState, OrderState newState) {
        return orderStateAtomicReference.compareAndSet(oldState, newState);
    }

    @Override public boolean isReachedEndState() {
        return END_STATES.contains(getOrderState());
    }

    @Override public boolean isInCurrentlyInAnyShelf() {
        return getOrderState() == OrderState.StoredInRegularShelf || getOrderState() == OrderState.StoredInOverflowShelf;
    }

    @Override public long getTimeSpentOnOverflowShelfInMs() {
        return timeSpentOnOverflowShelfInMs;
    }

    @Override public void setTimeSpentOnOverflowShelfInMs(long timeSpentOnOverflowShelfInMs) {
        this.timeSpentOnOverflowShelfInMs = timeSpentOnOverflowShelfInMs;
    }

    @Override public int hashCode() {
        return Objects.hash(name, temp, shelfLifeInSecs, id);
    }

    @Override public boolean equals(Object other) {
        if (other == this)
            return true;
        if (!(other instanceof OrderImpl))
            return false;
        OrderImpl that = (OrderImpl) other;
        return Objects.equals(name, this.name) && Objects.equals(this.temp, that.temp) && Objects
            .equals(this.shelfLifeInSecs, that.shelfLifeInSecs) && Objects.equals(id, that.id);
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(OrderImpl.class).add("orderId", id).toString();
    }
}
