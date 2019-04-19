package com.cloudkitchens.fulfillment.entities.orders;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class OrderTest {

    @Test public void testOrderAttributes() {
        Order order = new Order(UUID.randomUUID().toString(), "test", Temperature.Hot, 300, .45);
        Assertions.assertEquals("test", order.getName());
        Assertions.assertEquals(Temperature.Hot, order.getTemperature());
        Assertions.assertEquals(300, order.getShelfLifeInSecs());
        Assertions.assertEquals(.45, order.getDecayRate());
    }

    @Test public void testOrderShelfLifeValues() throws InterruptedException {
        Order order = new Order(UUID.randomUUID().toString(), "test", Temperature.Hot, 300, 1);
        Thread.sleep(1000);
        long shelfLifeValueInMs = order.getCurrShelfValueInMs(1);
        Assertions.assertTrue(shelfLifeValueInMs > 290000l);
    }

    @Test public void testNormalizedValueInMs() throws InterruptedException {
        Order order = new Order(UUID.randomUUID().toString(), "test", Temperature.Hot, 300, 1);
        Thread.sleep(1000);
        double normalizedValue = order.getNormalizedValue(1);
        Assertions.assertTrue(normalizedValue > .98);
    }
}
