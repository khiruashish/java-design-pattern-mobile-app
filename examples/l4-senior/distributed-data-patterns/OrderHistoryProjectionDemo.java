import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: FreshCart's "My orders" screen is a read model fed by order events. The broker delivers at least once
 * and doesn't guarantee order across partitions, so the projection handles duplicates, stale and early events.
 */
public class OrderHistoryProjectionDemo {

    record OrderEvent(String orderId, int version, String status) {}

    static final class OrderHistoryProjection {
        final Map<String, String> statusByOrder = new TreeMap<>();
        private final Map<String, Integer> appliedVersion = new TreeMap<>();
        private final Map<String, List<OrderEvent>> parked = new TreeMap<>();
        final List<String> log = new ArrayList<>();

        void on(OrderEvent e) {
            int current = appliedVersion.getOrDefault(e.orderId(), 0);
            if (e.version() <= current) { log.add("skip v" + e.version() + " of " + e.orderId() + " (already at v" + current + ")"); return; }   // (1)!
            if (e.version() > current + 1) {                                                                   // (2)!
                parked.computeIfAbsent(e.orderId(), k -> new ArrayList<>()).add(e);
                log.add("park v" + e.version() + " of " + e.orderId() + " (waiting for v" + (current + 1) + ")");
                return;
            }
            statusByOrder.put(e.orderId(), e.status());
            appliedVersion.put(e.orderId(), e.version());
            log.add("apply v" + e.version() + " of " + e.orderId() + " -> " + e.status());
            List<OrderEvent> waiting = parked.getOrDefault(e.orderId(), new ArrayList<>());
            waiting.stream().filter(p -> p.version() == e.version() + 1).findFirst().ifPresent(next -> {      // (3)!
                waiting.remove(next);
                on(next);
            });
        }
    }

    public static void main(String[] args) {
        OrderHistoryProjection view = new OrderHistoryProjection();
        List<OrderEvent> delivered = List.of(
                new OrderEvent("FC-1", 1, "PLACED"),
                new OrderEvent("FC-1", 3, "OUT_FOR_DELIVERY"),
                new OrderEvent("FC-1", 2, "PACKED"),
                new OrderEvent("FC-1", 2, "PACKED"),
                new OrderEvent("FC-2", 1, "PLACED"),
                new OrderEvent("FC-1", 4, "DELIVERED"));
        delivered.forEach(view::on);
        view.log.forEach(l -> System.out.println("  " + l));
        System.out.println("My orders: " + view.statusByOrder);
    }
}
