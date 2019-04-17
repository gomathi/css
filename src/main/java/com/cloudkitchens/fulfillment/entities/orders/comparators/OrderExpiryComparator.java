package com.cloudkitchens.fulfillment.entities.orders.comparators;

import com.cloudkitchens.fulfillment.entities.Temp;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.orders.OrderState;

import java.util.Comparator;
import java.util.Map;

/**
 * Compares expiry timestamp of two orders, and the return values are
 * <p>
 * 0 if two orders have same expiry timestamp
 * -1 if the first order is expiring sooner than the second order
 * 1 if the first order is expiring later than the second order
 * <p>
 * This comparator takes {@link #decayRateFactors} which is used for determining expiry time. As an order's expiration timestamp changes
 * depending upon the shelf where it is stored, decayRateFactor is used as parameter which can be provided dynamically by shelves.
 * As decayRateFactor is provided by the ShelfPod, this compare function can't be implemented as Comparable in Order class.
 * <p>
 * This comparator is used in sorting orders for maintaining the orders in a priority queue(so that soon to be
 * expired order can be given to PickupService) and displaying purposes as well.
 */
public class OrderExpiryComparator implements Comparator<Order> {
    private final Map<Temp, Double> decayRateFactors;

    public OrderExpiryComparator(Map<Temp, Double> decayRateFactors) {
        this.decayRateFactors = decayRateFactors;
    }

    @Override public int compare(Order first, Order second) {
        double firstOrderDecayRate = getDecayRate(first);
        double secondOrderDecayRate = getDecayRate(second);

        int result = Long.compare(first.getExpiryTimestampInMs(firstOrderDecayRate), second.getExpiryTimestampInMs(secondOrderDecayRate));
        // In case of a tie, lets use their ids to sort it. So we will have consistency.
        if (result == 0)
            return first.getId().compareTo(second.getId());
        return result;
    }

    private double getDecayRate(Order order) {
        return order.getOrderState() == OrderState.StoredInOverflowShelf ?
            decayRateFactors.get(Temp.Overflow) :
            decayRateFactors.get(order.getTemp());
    }
}
