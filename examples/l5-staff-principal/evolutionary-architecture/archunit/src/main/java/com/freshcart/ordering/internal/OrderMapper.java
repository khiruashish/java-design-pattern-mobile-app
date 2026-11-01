package com.freshcart.ordering.internal;

public class OrderMapper {
    public static String toJson(String sku, long paise) {
        return "{\"sku\":\"" + sku + "\",\"paise\":" + paise + "}";
    }
}
