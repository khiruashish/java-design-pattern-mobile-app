import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Production-grade Decorator stack around a remote InventoryClient:
 *   Metrics( CircuitBreaker( Retry( RemoteInventoryClient ) ) )
 * Each decorator does one job, is tested alone, and the stack is assembled in one place.
 */
public class ResilientClientDemo {

    interface InventoryClient { int available(String storeId, String sku); }

    static final class RemoteUnavailable extends RuntimeException { RemoteUnavailable(String m) { super(m); } }
    static final class CircuitOpen extends RuntimeException { CircuitOpen(String m) { super(m); } }

    /** Simulated remote service with a scripted sequence of outcomes ("ok" or "down"). */
    static final class RemoteInventoryClient implements InventoryClient {
        private final List<String> script; private int i; int calls;
        RemoteInventoryClient(String... script) { this.script = List.of(script); }
        public int available(String store, String sku) {
            calls++;
            String outcome = script.get(Math.min(i++, script.size() - 1));
            if (outcome.equals("down")) throw new RemoteUnavailable("inventory 503");
            return 12;
        }
    }

    /** Retries transient failures a fixed number of times. */
    record Retry(InventoryClient inner, int attempts) implements InventoryClient {
        public int available(String store, String sku) {
            for (int a = 1; ; a++) {
                try { return inner.available(store, sku); }
                catch (RemoteUnavailable e) { if (a >= attempts) throw e; }               // (1)!
            }
        }
    }

    /** Opens after N consecutive failures; while open, fails fast; after the cool-down lets one trial call through. */
    static final class CircuitBreaker implements InventoryClient {
        private final InventoryClient inner; private final int threshold; private final Duration coolDown; private final Supplier<Instant> clock;
        private int consecutiveFailures; private Instant openedAt;
        CircuitBreaker(InventoryClient inner, int threshold, Duration coolDown, Supplier<Instant> clock) {
            this.inner = inner; this.threshold = threshold; this.coolDown = coolDown; this.clock = clock;
        }
        public synchronized int available(String store, String sku) {
            if (openedAt != null) {
                if (clock.get().isBefore(openedAt.plus(coolDown))) throw new CircuitOpen("inventory circuit open");   // (2)!
                openedAt = null;                                                          // half-open: allow one trial
            }
            try {
                int r = inner.available(store, sku);
                consecutiveFailures = 0;
                return r;
            } catch (RemoteUnavailable e) {
                if (++consecutiveFailures >= threshold) openedAt = clock.get();           // (3)!
                throw e;
            }
        }
    }

    /** Counts outcomes; outermost so it sees everything, including fast failures. */
    static final class Metrics implements InventoryClient {
        private final InventoryClient inner; final Map<String, Integer> counts = new TreeMap<>();
        Metrics(InventoryClient inner) { this.inner = inner; }
        public int available(String store, String sku) {
            try { int r = inner.available(store, sku); counts.merge("ok", 1, Integer::sum); return r; }
            catch (RuntimeException e) { counts.merge(e.getClass().getSimpleName(), 1, Integer::sum); throw e; }   // (4)!
        }
    }

    public static void main(String[] args) {
        Instant[] now = {Instant.parse("2026-11-02T10:00:00Z")};
        RemoteInventoryClient remote = new RemoteInventoryClient("ok", "down", "down", "down", "down", "ok");
        Metrics client = new Metrics(new CircuitBreaker(new Retry(remote, 2), 2, Duration.ofSeconds(30), () -> now[0]));   // (5)!

        List<String> results = new ArrayList<>();
        for (int request = 1; request <= 6; request++) {
            if (request == 6) now[0] = now[0].plusSeconds(31);                            // cool-down passes
            try { results.add("req" + request + "=" + client.available("pune-01", "MILK-1L")); }
            catch (RuntimeException e) { results.add("req" + request + "=" + e.getClass().getSimpleName()); }
        }
        results.forEach(System.out::println);
        System.out.println("remote calls: " + remote.calls + ", metrics: " + client.counts);
    }
}
