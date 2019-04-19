package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.google.common.base.MoreObjects;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;

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

    @Override public boolean equals(Object other) {
        if (other == this)
            return true;
        if (!(other instanceof Shelf))
            return false;
        Shelf that = (Shelf) other;
        return Objects.equals(id, that.id) && Objects.equals(capacity, that.capacity) && Objects.equals(temperature, that.temperature);
    }


    @Override public int hashCode() {
        return Objects.hash(id, capacity, temperature);
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(Shelf.class).add("id", id).add("decayRateFactor", decayRateFactor).add("capacity", capacity)
            .add("temperature", temperature).toString();
    }
}
