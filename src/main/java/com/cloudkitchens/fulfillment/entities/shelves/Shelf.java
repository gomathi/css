package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.google.common.base.MoreObjects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Stores the shelf information like capacity, temperature.
 */
@ThreadSafe public class Shelf {

    private final String id;
    private final double decayRateFactor;
    private final int capacity;
    private final Temperature temperature;

    public Shelf(String id, double decayRateFactor, int capacity, Temperature temperature) {
        this.id = id;
        this.decayRateFactor = decayRateFactor;
        this.capacity = capacity;
        this.temperature = temperature;
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

    public Temperature getTemperature() {
        return temperature;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(Shelf.class).add("id", id).add("decayRateFactor", decayRateFactor).add("capacity", capacity)
            .add("temperature", temperature).toString();
    }
}
