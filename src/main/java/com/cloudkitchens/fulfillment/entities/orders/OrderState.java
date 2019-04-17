package com.cloudkitchens.fulfillment.entities.orders;

/**
 * An order goes through different states.
 * <p>
 * <p>
 * Order can be in one of the following stages at any given point
 * <p>
 * <p>
 * (1) Created
 * (2) Stored -> {2.1 StoredInRegularShelf, 2.2 StoredInOverflowShelf}
 * (3) Expired -> {ExpiredInRegularShelf, ExpiredInOverflowShelf, ExpiredOnNoSpace, CameExpired}
 * (4) PickedUp -> {PickedUpForDelivery}
 * <p>
 * Following state movements are only allowed
 * <p>
 * (1) -> (3)
 * (1) -> (2) -> (3)
 * (1) -> (2) -> (4)
 * <p>
 * <p>
 * Within (2), following is the only state movement allowed
 * <p>
 * (2.2) -> (2.1)
 * <p>
 * So the list of all possible state movements are as following:
 * <p>
 * (1) -> (3)
 * (1) -> (2.2) -> (3)
 * (1) -> (2.2) -> (2.1) -> (3)
 * (1) -> (2.2) -> (4)
 * (1) -> (2.2) -> (2.1) -> (4)
 * <p>
 * <p>
 * Since the fulfillment service is running in a multi threaded environment, when one thread is trying to move
 * an order from Overflow shelf to Regular shelf, the order may be delivered by another thread from Overflow shelf.
 * Hence using these states, mover thread/delivery thread can resolve any potential race conditions.
 */
public enum OrderState {Created, StoredInRegularShelf, StoredInOverflowShelf, ExpiredInRegularShelf, ExpiredInOverflowShelf, ExpiredOnNoSpace, CameExpired, PickedUpForDelivery}
