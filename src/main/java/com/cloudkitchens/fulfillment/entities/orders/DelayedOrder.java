package com.cloudkitchens.fulfillment.entities.orders;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * This implementation provides a way to expire an order as soon as it's shelf value becomes zero.
 * This delayed order can be added into a delay queue, and a cleanup thread can just issue a take on the delayed queue. Whenever any order expires,
 * the thread will wakeup and remove the order from the shelf.
 */
@Slf4j public class DelayedOrder implements Delayed {
    private final Order order;
    private final Map<Temperature, Double> decayRateFactors;
    private final long expiryTimestampInMs;

    public DelayedOrder(Order order, Map<Temperature, Double> decayRateFactors) {
        this.order = order;
        this.decayRateFactors = decayRateFactors;
        this.expiryTimestampInMs = getExpiryTimestampInMs();
    }

    public Order getOrder() {
        return order;
    }

    private long getExpiryTimestampInMs() {
        if (order.hasReachedEndState()) {
            return 0;
        }
        return order.getExpiryTimestampInMs(getDecayRateFactor());
    }

    private double getDecayRateFactor() {
        return order.getOrderState() == OrderState.StoredInOverflowShelf ?
            decayRateFactors.get(Temperature.Overflow) :
            decayRateFactors.get(order.getTemperature());
    }

    @Override public long getDelay(TimeUnit unit) {
        long diff = expiryTimestampInMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override public int compareTo(Delayed that) {
        return Ints.saturatedCast(this.expiryTimestampInMs - ((DelayedOrder) that).expiryTimestampInMs);
    }

    @Override public boolean equals(Object other) {
        if (other == this)
            return true;
        if (!(other instanceof DelayedOrder))
            return false;
        return Objects.equals(this.order, ((DelayedOrder) other).order);
    }
}
