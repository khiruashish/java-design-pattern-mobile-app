package com.freshcart.starter;

import java.time.LocalTime;
import java.util.List;

public final class App {

    private App() { }

    public static void main(String[] args) {
        LocalTime now = args.length > 0 ? LocalTime.parse(args[0]) : LocalTime.of(9, 15);
        List<DeliverySlot> slots = List.of(
                new DeliverySlot(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                new DeliverySlot(LocalTime.of(12, 0), LocalTime.of(14, 0)));
        for (DeliverySlot slot : slots) {
            System.out.println(slot + (slot.isOpenAt(now, 30) ? " open" : " closed"));
        }
    }
}
