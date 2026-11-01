import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** One process-wide metrics registry that every FreshCart component writes to. */
public class MetricsRegistryDemo {

    enum Metrics {
        INSTANCE;

        private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();       // (1)!

        void increment(String name) {
            if (!name.matches("[a-z_.]+")) throw new IllegalArgumentException("bad metric name: " + name);
            counters.computeIfAbsent(name, n -> new LongAdder()).increment();            // (2)!
        }

        Map<String, Long> snapshot() {
            Map<String, Long> copy = new TreeMap<>();
            counters.forEach((k, v) -> copy.put(k, v.sum()));
            return copy;                                                                 // (3)!
        }
    }

    static final class Checkout {
        void place(String orderId) { Metrics.INSTANCE.increment("checkout.orders_placed"); }
    }

    static final class Delivery {
        void deliver(String orderId, boolean ok) {
            Metrics.INSTANCE.increment(ok ? "delivery.completed" : "delivery.failed");
        }
    }

    public static void main(String[] args) {
        Checkout checkout = new Checkout();
        Delivery delivery = new Delivery();
        for (int i = 1; i <= 3; i++) checkout.place("FC-" + i);
        delivery.deliver("FC-1", true);
        delivery.deliver("FC-2", true);
        delivery.deliver("FC-3", false);
        System.out.println(Metrics.INSTANCE.snapshot());
        try { Metrics.INSTANCE.increment("Delivery Failed!"); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
