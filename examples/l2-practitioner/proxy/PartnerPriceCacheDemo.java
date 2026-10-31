import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** Realistic Proxy (caching): GreenValley's supplier price API is slow and rate-limited; a caching proxy with a TTL sits in front. */
public class PartnerPriceCacheDemo {

    interface SupplierPrices { long pricePaise(String sku); }

    static final class GreenValleyClient implements SupplierPrices {
        int calls;
        public long pricePaise(String sku) { calls++; return sku.hashCode() % 2 == 0 ? 6_400 : 5_900; }   // pretend: 300 ms HTTP call
    }

    static final class CachingPrices implements SupplierPrices {                           // (1)!
        private record Entry(long price, Instant expires) { }
        private final SupplierPrices real; private final Duration ttl; private final Clock clock;
        private final Map<String, Entry> cache = new HashMap<>();
        int hits, misses;
        CachingPrices(SupplierPrices real, Duration ttl, Clock clock) { this.real = real; this.ttl = ttl; this.clock = clock; }

        public synchronized long pricePaise(String sku) {
            Entry e = cache.get(sku);
            if (e != null && clock.instant().isBefore(e.expires())) { hits++; return e.price(); }   // (2)!
            misses++;
            long p = real.pricePaise(sku);
            cache.put(sku, new Entry(p, clock.instant().plus(ttl)));                          // (3)!
            return p;
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-02T09:00:00Z");
        public Instant instant() { return now; }
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
    }

    public static void main(String[] args) {
        GreenValleyClient client = new GreenValleyClient();
        MutableClock clock = new MutableClock();
        CachingPrices prices = new CachingPrices(client, Duration.ofMinutes(5), clock);

        for (int i = 0; i < 100; i++) prices.pricePaise(i % 2 == 0 ? "TOMATO-1KG" : "ONION-1KG");
        System.out.println("100 lookups at 09:00 -> supplier calls: " + client.calls + " (hits " + prices.hits + ")");
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        prices.pricePaise("TOMATO-1KG");
        System.out.println("after 6 minutes, TOMATO refreshed -> supplier calls: " + client.calls);
    }
}
