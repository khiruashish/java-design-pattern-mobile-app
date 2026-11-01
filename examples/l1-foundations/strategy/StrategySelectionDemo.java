import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Three ways to pick a strategy at runtime: a registry map, a factory that looks at context, and DI. */
public class StrategySelectionDemo {

    interface PackingStrategy { String pack(int items); }
    record OneBag() implements PackingStrategy { public String pack(int n) { return n + " items in 1 bag"; } }
    record ColdChain() implements PackingStrategy { public String pack(int n) { return n + " items in insulated box with ice packs"; } }
    record BulkCrates(int perCrate) implements PackingStrategy {
        public String pack(int n) { return n + " items in " + ((n + perCrate - 1) / perCrate) + " crates"; }
    }

    // 1) Registry: name -> strategy, e.g. from a config file
    static final Map<String, Supplier<PackingStrategy>> REGISTRY = Map.of(                 // (1)!
            "bag", OneBag::new, "cold", ColdChain::new, "crates", () -> new BulkCrates(12));

    static PackingStrategy byName(String name) {
        var s = REGISTRY.get(name);
        if (s == null) throw new IllegalArgumentException("unknown packing '" + name + "', known " + REGISTRY.keySet().stream().sorted().toList());
        return s.get();
    }

    // 2) Context-based factory: the ORDER decides
    record Order(int items, boolean hasFrozen, boolean business) { }
    static final Function<Order, PackingStrategy> BY_ORDER = o ->                          // (2)!
            o.hasFrozen() ? new ColdChain() : o.business() || o.items() > 30 ? new BulkCrates(12) : new OneBag();

    // 3) Dependency injection: the composition root decides, the service just uses it
    static final class PackingStation {
        private final Function<Order, PackingStrategy> chooser;                          // (3)!
        PackingStation(Function<Order, PackingStrategy> chooser) { this.chooser = chooser; }
        String handle(Order o) { return chooser.apply(o).pack(o.items()); }
    }

    public static void main(String[] args) {
        System.out.println("registry: " + byName("crates").pack(40));
        try { byName("drone"); } catch (IllegalArgumentException e) { System.out.println("registry: " + e.getMessage()); }

        PackingStation station = new PackingStation(BY_ORDER);
        System.out.println("by order: " + station.handle(new Order(8, false, false)));
        System.out.println("by order: " + station.handle(new Order(8, true, false)));
        System.out.println("by order: " + station.handle(new Order(45, false, false)));

        PackingStation testStation = new PackingStation(o -> new OneBag());               // tests inject a fixed strategy
        System.out.println("test: " + testStation.handle(new Order(45, true, true)));
    }
}
