package com.freshcart.web;

import com.freshcart.ordering.OrderService;
import com.freshcart.pricing.internal.DiscountTable;

public class OrderController {
    private final OrderService orders = new OrderService();

    public String post(String sku, long basePaise) {
        String banner = "save " + DiscountTable.percentFor(sku) + "%";
        return orders.place(sku, basePaise) + " " + banner;
    }
}
