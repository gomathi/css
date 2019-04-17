package com.cloudkitchens.fulfillment.entities.orders;

import com.cloudkitchens.fulfillment.entities.Temp;

/**
 * Defines the contract for the Order.
 * Though we dont expect multiple implementations of the Order, interface abstracts methods. That makes
 * the contract to be more readable, and also helps in mocking Order in the unit tests of other components.
 */

public interface Order {

    String getName();

    Temp getTemp();

    /**
     * Returns the shelf life in seconds.
     *
     * @return
     */
    int getShelfLifeInSecs();

    double getDecayRate();

    /**
     * Returns an identifier to track the order.
     *
     * @return
     */
    String getId();

    /**
     * Timestamp at which a kitchen has sent an order to shelf for delivery.
     * This timestamp is used in calculating shelf life value also will be available .
     *
     * @return timestamp in milliseconds
     */
    long getCreatedTimestamp();

    /**
     * Provides the shelf life value of this order. A value zero or less than zero indicates this order is expired.
     * The unit of returned value is in milliseconds.
     *
     * <p>
     *
     * @param decayRateFactor, decayRateFactor determines how fast the order will lose its value in a shelf(depending upon the shelf type).
     *                         A value of 2 means the order will decay 2 times faster. This parameter is passed by the ShelfPod which stores this
     *                         Order.
     * @return
     */
    long getCurrShelfValueInMs(double decayRateFactor);

    long getNormalizedValue(double decayRateFactor);

    long getExpiryTimestampInMs(double decayRateFactor);

    /**
     * Indicates whether the order is expired or not.
     * <p>
     * Implementation should make sure that given the same decayRateFactor, once the order expires, it should remain in expired state.
     *
     * @param decayRateFactor
     * @return
     */
    boolean isExpired(double decayRateFactor);

    OrderState getOrderState();

    void setOrderState(OrderState orderState);

    boolean compareAndSet(OrderState oldState, OrderState newState);

    boolean isReachedEndState();

    boolean isInCurrentlyInAnyShelf();

    long getTimeSpentOnOverflowShelfInMs();

    void setTimeSpentOnOverflowShelfInMs(long timeSpentOnOverflowShelfInMs);
}
