package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.cloudkitchens.fulfillment.entities.shelves.util.ShelfUtils;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BaseShelfPodTest {

    protected static final int SHELF_CAPACITY = 2;
    protected static final int OVERFLOW_DECAY_RATE_FACTOR = 2;
    protected static final int REGULAR_SHELF_DECAY_RATE_FACTOR = 1;

    private static List<Shelf> generateRegularShelfInfosAndOverflowShelfInfo(int capacity) {
        List<Shelf> shelves = new ArrayList<>();
        for (Temperature temperature : Temperature.values()) {
            shelves.add(new Shelf(UUID.randomUUID().toString(),
                temperature == Temperature.Overflow ? OVERFLOW_DECAY_RATE_FACTOR : REGULAR_SHELF_DECAY_RATE_FACTOR, capacity, temperature));
        }
        return shelves;
    }

    private static List<Order> generateOrders(List<Temperature> temperatureList, int capacity) {
        List<Order> result = new ArrayList<>();
        int shelfLifeInSecs = 300;
        for (Temperature temperature : temperatureList) {
            for (int i = 0; i < capacity; i++) {
                result.add(new Order(UUID.randomUUID().toString(), "Item", temperature, shelfLifeInSecs, .45));
                shelfLifeInSecs--;
            }
        }
        return result;
    }

    private static List<Order> pollAllOrders(IShelfPod shelfPod) {
        List<Order> result = new ArrayList<>();
        while (true) {
            Order order = shelfPod.pollOrder();
            if (order == null)
                break;
            result.add(order);
        }
        return result;
    }

    @Test public void testAddToRegularShelf() {
        BaseShelfPod baseShelfPod = new BaseShelfPod(generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY));
        Order expected = new Order(UUID.randomUUID().toString(), "HotItem", Temperature.Hot, 300, .45);
        baseShelfPod.addOrder(expected);
        Order actual = baseShelfPod.pollOrder();
        assertEquals(expected, actual, "Adding to regular hot shelf failed.");
        assertNull(baseShelfPod.pollOrder(), "Regular hot shelf is delivering the same order twice.");
    }

    @Test public void testAddForPriorityInRegularShelf() {
        BaseShelfPod baseShelfPod = new BaseShelfPod(generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY));
        Order expectedSecond = new Order(UUID.randomUUID().toString(), "HotItem", Temperature.Hot, 300, .45);
        Order expectedFirst = new Order(UUID.randomUUID().toString(), "HotItem", Temperature.Hot, 200, .45);

        baseShelfPod.addOrder(expectedSecond);
        baseShelfPod.addOrder(expectedFirst);

        Order actualFirst = baseShelfPod.pollOrder();
        Order actualSecond = baseShelfPod.pollOrder();
        assertEquals(ImmutableList.of(expectedFirst, expectedSecond), ImmutableList.of(actualFirst, actualSecond),
            "Shelf life of the orders are not respected.");
    }

    @Test public void testAddToRegularShelfWithOverflowShelf() {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPod baseShelfPod = new BaseShelfPod(shelves);
        List<Order> expectedItemsForAddition = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            expectedItemsForAddition.add(new Order(UUID.randomUUID().toString(), "HotItem", Temperature.Hot, 300 - i, .45));
        List<Order> expected = new ArrayList<>(expectedItemsForAddition);
        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelves)));

        for (Order order : expectedItemsForAddition)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Overflow shelf is not used when the regular hot shelf is full");
    }

    @Test public void testForExpiryDueToNoSpace() {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPod baseShelfPod = new BaseShelfPod(shelves);
        List<Order> expectedItemsForAddition = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            expectedItemsForAddition.add(new Order(UUID.randomUUID().toString(), "HotItem", Temperature.Hot, 300 - i, .45));

        List<Order> expected = new ArrayList<>(expectedItemsForAddition);
        expected.remove(expected.size() - 1);

        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelves)));

        for (Order order : expectedItemsForAddition)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Order did not expire due to no space during add.");
        assertEquals(OrderState.ExpiredOnNoSpace, expectedItemsForAddition.get(expectedItemsForAddition.size() - 1).getOrderState(),
            "Order did not expire due to no space availability during add.");
    }

    @Test public void testForAddToAllShelves() {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPod baseShelfPod = new BaseShelfPod(shelves);
        List<Order> expectedItemsForAddition =
            generateOrders(ImmutableList.of(Temperature.Hot, Temperature.Cold, Temperature.Frozen, Temperature.Frozen), SHELF_CAPACITY);

        List<Order> expected = new ArrayList<>(expectedItemsForAddition);
        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelves)));

        for (Order order : expectedItemsForAddition)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Adding items to all shelves including overflow shelf did not work.");
    }

    @Test public void testGetOrders() {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPod baseShelfPod = new BaseShelfPod(shelves);

        List<Order> actual = baseShelfPod.getOrders();
        assertEquals(0, actual.size(), "No orders is expected to be in the empty shelf.");

        Order expectedOrder = new Order(UUID.randomUUID().toString(), "HotItem", Temperature.Hot, 300, .45);
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
        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelves)));
        assertEquals(expected, actual);
    }

}
