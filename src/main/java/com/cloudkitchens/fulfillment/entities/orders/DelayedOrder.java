package com.cloudkitchens.fulfillment.entities.orders;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayedOrder implements Delayed {
    private final Order order;

    public DelayedOrder(Order order) {
        this.order = order;
    }

    @Override public long getDelay(TimeUnit unit) {
        return 0;
    }

    @Override public int compareTo(Delayed o) {
        return 0;
    }
}
