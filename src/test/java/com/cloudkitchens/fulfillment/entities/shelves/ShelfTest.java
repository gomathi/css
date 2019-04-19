package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ShelfTest {

    @Test public void testEqualsAndHashCode() {
        String id = UUID.randomUUID().toString();
        Shelf expected = new Shelf(id, 1, 1, Temperature.Hot);
        Assertions.assertFalse(expected.equals(null));

        Shelf actual = new Shelf(id, 1, 1, Temperature.Hot);
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals(id, actual.getId());
        Assertions.assertEquals(1, actual.getDecayRateFactor());
        Assertions.assertEquals(1, actual.getCapacity());

        Set<Shelf> shelves = new HashSet<>();
        shelves.add(expected);
        Assertions.assertTrue(shelves.contains(actual));
    }
}
