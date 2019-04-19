package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.orders.comparators.OrderExpiryComparator;
import com.cloudkitchens.fulfillment.entities.shelves.util.ShelfUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cloudkitchens.fulfillment.entities.shelves.BaseShelfPodTest.createOrder;
import static com.cloudkitchens.fulfillment.entities.shelves.BaseShelfPodTest.generateRegularShelfInfosAndOverflowShelfInfo;
import static com.cloudkitchens.fulfillment.entities.shelves.BaseShelfPodTest.generateOrders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShelfPodTest {

    // Tests whether the mover thread automatically moves orders from overflow shelf to regular shelf when the space is available.
    @Test public void testMoverThread() throws InterruptedException {
        Temperature temperature = Temperature.Hot;

        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1);
        ShelfPod shelfPod = new ShelfPod(shelves);
        shelfPod.startBackgroundActivities();
        try {
            Order expectedOrderWithLowShelfTime = createOrder(temperature, 100);
            shelfPod.addOrder(expectedOrderWithLowShelfTime);
            Order expectedOrderWithHighShelfTime = createOrder(temperature, 300);
            //This will cause shelf to store the order in overflow shelf as the regular shelf has capacity of 1, and we added already one.
            shelfPod.addOrder(expectedOrderWithHighShelfTime);
            //Since we added the first order with low shelf time, we expect the item to be returned from regular shelf, then we can test
            // whether order moves from overflow shelf to regular shelf.
            Order actualOrderWithLowShelfTime = shelfPod.pollOrder();
            assertEquals(expectedOrderWithLowShelfTime, actualOrderWithLowShelfTime);

            //lets give some time for mover thread to move items.
            //though local testing worked with just under 10ms delay, increasing this value so that this test wont become a flaky test.
            Thread.sleep(100);

            Order actualOrderWithHighShelfTime = shelfPod.pollOrder();
            assertNotNull(actualOrderWithHighShelfTime);
            assertEquals(expectedOrderWithHighShelfTime, actualOrderWithHighShelfTime);
            assertEquals(OrderState.DeliveredFromRegularShelf, actualOrderWithHighShelfTime.getOrderState(),
                "Order was not moved from overflow shelf to regular shelf.");
        } finally {
            shelfPod.stopBackgroundActivities();
        }
    }

    @Test public void testMoverThreadWithMultipleShelves() throws InterruptedException {
        Temperature temperature = Temperature.Hot;

        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(5, 15, 2, 1);
        ShelfPod shelfPod = new ShelfPod(shelves);
        shelfPod.startBackgroundActivities();
        try {
            List<Order> expected = new ArrayList<>();
            expected.addAll(generateOrders(ImmutableList.of(Temperature.Hot, Temperature.Cold, Temperature.Frozen), 5, 300));
            expected.addAll(generateOrders(ImmutableList.of(Temperature.Hot, Temperature.Cold, Temperature.Frozen), 5, 700));

            for (Order order : expected) {
                shelfPod.addOrder(order);
            }

            List<Order> actual = new ArrayList<>();
            for (int i = 1; i <= 15; i++)
                actual.add(shelfPod.pollOrder());

            //lets give some time for mover thread to move items.
            //though local testing worked with just under 10ms delay, increasing this value so that this test wont become a flaky test.
            Thread.sleep(100);

            for (int i = 1; i <= 15; i++)
                actual.add(shelfPod.pollOrder());

            assertEquals(ImmutableSet.copyOf(expected), ImmutableSet.copyOf(actual));
            Set<OrderState> orderStateSet = actual.stream().map(order -> order.getOrderState()).collect(Collectors.toSet());
            assertEquals(1, orderStateSet.size());
            assertTrue(orderStateSet.contains(OrderState.DeliveredFromRegularShelf));

        } finally {
            shelfPod.stopBackgroundActivities();
        }
    }

    // Tests whether the mark expired thread automatically expires the orders
    @Test public void testExpireThreadInOverflowShelf() throws InterruptedException {
        Temperature temperature = Temperature.Hot;
        // Setting overflow shelf decay rate factor to 10, so the orders will expire sooner, and this test can run quicker.
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1, 10, 1);
        ShelfPod shelfPod = new ShelfPod(shelves);
        shelfPod.startBackgroundActivities();
        try {

            Order expectedOrderWithHighShelfTime = createOrder(temperature, 300);
            shelfPod.addOrder(expectedOrderWithHighShelfTime);

            Order expectedOrderWithLowShelfTime = createOrder(temperature, 1, .45);
            //This will cause shelf to store the order in overflow shelf as the regular shelf has capacity of 1, and we added already one.
            Thread.sleep(100);
            shelfPod.addOrder(expectedOrderWithLowShelfTime);

            Thread.sleep(800);
            assertEquals(OrderState.ExpiredInOverflowShelf, expectedOrderWithLowShelfTime.getOrderState());

        } finally {
            shelfPod.stopBackgroundActivities();
        }
    }

    @Test public void testExpireThreadInRegularShelf() throws InterruptedException {
        Temperature temperature = Temperature.Hot;
        // Setting overflow shelf decay rate factor to 10, so the orders will expire sooner, and this test can run quicker.
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1, 1, 10);
        ShelfPod shelfPod = new ShelfPod(shelves);
        shelfPod.startBackgroundActivities();
        try {

            Order expectedOrderWithLowShelfTime = createOrder(temperature, 1, .45);
            Thread.sleep(100);
            shelfPod.addOrder(expectedOrderWithLowShelfTime);

            Thread.sleep(800);
            assertEquals(OrderState.ExpiredInRegularShelf, expectedOrderWithLowShelfTime.getOrderState());

        } finally {
            shelfPod.stopBackgroundActivities();
        }
    }
}
