package com.cloudkitchens.fulfillment.entities.shelves.util;

import com.cloudkitchens.fulfillment.entities.Temp;
import com.cloudkitchens.fulfillment.entities.shelves.ShelfInfo;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShelfUtils {
    public static Map<Temp, ShelfInfo> getTempShelfInfoMap(List<ShelfInfo> shelfInfos) {
        Map<Temp, ShelfInfo> tempShelfInfoMap = new HashMap<>();
        for (ShelfInfo shelfInfo : shelfInfos) {
            tempShelfInfoMap.put(shelfInfo.getTemp(), shelfInfo);
        }
        return ImmutableMap.copyOf(tempShelfInfoMap);
    }

    public static Map<Temp, Double> getDecayRateFactors(List<ShelfInfo> shelfInfos) {
        Map<Temp, Double> spaces = new HashMap<>();
        for (ShelfInfo shelfInfo : shelfInfos) {
            spaces.put(shelfInfo.getTemp(), shelfInfo.getDecayRateFactor());
        }
        return ImmutableMap.copyOf(spaces);
    }
}
