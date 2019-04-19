package com.cloudkitchens.fulfillment.daemons;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * POJO for storing daemons config. Look at daemons_config.json for example daemons config.
 */
@Getter @Setter @Builder public class Config {
    private double poissonMeanPerSecond;
    private int minDelayForPickupInSecs, maxDelayForPickupInSecs;
    private List<ShelfInput> shelfInputs;
}
