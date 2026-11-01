import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Production shape: ONE shared cache instance for the whole service, created in the composition root and
 * injected. Thread-safe, time-based expiry with an injected Clock, and hit/miss/load-failure metrics.
 */
public class SlotCacheDemo {

    record Slots(String storeId, List<String> open) { }

    /** Thread-safe, single shared instance. Not a static singleton: tests create their own. */
    static final class SlotCache {
        private record Entry(Slots slots, Instant loadedAt) { }

        private final Map<String, Entry> entries = new ConcurrentHashMap<>();           // (1)!
        private final Function<String, Slots> loader;
        private final Duration ttl;
        private final Clock clock;
        final AtomicInteger hits = new AtomicInteger(), misses = new AtomicInteger(), loadFailures = new AtomicInteger();

        SlotCache(Function<String, Slots> loader, Duration ttl, Clock clock) {
            if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
            this.loader = loader; this.ttl = ttl; this.clock = clock;
        }

        Slots get(String storeId) {
            Instant now = clock.instant();
            Entry current = entries.get(storeId);
            if (current != null && now.isBefore(current.loadedAt().plus(ttl))) {
                hits.incrementAndGet();
                return current.slots();
            }
            misses.incrementAndGet();
            try {
                Entry fresh = new Entry(loader.apply(storeId), now);
                entries.put(storeId, fresh);
                return fresh.slots();
            } catch (RuntimeException e) {
                loadFailures.incrementAndGet();
                if (current != null) return current.slots();                            // (2)!
                throw new IllegalStateException("slots unavailable for " + storeId, e);
            }
        }

        String stats() { return "hits=" + hits + " misses=" + misses + " loadFailures=" + loadFailures; }
    }

    /** A clock tests (and this demo) can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { now = start; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    // ---- Two request handlers that share the single cache instance ----
    record AppHandler(SlotCache cache) { String show(String store) { return "app sees " + cache.get(store).open(); } }
    record WebHandler(SlotCache cache) { String show(String store) { return "web sees " + cache.get(store).open(); } }

    public static void main(String[] args) {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-02T09:00:00Z"));
        AtomicInteger dbCalls = new AtomicInteger();
        boolean[] dbDown = {false};
        Function<String, Slots> database = store -> {
            dbCalls.incrementAndGet();
            if (dbDown[0]) throw new RuntimeException("db timeout");
            return new Slots(store, dbCalls.get() == 1 ? List.of("12:00", "18:00") : List.of("18:00"));
        };

        SlotCache cache = new SlotCache(database, Duration.ofMinutes(5), clock);          // (3)!
        AppHandler app = new AppHandler(cache);
        WebHandler web = new WebHandler(cache);

        System.out.println(app.show("pune-01"));
        System.out.println(web.show("pune-01") + "  (same cache, no db call)");
        clock.advance(Duration.ofMinutes(6));
        System.out.println(app.show("pune-01") + "  (expired, reloaded)");
        clock.advance(Duration.ofMinutes(6));
        dbDown[0] = true;
        System.out.println(web.show("pune-01") + "  (db down, served stale)");
        try { app.show("mumbai-02"); } catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
        System.out.println("db calls: " + dbCalls.get() + ", " + cache.stats());
    }
}
