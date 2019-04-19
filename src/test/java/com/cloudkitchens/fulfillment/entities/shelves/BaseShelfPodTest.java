package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaseShelfPodTest {
    protected static final double DEF_DECAY_RATE_OF_ORDER = .45;
    protected static final int SHELF_CAPACITY = 2;
    protected static final int OVERFLOW_SHELF_DECAY_RATE_FACTOR = 2;
    protected static final int REGULAR_SHELF_DECAY_RATE_FACTOR = 1;

    protected static List<Shelf> generateRegularShelfInfosAndOverflowShelfInfo(int capacity) {
        return generateRegularShelfInfosAndOverflowShelfInfo(capacity, OVERFLOW_SHELF_DECAY_RATE_FACTOR, REGULAR_SHELF_DECAY_RATE_FACTOR);
    }

    public static List<Shelf> generateRegularShelfInfosAndOverflowShelfInfo(int capacity, int overflowShelfDecayRateFactor,
        int regularShelfDecayRateFactor) {
        List<Shelf> shelves = new ArrayList<>();
        for (Temperature temperature : Temperature.values()) {
            shelves.add(new Shelf(UUID.randomUUID().toString(),
                temperature == Temperature.Overflow ? overflowShelfDecayRateFactor : regularShelfDecayRateFactor, capacity, temperature));
        }
        return shelves;
    }

    protected static List<Shelf> generateRegularShelfInfosAndOverflowShelfInfo(int capacity, int overflowCapacity,
        int overflowShelfDecayRateFactor, int regularShelfDecayRateFactor) {
        List<Shelf> shelves = new ArrayList<>();
        for (Temperature temperature : Temperature.values()) {
            shelves.add(new Shelf(UUID.randomUUID().toString(),
                temperature == Temperature.Overflow ? overflowShelfDecayRateFactor : regularShelfDecayRateFactor,
                temperature == Temperature.Overflow ? overflowCapacity : capacity, temperature));
        }
        return shelves;
    }

    protected static List<Order> generateOrders(List<Temperature> temperatureList, int capacity, int shelfLifeInSecs) {
        List<Order> result = new ArrayList<>();
        for (Temperature temperature : temperatureList) {
            for (int i = 0; i < capacity; i++) {
                result.add(new Order(UUID.randomUUID().toString(), "Item", temperature, shelfLifeInSecs, .45));
                shelfLifeInSecs--;
            }
        }
        return result;
    }

    protected static List<Order> generateOrders(List<Temperature> temperatureList, int capacity) {
        return generateOrders(temperatureList, capacity, 300);
    }

    protected static List<Order> pollAllOrders(IShelfPod shelfPod) {
        List<Order> result = new ArrayList<>();
        while (true) {
            Order order = shelfPod.pollOrder();
            if (order == null)
                break;
            result.add(order);
        }
        return result;
    }

    public static Order createOrder(Temperature temperature, int shelfLifeInSecs) {
        return createOrder(temperature, shelfLifeInSecs, DEF_DECAY_RATE_OF_ORDER);
    }

    protected static Order createOrder(Temperature temperature, int shelfLifeInSecs, double decayRate) {
        String name = temperature.name() + "Item";
        return new Order(UUID.randomUUID().toString(), name, temperature, shelfLifeInSecs, decayRate);
    }

    @Test public void testShelfAddOrder() {
        for (Temperature temperature : Temperature.getRegularShelves()) {

            // Test add
            List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            ShelfPod baseShelfPod = new ShelfPod(shelves);
            Order expected = createOrder(temperature, 300);

            AddResult addResult = baseShelfPod.addOrder(expected);
            assertTrue(addResult.isAdded(), "Add did not work.");
            assertEquals(BaseShelfPod.getStoredOrderStateForShelfType(temperature), expected.getOrderState());
            assertEquals(BaseShelfPod.getStoredOrderStateForShelfType(temperature), addResult.getOrderState());
            assertEquals(temperature, addResult.getShelf().getTemperature());

            // Test came expired
            shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            baseShelfPod = new ShelfPod(shelves);
            expected = createOrder(temperature, 0);

            addResult = baseShelfPod.addOrder(expected);
            assertFalse(addResult.isAdded(), "Add did not work.");
            assertEquals(OrderState.CameExpired, expected.getOrderState());


            // Test expired on no space
            shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            baseShelfPod = new ShelfPod(shelves);
            baseShelfPod.addOrder(createOrder(temperature, 300)); // adds to regular shelf
            baseShelfPod.addOrder(createOrder(temperature, 300)); // add to overflow shelf
            expected = createOrder(temperature, 300);

            addResult = baseShelfPod.addOrder(expected); // expected to expired due to no space
            assertFalse(addResult.isAdded(), "Add did not work.");
            assertEquals(OrderState.ExpiredOnNoSpace, expected.getOrderState());
        }
    }

    @Test public void testGetShelves() {
        List<Shelf> expected = generateRegularShelfInfosAndOverflowShelfInfo(1);
        ShelfPod baseShelfPod = new ShelfPod(expected);
        List<Shelf> actual = baseShelfPod.getShelves();
        assertEquals(expected, actual);
    }

    @Test public void testShelfMoveOrder() {
        for (Temperature temperature : Temperature.getRegularShelves()) {
            List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            ShelfPod baseShelfPod = new ShelfPod(shelves);

            Order expectedOrderWithLowShelfTime = createOrder(temperature, 100);
            baseShelfPod.addOrder(expectedOrderWithLowShelfTime);
            Order expectedHighPriorityOverflowOrder = createOrder(temperature, 300);
            //This will cause shelf to store the order in overflow shelf as the regular shelf has capacity of 1, and we added already one.
            baseShelfPod.addOrder(expectedHighPriorityOverflowOrder);
            //Since we added the first order with low shelf time, we expect the item to be returned from regular shelf, then we can test
            // whether order moves from overflow shelf to regular shelf.
            Order actualOrderWithLowShelfTime = baseShelfPod.pollOrder();
            assertEquals(expectedOrderWithLowShelfTime, actualOrderWithLowShelfTime);

            AddResult addResult = baseShelfPod.moveOrder(expectedHighPriorityOverflowOrder);
            assertTrue(addResult.isAdded(), "Add did not work.");
            assertEquals(BaseShelfPod.getStoredOrderStateForShelfType(temperature), expectedHighPriorityOverflowOrder.getOrderState());
            assertEquals(BaseShelfPod.getStoredOrderStateForShelfType(temperature), addResult.getOrderState());
            assertEquals(temperature, addResult.getShelf().getTemperature());
        }
    }

    @Test public void testShelfRemoveOrder() {
        for (Temperature temperature : Temperature.getRegularShelves()) {
            List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            ShelfPod baseShelfPod = new ShelfPod(shelves);

            Order expected = createOrder(temperature, 300);
            assertFalse(baseShelfPod.removeOrder(expected));

            baseShelfPod.addOrder(expected);
            boolean removed = baseShelfPod.removeOrder(expected);
            assertTrue(removed);
            assertFalse(baseShelfPod.removeOrder(expected));
            assertNull(baseShelfPod.pollOrder());
            assertFalse(baseShelfPod.addOrder(expected).isAdded());
        }
    }

    @Test public void testShelfExpireOrder() {
        for (Temperature temperature : Temperature.getRegularShelves()) {
            List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            ShelfPod baseShelfPod = new ShelfPod(shelves);

            Order expected = createOrder(temperature, 300);
            assertFalse(baseShelfPod.expireOrder(expected));
            baseShelfPod.addOrder(expected);
            assertTrue(baseShelfPod.expireOrder(expected));
            assertEquals(BaseShelfPod.getExpiredOrderStateForShelf(temperature), expected.getOrderState());
        }
    }

    @Test public void testShelfPollOrder() {
        for (Temperature temperature : Temperature.getRegularShelves()) {
            List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
            ShelfPod baseShelfPod = new ShelfPod(shelves);

            Order expected = createOrder(temperature, 300);
            baseShelfPod.addOrder(expected);
            Order actual = baseShelfPod.pollOrder();
            assertNotNull(actual);
            assertEquals(expected, actual);
            assertEquals(BaseShelfPod.getDeliveredOrderStateForShelf(temperature), actual.getOrderState());
            assertNull(baseShelfPod.pollOrder());

            // Tests for orders with low shelf time are returned first

            baseShelfPod = new ShelfPod(generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY));
            Order expectedSecond = createOrder(temperature, 300);
            Order expectedFirst = createOrder(temperature, 200);

            baseShelfPod.addOrder(expectedSecond);
            baseShelfPod.addOrder(expectedFirst);

            Order actualFirst = baseShelfPod.pollOrder();
            Order actualSecond = baseShelfPod.pollOrder();
            assertEquals(ImmutableList.of(expectedFirst, expectedSecond), ImmutableList.of(actualFirst, actualSecond),
                "Shelf life of the orders are not respected.");
        }
    }

    @Test public void testForAddToAllShelves() {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        ShelfPod baseShelfPod = new ShelfPod(shelves);
        List<Order> generated =
            generateOrders(ImmutableList.of(Temperature.Hot, Temperature.Cold, Temperature.Frozen, Temperature.Frozen), SHELF_CAPACITY);

        List<Order> expected = new ArrayList<>(generated);
        Collections.sort(expected, new OrderExpiryComparator(BaseShelfPod.getDecayRateFactors(shelves)));

        for (Order order : generated)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Adding items to all shelves including overflow shelf did not work.");
    }

    @Test public void testGetOrders() {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        ShelfPod baseShelfPod = new ShelfPod(shelves);

        List<Order> actual = baseShelfPod.getOrders();
        assertEquals(0, actual.size(), "No orders is expected to be in the empty shelf.");

        Order expectedOrder = createOrder(Temperature.Hot, 300);
        baseShelfPod.addOrder(expectedOrder);
        actual = baseShelfPod.getOrders();
        assertEquals(1, actual.size(), "One order is expected to be in the shelf.");
        assertEquals(expectedOrder, actual.get(0), "Orders are expected to match.");
        baseShelfPod.pollOrder();
        actual = baseShelfPod.getOrders();
        assertEquals(0, actual.size(), "No orders is expected to be in the shelf after they all delivered.");

        List<Order> expected =
            generateOrders(ImmutableList.of(Temperature.Hot, Temperature.Cold, Temperature.Frozen, Temperature.Frozen), SHELF_CAPACITY);
        for (Order order : expected)
            baseShelfPod.addOrder(order);
        actual = baseShelfPod.getOrders();
        Collections.sort(expected, new OrderExpiryComparator(BaseShelfPod.getDecayRateFactors(shelves)));
        assertEquals(expected, actual);
    }
}
