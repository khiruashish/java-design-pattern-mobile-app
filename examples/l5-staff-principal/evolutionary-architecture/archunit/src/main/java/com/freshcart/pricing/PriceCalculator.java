package com.freshcart.pricing;

import com.freshcart.pricing.internal.DiscountTable;

public class PriceCalculator {
    public long priceInPaise(String sku, long basePaise) {
        return basePaise * (100 - DiscountTable.percentFor(sku)) / 100;
    }
}
