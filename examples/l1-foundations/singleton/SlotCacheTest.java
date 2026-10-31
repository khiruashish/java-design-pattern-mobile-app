import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SlotCacheTest {

    private final SlotCacheDemo.MutableClock clock = new SlotCacheDemo.MutableClock(Instant.parse("2026-11-02T09:00:00Z"));
    private final AtomicInteger loads = new AtomicInteger();

    /** Each test gets its OWN cache: impossible with a static singleton. */
    private SlotCacheDemo.SlotCache newCache() {
        return new SlotCacheDemo.SlotCache(s -> { loads.incrementAndGet(); return new SlotCacheDemo.Slots(s, List.of("18:00")); },
                Duration.ofMinutes(5), clock);
    }

    @Test
    void servesFromCacheUntilTheTtlExpires() {
        var cache = newCache();
        var first = cache.get("s1");
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertSame(first, cache.get("s1"));
        clock.advance(Duration.ofSeconds(1));
        cache.get("s1");
        assertEquals(2, loads.get());
    }

    @Test
    void handlersShareOneInstance() {
        var cache = newCache();
        new SlotCacheDemo.AppHandler(cache).show("s1");
        new SlotCacheDemo.WebHandler(cache).show("s1");
        assertEquals(1, loads.get());
        assertEquals(1, cache.hits.get());
    }
}
