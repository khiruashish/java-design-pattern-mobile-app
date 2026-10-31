package com.freshcart.starter;

import java.time.LocalTime;
import java.util.Objects;

/** A delivery window. Invalid slots can't be created. */
public record DeliverySlot(LocalTime start, LocalTime end) {

    public DeliverySlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Slot must end after it starts: " + start + "-" + end);
        }
    }

    /** True if an order placed at {@code now} can still make this slot. */
    public boolean isOpenAt(LocalTime now, int cutoffMinutes) {
        if (cutoffMinutes < 0) throw new IllegalArgumentException("cutoffMinutes must not be negative");
        return now.plusMinutes(cutoffMinutes).isBefore(start);
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }
}
