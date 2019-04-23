package com.cloudkitchens.fulfillment.daemons;

import lombok.Getter;

/**
 * Input orders files converted into list of this class's instances.
 * This class along with Gson makes reading input easier.
 */
@Getter public class OrderInput {
    private String name;
    private String temp;
    private int shelfLife;
    private double decayRate;
}
