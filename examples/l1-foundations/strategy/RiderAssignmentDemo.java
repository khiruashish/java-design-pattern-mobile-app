import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Production-grade Strategy: rider assignment.
 * - strategies are small, stateless and individually testable
 * - each store is configured with a strategy; a new strategy can be rolled out to a % of orders (deterministic)
 * - if a strategy fails or finds nobody, a safe fallback runs and the event is counted
 */
public class RiderAssignmentDemo {

    record Rider(String id, double kmAway, int activeOrders, boolean hasColdBox) { }
    record Order(String id, boolean needsColdBox) { }

    enum StrategyName { NEAREST, LEAST_LOADED, BATCHING }

    interface AssignmentStrategy { Optional<Rider> choose(Order order, List<Rider> riders); }

    static List<Rider> eligible(Order o, List<Rider> riders) {
        return riders.stream().filter(r -> !o.needsColdBox() || r.hasColdBox()).filter(r -> r.activeOrders() < 3).toList();   // (1)!
    }

    static final Map<StrategyName, AssignmentStrategy> STRATEGIES = new EnumMap<>(Map.of(
            StrategyName.NEAREST, (o, rs) -> eligible(o, rs).stream().min(Comparator.comparingDouble(Rider::kmAway)),
            StrategyName.LEAST_LOADED, (o, rs) -> eligible(o, rs).stream()
                    .min(Comparator.comparingInt(Rider::activeOrders).thenComparingDouble(Rider::kmAway)),
            StrategyName.BATCHING, (o, rs) -> eligible(o, rs).stream()                    // prefer riders already out with 1-2 orders nearby
                    .filter(r -> r.activeOrders() > 0 && r.kmAway() < 1.5)
                    .min(Comparator.comparingDouble(Rider::kmAway))));

    record StoreConfig(StrategyName current, StrategyName candidate, int candidatePercent) { }

    static final class Dispatcher {
        private final Map<String, StoreConfig> config;
        final Map<String, LongAdder> metrics = new ConcurrentHashMap<>();

        Dispatcher(Map<String, StoreConfig> config) { this.config = Map.copyOf(config); }

        StrategyName strategyFor(String storeId, String orderId) {
            StoreConfig c = config.getOrDefault(storeId, new StoreConfig(StrategyName.NEAREST, null, 0));
            if (c.candidate() == null) return c.current();
            int bucket = bucket(orderId);                                                 // (2)!
            return bucket < c.candidatePercent() ? c.candidate() : c.current();
        }

        /** Stable 0..99 bucket. Mixes the bits because sequential ids have sequential hashCodes. */
        static int bucket(String orderId) {
            int h = orderId.hashCode() * 0x9E3779B9;
            return Math.floorMod(h ^ (h >>> 16), 100);
        }

        String assign(String storeId, Order order, List<Rider> riders) {
            StrategyName name = strategyFor(storeId, order.id());
            Optional<Rider> chosen;
            try {
                chosen = STRATEGIES.get(name).choose(order, riders);
            } catch (RuntimeException e) {                                                // (3)!
                count("assign.error." + name);
                chosen = Optional.empty();
            }
            if (chosen.isEmpty() && name != StrategyName.NEAREST) {
                count("assign.fallback." + name);
                chosen = STRATEGIES.get(StrategyName.NEAREST).choose(order, riders);    // (4)!
                name = StrategyName.NEAREST;
            }
            count("assign.used." + name);
            return order.id() + " -> " + chosen.map(Rider::id).orElse("QUEUED (no eligible rider)") + " via " + name;
        }

        private void count(String key) { metrics.computeIfAbsent(key, k -> new LongAdder()).increment(); }
    }

    public static void main(String[] args) {
        List<Rider> riders = List.of(
                new Rider("ravi", 0.4, 2, false), new Rider("meena", 1.1, 0, true),
                new Rider("arjun", 2.5, 1, true), new Rider("sara", 0.9, 3, true));

        Dispatcher dispatcher = new Dispatcher(Map.of(
                "pune-01", new StoreConfig(StrategyName.NEAREST, StrategyName.LEAST_LOADED, 50),
                "pune-02", new StoreConfig(StrategyName.BATCHING, null, 0)));

        for (String id : List.of("FC-1001", "FC-1002", "FC-1003", "FC-1004")) {
            System.out.println("pune-01 " + dispatcher.assign("pune-01", new Order(id, false), riders));
        }
        System.out.println("pune-02 " + dispatcher.assign("pune-02", new Order("FC-2001", true), riders));
        System.out.println("pune-02 " + dispatcher.assign("pune-02", new Order("FC-2002", false), riders));
        System.out.println("pune-09 " + dispatcher.assign("pune-09", new Order("FC-9001", true), List.of(new Rider("ravi", 0.4, 2, false))));
        System.out.println("same order, same strategy every time: " + (dispatcher.strategyFor("pune-01", "FC-1003") == dispatcher.strategyFor("pune-01", "FC-1003")));
        System.out.println(new java.util.TreeMap<>(dispatcher.metrics));
    }
}
