import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EventBusTest {

    /** Runs "async" tasks immediately on the calling thread, so tests are deterministic. */
    static final class DirectExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;
        public void execute(Runnable r) { r.run(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    private final EventBusDemo.EventBus bus = new EventBusDemo.EventBus(new DirectExecutor());
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Test
    void deliversOnlyMatchingEventTypes() {
        bus.subscribe("a", EventBusDemo.OrderPlaced.class, e -> seen.add("placed " + e.orderId()), false);
        bus.publish(new EventBusDemo.PaymentFailed("o1", "declined"));
        bus.publish(new EventBusDemo.OrderPlaced("o2", 100));
        assertEquals(List.of("placed o2"), seen);
    }

    @Test
    void closedSubscriptionsStopReceiving() {
        var sub = bus.subscribe("a", EventBusDemo.OrderPlaced.class, e -> seen.add(e.orderId()), false);
        bus.publish(new EventBusDemo.OrderPlaced("o1", 100));
        sub.close();
        bus.publish(new EventBusDemo.OrderPlaced("o2", 100));
        assertEquals(List.of("o1"), seen);
    }

    @Test
    void aFailingNonCriticalSubscriberIsIsolatedAndDeadLettered() {
        bus.subscribe("bad", EventBusDemo.OrderPlaced.class, e -> { throw new IllegalStateException("boom"); }, false);
        bus.subscribe("good", EventBusDemo.OrderPlaced.class, e -> seen.add(e.orderId()), false);
        bus.publish(new EventBusDemo.OrderPlaced("o1", 100));
        assertEquals(List.of("o1"), seen);
        assertEquals("bad", bus.deadLetters.get(0).subscriber());
    }

    @Test
    void aFailingCriticalSubscriberFailsThePublisher() {
        bus.subscribe("fraud", EventBusDemo.OrderPlaced.class, e -> { throw new IllegalStateException("timeout"); }, true);
        assertThrows(IllegalStateException.class, () -> bus.publish(new EventBusDemo.OrderPlaced("o1", 100)));
        assertEquals(1, bus.metrics.get("fraud.failed").sum());
    }
}
