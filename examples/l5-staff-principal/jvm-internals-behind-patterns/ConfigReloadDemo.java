import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Production-grade: FreshCart's pricing service reloads its price configuration while serving traffic. Each config
 * is an immutable, validated snapshot published through an AtomicReference; readers take one snapshot per request,
 * so they never mix values from two versions. A stress run checks that invariant across threads.
 */
public class ConfigReloadDemo {

    record PricingConfig(int version, Map<String, Long> prices, int deliveryFeePaise) {                     // (1)!
        PricingConfig {
            prices = Map.copyOf(prices);
            if (prices.values().stream().anyMatch(p -> p <= 0)) throw new IllegalArgumentException("non-positive price in v" + version);
        }
    }

    static final class ConfigStore {
        private final AtomicReference<PricingConfig> current;                                               // (2)!
        private final List<Consumer<PricingConfig>> listeners = new ArrayList<>();
        ConfigStore(PricingConfig initial) { current = new AtomicReference<>(initial); }
        PricingConfig snapshot() { return current.get(); }
        void onChange(Consumer<PricingConfig> l) { listeners.add(l); }

        String reload(int version, Map<String, Long> prices, int fee) {
            PricingConfig next;
            try { next = new PricingConfig(version, prices, fee); }                                           // (3)!
            catch (IllegalArgumentException e) { return "rejected: " + e.getMessage() + " (still serving v" + current.get().version() + ")"; }
            PricingConfig prev = current.get();
            if (next.version() <= prev.version()) return "ignored stale v" + version;
            if (!current.compareAndSet(prev, next)) return "lost race, retry";                                  // (4)!
            listeners.forEach(l -> l.accept(next));
            return "now serving v" + version;
        }
    }

    /** One request: read a snapshot once and use only it. */
    static long basketTotal(ConfigStore store) {
        PricingConfig c = store.snapshot();                                                                  // (5)!
        return c.prices().get("MILK-1L") * 2 + c.deliveryFeePaise();
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigStore store = new ConfigStore(new PricingConfig(1, Map.of("MILK-1L", 6801L), 2499));   // every version v: milk 6800+v, fee 2500-v
        List<String> notified = new ArrayList<>();
        store.onChange(c -> notified.add("v" + c.version()));
        System.out.println(store.reload(2, Map.of("MILK-1L", -1L), 2500));
        System.out.println(store.reload(1, Map.of("MILK-1L", 6900L), 2500));

        // stress: readers compute totals while versions 2..200 are published; every total must match some single version
        AtomicInteger inconsistent = new AtomicInteger(), reads = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        List<Thread> readers = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            Thread t = new Thread(() -> {
                while (done.getCount() > 0) {
                    PricingConfig c = store.snapshot();
                    long total = c.prices().get("MILK-1L") * 2 + c.deliveryFeePaise();
                    if (total != 2 * (6800L + c.version()) + (2500 - c.version())) inconsistent.incrementAndGet();
                    reads.incrementAndGet();
                }
            });
            readers.add(t); t.start();
        }
        for (int v = 2; v <= 200; v++) store.reload(v, Map.of("MILK-1L", 6800L + v), 2500 - v);
        done.countDown();
        for (Thread t : readers) t.join();
        System.err.println("reads during reloads: " + reads.get());
        System.out.println("stress: inconsistent totals = " + inconsistent.get() + ", final " + store.snapshot().version()
                + ", listeners notified " + notified.size() + " times");
        System.out.println("basket now: Rs " + basketTotal(store) / 100.0);
    }
}
