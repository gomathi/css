package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.google.common.base.MoreObjects;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe public class AddResult {

    private final boolean added;
    private final OrderState orderState;
    private final Shelf shelf;

    public AddResult(boolean added, OrderState orderState, Shelf shelf) {
        this.added = added;
        this.orderState = orderState;
        this.shelf = shelf;
    }

    /**
     * Returns true if the order is successfully added to regular shelf/overflow shelf.
     * Otherwise returns false.
     *
     * @return
     */
    public boolean isAdded() {
        return added;
    }

    /**
     * Returns the orderState at the end of Add operation.
     * Copying the OrderState from Order to here, as the state may get changed in Order class.
     *
     * @return
     */
    public OrderState getOrderState() {
        return orderState;
    }

    /**
     * Returns the Shelf that currently stores the order in case of successful add.
     * Otherwise returns the Shelf which tried to store this order.
     *
     * @return
     */
    public Shelf getShelf() {
        return shelf;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(AddResult.class).add("added", isAdded()).add("orderState", getOrderState())
            .add("shelfId", shelf.getId()).toString();
    }
}
