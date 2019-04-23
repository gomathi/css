package com.cloudkitchens.fulfillment.entities.pickup;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.cloudkitchens.fulfillment.entities.shelves.BaseShelfPodTest;
import com.cloudkitchens.fulfillment.entities.shelves.Shelf;
import com.cloudkitchens.fulfillment.entities.shelves.ShelfPod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cloudkitchens.fulfillment.entities.shelves.BaseShelfPodTest.generateRegularShelfInfosAndOverflowShelfInfo;

public class DispatcherTest {

    @Test public void testDispatcherAndPickup() throws InterruptedException {
        List<Shelf> shelves = generateRegularShelfInfosAndOverflowShelfInfo(1, 2, 1);
        ShelfPod shelfPod = new ShelfPod(shelves);
        Dispatcher dispatcher = new Dispatcher(shelfPod, 0, 2);
        dispatcher.startBackgroundActivities();
        try {
            Order expected = BaseShelfPodTest.createOrder(Temperature.Hot, 300);
            shelfPod.addOrder(expected);
            Thread.sleep(1500);
            Assertions.assertEquals(OrderState.DeliveredFromRegularShelf, expected.getOrderState());
        } finally {
            dispatcher.stopBackgroundActivities();
        }
    }
}
