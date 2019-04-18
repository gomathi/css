package com.cloudkitchens.fulfillment.entities;

/**
 * Orders and Shelves are associated with Temperature type.
 * Though {@link Temperature#Overflow} is associated only with IShelfPod, its easy to maintain one enum for both Order and IShelfPod.
 */
public enum Temperature {
    Hot, Cold, Frozen, Overflow
}
