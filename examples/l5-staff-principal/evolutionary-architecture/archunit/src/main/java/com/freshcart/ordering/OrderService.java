package com.freshcart.ordering;

import com.freshcart.ordering.internal.OrderMapper;
import com.freshcart.pricing.PriceCalculator;

public class OrderService {
    private final PriceCalculator prices = new PriceCalculator();

    public String place(String sku, long basePaise) {
        return OrderMapper.toJson(sku, prices.priceInPaise(sku, basePaise));
    }
}
