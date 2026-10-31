import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** LSP in production: one contract, checked against every implementation. */
public class LspContractDemo {

    /**
     * Contract:
     *  - reserve(sku, qty) with qty > 0 returns true and lowers available(sku) by qty if enough stock, else false
     *  - it never throws for an unknown sku (it returns false)
     *  - available(sku) is never negative
     */
    interface StockReservations {                                                       // (1)!
        boolean reserve(String sku, int qty);
        int available(String sku);
    }

    static final class InMemoryStock implements StockReservations {
        private final Map<String, Integer> stock = new HashMap<>();
        InMemoryStock(Map<String, Integer> initial) { stock.putAll(initial); }
        public synchronized boolean reserve(String sku, int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
            int have = stock.getOrDefault(sku, 0);
            if (have < qty) return false;
            stock.put(sku, have - qty);
            return true;
        }
        public synchronized int available(String sku) { return stock.getOrDefault(sku, 0); }
    }

    /** VIOLATES the contract: throws for unknown SKUs and lets stock go negative ("backorders"). */
    static final class BackorderStock implements StockReservations {                     // (2)!
        private final Map<String, Integer> stock = new HashMap<>();
        BackorderStock(Map<String, Integer> initial) { stock.putAll(initial); }
        public boolean reserve(String sku, int qty) {
            Integer have = Optional.ofNullable(stock.get(sku)).orElseThrow(() -> new IllegalStateException("no such sku " + sku));
            stock.put(sku, have - qty);
            return true;
        }
        public int available(String sku) { return stock.getOrDefault(sku, 0); }
    }

    /** Honours the contract AND adds backorders as a separate, explicit capability. */
    interface Backorders { int backordered(String sku); }                                 // (3)!

    static final class BackorderAwareStock implements StockReservations, Backorders {
        private final InMemoryStock delegate;
        private final Map<String, Integer> backorders = new HashMap<>();
        BackorderAwareStock(Map<String, Integer> initial) { delegate = new InMemoryStock(initial); }
        public boolean reserve(String sku, int qty) {
            if (delegate.reserve(sku, qty)) return true;
            backorders.merge(sku, qty, Integer::sum);
            return false;
        }
        public int available(String sku) { return delegate.available(sku); }
        public int backordered(String sku) { return backorders.getOrDefault(sku, 0); }
    }

    /** The contract test every implementation must pass. In a real project this is an abstract JUnit class. */
    static List<String> checkContract(Supplier<StockReservations> factory) {               // (4)!
        List<String> failures = new ArrayList<>();
        check(failures, "reserves when enough stock", () -> { var s = factory.get(); return s.reserve("MILK", 2) && s.available("MILK") == 3; });
        check(failures, "refuses when not enough stock", () -> { var s = factory.get(); return !s.reserve("MILK", 9) && s.available("MILK") == 5; });
        check(failures, "unknown sku returns false", () -> !factory.get().reserve("NOPE", 1));
        check(failures, "never negative", () -> { var s = factory.get(); s.reserve("MILK", 9); return s.available("MILK") >= 0; });
        return failures;
    }

    interface Check { boolean ok() throws Exception; }
    static void check(List<String> failures, String name, Check c) {
        try { if (!c.ok()) failures.add(name); } catch (Exception e) { failures.add(name + " (threw " + e.getClass().getSimpleName() + ")"); }
    }

    public static void main(String[] args) {
        Map<String, Integer> initial = Map.of("MILK", 5);
        Map<String, Supplier<StockReservations>> impls = new java.util.LinkedHashMap<>();
        impls.put("InMemoryStock", () -> new InMemoryStock(initial));
        impls.put("BackorderStock", () -> new BackorderStock(initial));
        impls.put("BackorderAwareStock", () -> new BackorderAwareStock(initial));

        impls.forEach((name, factory) -> {
            List<String> failures = checkContract(factory);
            System.out.println((failures.isEmpty() ? "PASS " : "FAIL ") + name + (failures.isEmpty() ? "" : " " + failures));
        });

        var aware = new BackorderAwareStock(initial);
        aware.reserve("MILK", 9);
        System.out.println("backordered MILK: " + aware.backordered("MILK") + ", available: " + aware.available("MILK"));
    }
}
