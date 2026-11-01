package com.freshcart.starter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DeliverySlotTest {

    private final DeliverySlot noon = new DeliverySlot(LocalTime.of(12, 0), LocalTime.of(14, 0));

    @Test
    void openWhenOrderedBeforeTheCutoff() {
        assertTrue(noon.isOpenAt(LocalTime.of(11, 29), 30));
    }

    @Test
    void closedExactlyAtTheCutoff() {
        assertFalse(noon.isOpenAt(LocalTime.of(11, 30), 30));
    }

    @Test
    void rejectsSlotsThatEndBeforeTheyStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliverySlot(LocalTime.of(14, 0), LocalTime.of(12, 0)));
    }
}
