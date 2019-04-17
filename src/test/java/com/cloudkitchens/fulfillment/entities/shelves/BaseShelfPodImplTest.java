package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temp;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderImpl;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.cloudkitchens.fulfillment.entities.shelves.util.ShelfUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BaseShelfPodImplTest {

    private static final int SHELF_CAPACITY = 2;
    private static final int OVERFLOW_DECAY_RATE_FACTOR = 2;
    private static final int REGULAR_SHELF_DECAY_RATE_FACTOR = 1;

    private static List<ShelfInfo> generateRegularShelfInfosAndOverflowShelfInfo(int capacity) {
        List<ShelfInfo> shelfInfos = new ArrayList<>();
        for (Temp temp : Temp.values()) {
            shelfInfos.add(new ShelfInfo(UUID.randomUUID().toString(),
                temp == Temp.Overflow ? OVERFLOW_DECAY_RATE_FACTOR : REGULAR_SHELF_DECAY_RATE_FACTOR, capacity, temp));
        }
        return shelfInfos;
    }

    private static List<Order> generateOrders(List<Temp> tempList, int capacity) {
        List<Order> result = new ArrayList<>();
        int shelfLifeInSecs = 300;
        for (Temp temp : tempList) {
            for (int i = 0; i < capacity; i++) {
                result.add(new OrderImpl(UUID.randomUUID().toString(), "Item", temp, shelfLifeInSecs, .45));
                shelfLifeInSecs--;
            }
        }
        return result;
    }

    private static List<Order> pollAllOrders(ShelfPod shelfPod) {
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
        BaseShelfPodImpl shelfPod = new BaseShelfPodImpl(generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY));
        Order expected = new OrderImpl(UUID.randomUUID().toString(), "HotItem", Temp.Hot, 300, .45);
        shelfPod.addOrder(expected);
        Order actual = shelfPod.pollOrder();
        assertEquals(expected, actual, "Adding to regular hot shelf failed.");
        assertNull(shelfPod.pollOrder(), "Regular hot shelf is delivering the same order twice.");
    }

    @Test public void testAddForPriorityInRegularShelf() {
        BaseShelfPodImpl shelfPod = new BaseShelfPodImpl(generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY));
        Order expectedSecond = new OrderImpl(UUID.randomUUID().toString(), "HotItem", Temp.Hot, 300, .45);
        Order expectedFirst = new OrderImpl(UUID.randomUUID().toString(), "HotItem", Temp.Hot, 200, .45);

        shelfPod.addOrder(expectedSecond);
        shelfPod.addOrder(expectedFirst);

        Order actualFirst = shelfPod.pollOrder();
        Order actualSecond = shelfPod.pollOrder();
        assertEquals(ImmutableList.of(expectedFirst, expectedSecond), ImmutableList.of(actualFirst, actualSecond),
            "Shelf life of the orders are not respected.");
    }

    @Test public void testAddToRegularShelfWithOverflowShelf() {
        List<ShelfInfo> shelfInfos = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPodImpl baseShelfPod = new BaseShelfPodImpl(shelfInfos);
        List<Order> expectedItemsForAddition = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            expectedItemsForAddition.add(new OrderImpl(UUID.randomUUID().toString(), "HotItem", Temp.Hot, 300 - i, .45));
        List<Order> expected = new ArrayList<>(expectedItemsForAddition);
        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelfInfos)));

        for (Order order : expectedItemsForAddition)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Overflow shelf is not used when the regular hot shelf is full");
    }

    @Test public void testForExpiryDueToNoSpace() {
        List<ShelfInfo> shelfInfos = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPodImpl baseShelfPod = new BaseShelfPodImpl(shelfInfos);
        List<Order> expectedItemsForAddition = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            expectedItemsForAddition.add(new OrderImpl(UUID.randomUUID().toString(), "HotItem", Temp.Hot, 300 - i, .45));

        List<Order> expected = new ArrayList<>(expectedItemsForAddition);
        expected.remove(expected.size() - 1);

        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelfInfos)));

        for (Order order : expectedItemsForAddition)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Order did not expire due to no space during add.");
        assertEquals(OrderState.ExpiredOnNoSpace, expectedItemsForAddition.get(expectedItemsForAddition.size() - 1).getOrderState(),
            "Order did not expire due to no space availability during add.");
    }

    @Test public void testForAddToAllShelves() {
        List<ShelfInfo> shelfInfos = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPodImpl baseShelfPod = new BaseShelfPodImpl(shelfInfos);
        List<Order> expectedItemsForAddition =
            generateOrders(ImmutableList.of(Temp.Hot, Temp.Cold, Temp.Frozen, Temp.Frozen), SHELF_CAPACITY);

        List<Order> expected = new ArrayList<>(expectedItemsForAddition);
        Collections.sort(expected, new OrderExpiryComparator(ShelfUtils.getDecayRateFactors(shelfInfos)));

        for (Order order : expectedItemsForAddition)
            baseShelfPod.addOrder(order);

        List<Order> actual = pollAllOrders(baseShelfPod);

        assertEquals(expected, actual, "Adding items to all shelves including overflow shelf did not work.");
    }

    @Test public void testGetOrders() {
        List<ShelfInfo> shelfInfos = generateRegularShelfInfosAndOverflowShelfInfo(SHELF_CAPACITY);
        BaseShelfPodImpl baseShelfPod = new BaseShelfPodImpl(shelfInfos);

        List<Order> actual = baseShelfPod.getOrders();
        assertEquals(0, actual.size(), "No orders is expected to be in the empty shelf.");

        Order expectedOrder = new OrderImpl(UUID.randomUUID().toString(), "HotItem", Temp.Hot, 300, .45);
        baseShelfPod.addOrder(expectedOrder);
        actual = baseShelfPod.getOrders();
        assertEquals(1, actual.size(), "One order is expected to be in the shelf.");
        assertEquals(expectedOrder, actual.get(0), "Orders are expected to match.");
        baseShelfPod.pollOrder();
        actual = baseShelfPod.getOrders();
        assertEquals(0, actual.size(), "No orders is expected to be in the shelf after they all delivered.");

        List<Order> expected = generateOrders(ImmutableList.of(Temp.Hot, Temp.Cold, Temp.Frozen, Temp.Frozen), SHELF_CAPACITY);
        for (Order order : expected)
            baseShelfPod.addOrder(order);
        actual = baseShelfPod.getOrders();
        assertEquals(ImmutableSet.of(expected), ImmutableSet.of(actual));
    }

}
