package com.cloudkitchens.fulfillment.entities.shelves;

import com.cloudkitchens.fulfillment.entities.orders.OrderState;
import com.google.common.base.MoreObjects;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe public class AddResultImpl implements ShelfPod.AddResult {

    private final boolean added;
    private final OrderState orderState;
    private final ShelfInfo shelfInfo;

    public AddResultImpl(boolean added, OrderState orderState, ShelfInfo shelfInfo) {
        this.added = added;
        this.orderState = orderState;
        this.shelfInfo = shelfInfo;
    }

    @Override public boolean isAdded() {
        return added;
    }

    @Override public OrderState getOrderState() {
        return orderState;
    }

    @Override public ShelfInfo getShelfInfo() {
        return shelfInfo;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(AddResultImpl.class).add("added", isAdded()).add("orderState", getOrderState())
            .add("shelfId", shelfInfo.getId()).toString();
    }
}
