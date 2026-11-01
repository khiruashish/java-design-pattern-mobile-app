import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** An in-process event bus is a mediator: publishers and subscribers only know the bus, never each other. */
public class EventBusMediatorDemo {

    record OrderPlaced(String orderId, List<String> skus) { }
    record OrderCancelled(String orderId) { }

    static final class EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> subs = new HashMap<>();
        @SuppressWarnings("unchecked")
        <E> void subscribe(Class<E> type, Consumer<? super E> handler) {                              // (1)!
            subs.computeIfAbsent(type, t -> new ArrayList<>()).add(e -> handler.accept((E) e));
        }
        void publish(Object event) {
            for (Consumer<Object> h : subs.getOrDefault(event.getClass(), List.of())) {
                try { h.accept(event); }
                catch (RuntimeException ex) { System.out.println("  subscriber failed: " + ex.getMessage() + " (others still run)"); }   // (2)!
            }
        }
    }

    public static void main(String[] args) {
        EventBus bus = new EventBus();
        bus.subscribe(OrderPlaced.class, e -> System.out.println("  inventory: reserve " + e.skus()));
        bus.subscribe(OrderPlaced.class, e -> { throw new IllegalStateException("analytics endpoint down"); });
        bus.subscribe(OrderPlaced.class, e -> System.out.println("  notifications: SMS for " + e.orderId()));
        bus.subscribe(OrderCancelled.class, e -> System.out.println("  inventory: release " + e.orderId()));

        System.out.println("checkout publishes OrderPlaced:");
        bus.publish(new OrderPlaced("FC-9401", List.of("MILK-1L", "BREAD")));                          // (3)!
        System.out.println("support publishes OrderCancelled:");
        bus.publish(new OrderCancelled("FC-9401"));
    }
}
