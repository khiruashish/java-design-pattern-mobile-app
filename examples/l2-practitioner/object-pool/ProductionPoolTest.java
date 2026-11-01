import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionPoolTest {

    private final Instant[] now = {Instant.parse("2026-11-02T09:00:00Z")};
    private final ProductionPoolDemo.Pool<ProductionPoolDemo.PrintClient> pool = new ProductionPoolDemo.Pool<>(
            1, () -> new ProductionPoolDemo.PrintClient(1), c -> c.healthy, Duration.ofMinutes(30), Duration.ofSeconds(60), () -> now[0]);

    @Test
    void reusesReturnedObjects() throws Exception {
        ProductionPoolDemo.PrintClient first;
        try (var l = pool.borrow("a", Duration.ofMillis(10))) { first = l.get(); }
        try (var l = pool.borrow("b", Duration.ofMillis(10))) { assertSame(first, l.get()); }
        assertEquals(1, pool.metrics.get("created"));
    }

    @Test
    void timesOutWhenExhausted() throws Exception {
        var held = pool.borrow("a", Duration.ofMillis(10));
        assertThrows(IllegalStateException.class, () -> pool.borrow("b", Duration.ofMillis(20)));
        held.close();
    }

    @Test
    void retiresObjectsPastMaxLifetime() throws Exception {
        ProductionPoolDemo.PrintClient first;
        try (var l = pool.borrow("a", Duration.ofMillis(10))) { first = l.get(); }
        now[0] = now[0].plus(Duration.ofMinutes(31));
        try (var l = pool.borrow("b", Duration.ofMillis(10))) { assertNotSame(first, l.get()); }
    }

    @Test
    void reportsLeasesHeldTooLongAndIgnoresDoubleClose() throws Exception {
        var l = pool.borrow("returns-desk", Duration.ofMillis(10));
        now[0] = now[0].plusSeconds(61);
        assertEquals(List.of("returns-desk held 61s"), pool.suspectedLeaks());
        l.close(); l.close();
        assertTrue(pool.suspectedLeaks().isEmpty());
        assertEquals(1, pool.metrics.get("returned"));
    }
}
