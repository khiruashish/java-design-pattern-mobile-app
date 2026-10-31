import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Production-grade in-process event bus for FreshCart domain events:
 * - type-safe subscriptions by event class
 * - "critical" subscribers run synchronously (in the publisher's transaction); others run async on a bounded pool
 * - one failing subscriber never affects the publisher or other subscribers; failures go to a dead-letter list
 * - subscribe() returns a handle; metrics per subscriber
 */
public class EventBusDemo {

    interface Subscription extends AutoCloseable { @Override void close(); }

    record Failure(String subscriber, Object event, String error) { }

    static final class EventBus implements AutoCloseable {
        private record Sub(String name, Class<?> type, Consumer<Object> handler, boolean critical) { }

        private final List<Sub> subs = new CopyOnWriteArrayList<>();
        private final ExecutorService async;
        final List<Failure> deadLetters = new CopyOnWriteArrayList<>();
        final Map<String, LongAdder> metrics = new ConcurrentHashMap<>();

        EventBus(ExecutorService async) { this.async = async; }

        <E> Subscription subscribe(String name, Class<E> type, Consumer<? super E> handler, boolean critical) {   // (1)!
            Sub s = new Sub(name, type, o -> handler.accept(type.cast(o)), critical);
            subs.add(s);
            return () -> subs.remove(s);
        }

        void publish(Object event) {
            for (Sub s : subs) {
                if (!s.type().isInstance(event)) continue;
                if (s.critical()) deliver(s, event);                                     // (2)!
                else async.execute(() -> deliver(s, event));
            }
        }

        private void deliver(Sub s, Object event) {
            try {
                s.handler().accept(event);
                count(s.name() + ".ok");
            } catch (RuntimeException e) {                                                // (3)!
                count(s.name() + ".failed");
                deadLetters.add(new Failure(s.name(), event, e.getMessage()));
                if (s.critical()) throw e;                                                // (4)!
            }
        }

        private void count(String key) { metrics.computeIfAbsent(key, k -> new LongAdder()).increment(); }

        @Override public void close() throws InterruptedException {
            async.shutdown();
            if (!async.awaitTermination(5, TimeUnit.SECONDS)) async.shutdownNow();       // (5)!
        }
    }

    record OrderPlaced(String orderId, long totalCents) { }
    record PaymentFailed(String orderId, String reason) { }

    public static void main(String[] args) throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        EventBus bus = new EventBus(Executors.newFixedThreadPool(4));

        bus.subscribe("stock", OrderPlaced.class, e -> log.add("stock reserved for " + e.orderId()), true);
        bus.subscribe("email", OrderPlaced.class, e -> { sleep(Duration.ofMillis(50)); log.add("email sent for " + e.orderId()); }, false);
        bus.subscribe("loyalty", OrderPlaced.class, e -> { if (e.totalCents() > 100_000) throw new IllegalStateException("loyalty API 503"); log.add("points for " + e.orderId()); }, false);
        Subscription promo = bus.subscribe("promo", OrderPlaced.class, e -> log.add("promo banner for " + e.orderId()), false);
        bus.subscribe("support", PaymentFailed.class, e -> log.add("support ticket for " + e.orderId() + ": " + e.reason()), false);

        bus.publish(new OrderPlaced("FC-1401", 45_000));
        promo.close();                                                                    // campaign ended
        bus.publish(new OrderPlaced("FC-1402", 150_000));
        bus.publish(new PaymentFailed("FC-1403", "card declined"));

        try {
            bus.subscribe("fraud", OrderPlaced.class, e -> { throw new IllegalStateException("fraud check timeout"); }, true);
            bus.publish(new OrderPlaced("FC-1404", 9_900));
        } catch (IllegalStateException e) {
            log.add("publisher saw critical failure: " + e.getMessage());
        }

        bus.close();
        new ArrayList<>(log).stream().sorted().forEach(System.out::println);
        System.out.println("dead letters: " + bus.deadLetters.stream().map(f -> f.subscriber() + "(" + f.error() + ")").sorted().toList());
        System.out.println(new TreeMap<>(bus.metrics));
    }

    static void sleep(Duration d) { try { Thread.sleep(d); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
