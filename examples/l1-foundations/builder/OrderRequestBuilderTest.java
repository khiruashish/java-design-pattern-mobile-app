import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderRequestBuilderTest {

    private final Clock noonInPune = Clock.fixed(Instant.parse("2026-11-02T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private OrderRequestBuilderDemo.OrderRequest.Builder valid() {
        return OrderRequestBuilderDemo.OrderRequest.builder(noonInPune)
                .customer("c1").store("s1").slot(LocalTime.of(18, 0)).add("RICE-5KG", 1, 54_900);
    }

    @Test
    void mergesDuplicateSkusAndKeepsInsertionOrder() {
        var order = valid().add("MILK-1L", 1, 129).add("MILK-1L", 2, 129).build();
        assertEquals(List.of("RICE-5KG", "MILK-1L"), order.lines().stream().map(OrderRequestBuilderDemo.Line::sku).toList());
        assertEquals(3, order.lines().get(1).quantity());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        var e = assertThrows(OrderRequestBuilderDemo.ValidationException.class,
                () -> OrderRequestBuilderDemo.OrderRequest.builder(noonInPune).build());
        assertEquals(List.of("customer is required", "store is required", "basket is empty", "delivery slot is required"), e.errors);
    }

    @Test
    void builtOrderIsImmutableAndIndependentOfTheBuilder() {
        var builder = valid();
        var order = builder.build();
        builder.add("EXTRA", 1, 100);
        assertEquals(1, order.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> order.lines().add(null));
    }

    @Test
    void slotMustBeAtLeastThirtyMinutesAway() {
        var e = assertThrows(OrderRequestBuilderDemo.ValidationException.class, () -> valid().slot(LocalTime.of(12, 30)).build());
        assertTrue(e.errors.get(0).contains("30 minutes"));
    }
}
