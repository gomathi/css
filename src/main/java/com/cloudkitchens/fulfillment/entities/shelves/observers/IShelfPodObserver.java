package com.cloudkitchens.fulfillment.entities.shelves.observers;

import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.shelves.AddResult;

/**
 * An observer that listens to {@link com.cloudkitchens.fulfillment.entities.shelves.IShelfPod} events.
 * <p>
 * Currently we are interested only in addOrder event to the shelf in order to dispatch taxi for order delivery.
 * Later more events on the Shelf can be observed for analytics, and improve the efficiency of Shelf based on it.
 * <p>
 * The implementers of this observer should make sure they are not blocking the incoming notifications from the shelf.
 * Any heavy duty work should be offloaded from the thread which sends notification.
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
