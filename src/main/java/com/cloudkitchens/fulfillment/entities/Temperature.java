package com.cloudkitchens.fulfillment.entities;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Orders and Shelves are associated with Temperature type.
 * Though {@link Temperature#Overflow} is associated only with IShelfPod, its easy to maintain one enum for both Order and IShelfPod.
 */
public enum Temperature {

    Hot, Cold, Frozen, Overflow;

    private static final List<Temperature> REG_SHELVES = ImmutableList.of(Hot, Cold, Frozen);

    public static List<Temperature> getRegularShelves() {
        return REG_SHELVES;
    }}
