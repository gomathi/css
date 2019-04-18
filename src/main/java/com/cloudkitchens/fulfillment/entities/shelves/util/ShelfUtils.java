package com.cloudkitchens.fulfillment.entities.shelves.util;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.shelves.Shelf;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShelfUtils {
    public static Map<Temperature, Shelf> getTempShelfInfoMap(List<Shelf> shelves) {
        Map<Temperature, Shelf> tempShelfInfoMap = new HashMap<>();
        for (Shelf shelf : shelves) {
            tempShelfInfoMap.put(shelf.getTemperature(), shelf);
        }
        return ImmutableMap.copyOf(tempShelfInfoMap);
    }

    public static Map<Temperature, Double> getDecayRateFactors(List<Shelf> shelves) {
        Map<Temperature, Double> spaces = new HashMap<>();
        for (Shelf shelf : shelves) {
            spaces.put(shelf.getTemperature(), shelf.getDecayRateFactor());
        }
        return ImmutableMap.copyOf(spaces);
    }
}
