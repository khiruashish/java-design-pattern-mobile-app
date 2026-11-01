package com.freshcart.pricing.internal;

public class DiscountTable {
    public static int percentFor(String sku) {
        return sku.startsWith("CURD") ? 10 : 0;
    }
}
