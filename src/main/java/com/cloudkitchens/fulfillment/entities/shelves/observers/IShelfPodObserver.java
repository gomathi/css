package com.cloudkitchens.fulfillment.entities.shelves.observers;

import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.shelves.AddResult;

/**
 * An observer that listens to {@link com.cloudkitchens.fulfillment.entities.shelves.IShelfPod} events.
 * <p>
 * Currently we are interested only in addOrder event to the shelf in order to dispatch taxi for order delivery.
 * Later more events on the Shelf can be observed for analytics, and improve the efficiency of Shelf based on it.
 */
public interface IShelfPodObserver {

    /**
     * This function is called after the addOrder gets executed on the ShelfPod.
     *
     * @param order
     * @param addResult
     */
    void postAddOrder(Order order, AddResult addResult);
}
