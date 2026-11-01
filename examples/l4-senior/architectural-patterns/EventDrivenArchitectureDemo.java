import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Event-driven architecture: services publish facts; others react. Event notification carries an ID (consumers
 * call back for details); event-carried state transfer carries the data (consumers keep their own copy).
 */
public class EventDrivenArchitectureDemo {

    /** Delivery is asynchronous: published events wait in a queue until consumers get to them. */
    static final class Broker {                                                                  // (1)!
        private final Map<String, List<Consumer<Object>>> topics = new HashMap<>();
        private final List<Object[]> queue = new ArrayList<>();
        void subscribe(String topic, Consumer<Object> c) { topics.computeIfAbsent(topic, t -> new ArrayList<>()).add(c); }
        void publish(String topic, Object event) { queue.add(new Object[] {topic, event}); }
        int drain() {
            int delivered = queue.size();
            for (Object[] m : queue) topics.getOrDefault((String) m[0], List.of()).forEach(c -> c.accept(m[1]));
            queue.clear();
            return delivered;
        }
    }

    record PriceChanged(String sku) {}                                                            // (2)!
    record PriceChangedWithData(String sku, long newPricePaise) {}                                // (3)!

    public static void main(String[] args) {
        Map<String, Long> catalogDb = new HashMap<>(Map.of("GHEE-1L", 65_000L));
        int[] callbacks = {0};
        Broker broker = new Broker();

        // Notification style: search index calls the catalogue back for the price.
        Map<String, Long> searchIndex = new HashMap<>();
        broker.subscribe("price.changed", e -> {
            String sku = ((PriceChanged) e).sku();
            callbacks[0]++;
            searchIndex.put(sku, catalogDb.get(sku));
        });

        // State transfer style: the cart service keeps its own copy, no callback needed.
        Map<String, Long> cartPrices = new HashMap<>();
        broker.subscribe("price.changed.v2", e -> {
            var p = (PriceChangedWithData) e;
            cartPrices.put(p.sku(), p.newPricePaise());
        });

        catalogDb.put("GHEE-1L", 62_000L);
        broker.publish("price.changed", new PriceChanged("GHEE-1L"));
        broker.publish("price.changed.v2", new PriceChangedWithData("GHEE-1L", 62_000));

        catalogDb.put("GHEE-1L", 64_000L);   // the catalogue changes again before consumers run
        System.out.println("delivered " + broker.drain() + " events");
        System.out.println("search index (read back current state): " + searchIndex + ", callbacks: " + callbacks[0]);
        System.out.println("cart copy (state carried in event):     " + cartPrices + ", callbacks: 0");
    }
}
