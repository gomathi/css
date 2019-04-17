package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;

import java.util.List;

/**
 * Defines the contract for the ShelfPod, which manages group of shelves.
 * <p>
 * Contract is aimed at the following consumers
 * <p>
 * External consumers like KitchenService, PickupService.
 */
public interface ShelfPod {

    List<ShelfInfo> getShelfInfos();

    /**
     * This adds the order into the shelf.
     *
     * @param order
     * @return true if the order is successfully added into the shelf, otherwise false.
     */
    AddResult addOrder(Order order);

    /**
     * This removes the order from the shelf for delivery.
     *
     * @return
     */
    Order pollOrder();

    /**
     * Returns the list of orders which are currently stored in the shelf.
     * <p>
     * The orders that are soon to-be expired in the lower indices.
     *
     * @return
     */
    List<Order> getOrders();

    interface AddResult {
        boolean isAdded();

        /**
         * Returns the orderState at the end of Add operation.
         * Copying the OrderState from Order to here, as the state may get changed in Order class.
         *
         * @return
         */
        OrderState getOrderState();

        /**
         * Returns the
         *
         * @return
         */
        ShelfInfo getShelfInfo();
    }
}
