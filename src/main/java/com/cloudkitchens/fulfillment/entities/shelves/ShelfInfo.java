package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temp;
import com.google.common.base.MoreObjects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Stores the shelf information like capacity, temp.
 */
@ThreadSafe public class ShelfInfo {

    private final String id;
    private final double decayRateFactor;
    private final int capacity;
    private final Temp temp;

    public ShelfInfo(String id, double decayRateFactor, int capacity, Temp temp) {
        this.id = id;
        this.decayRateFactor = decayRateFactor;
        this.capacity = capacity;
        this.temp = temp;
    }

    public String getId() {
        return id;
    }

    public double getDecayRateFactor() {
        return decayRateFactor;
    }

    public int getCapacity() {
        return capacity;
    }

    public Temp getTemp() {
        return temp;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(ShelfInfo.class).add("id", id).add("decayRateFactor", decayRateFactor).add("capacity", capacity)
            .add("temp", temp).toString();
    }
}
