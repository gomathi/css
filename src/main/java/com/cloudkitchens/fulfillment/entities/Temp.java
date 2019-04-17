package com.cloudkitchens.fulfillment.entities;

/**
 * Orders and Shelves are associated with Temp type.
 * Though {@link Temp#Overflow} is associated only with ShelfPod, its easy to maintain one enum for both Order and ShelfPod.
 */
public enum Temp {
    Hot, Cold, Frozen, Overflow
}
