package com.cloudkitchens.fulfillment.daemons;

import lombok.Getter;

@Getter public class ShelfInput {
    private String temperature;
    private double decayRateFactor;
    private int capacity;
}
