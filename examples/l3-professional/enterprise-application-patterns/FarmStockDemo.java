import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** GreenValley farm: a Repository hides storage and a Service Layer exposes one use case. */
public class FarmStockDemo {

    record Crate(String id, String produce, int kg) {}

    interface CrateRepository {                                          // (1)!
        void save(Crate c);
        Optional<Crate> findById(String id);
        List<Crate> findByProduce(String produce);
    }

    static final class InMemoryCrates implements CrateRepository {
        private final Map<String, Crate> rows = new TreeMap<>();
        public void save(Crate c) { rows.put(c.id(), c); }
        public Optional<Crate> findById(String id) { return Optional.ofNullable(rows.get(id)); }
        public List<Crate> findByProduce(String p) { return rows.values().stream().filter(c -> c.produce().equals(p)).toList(); }
    }

    record StockService(CrateRepository crates) {                       // (2)!
        int kgAvailable(String produce) { return crates.findByProduce(produce).stream().mapToInt(Crate::kg).sum(); }
    }

    public static void main(String[] args) {
        CrateRepository crates = new InMemoryCrates();
        crates.save(new Crate("C1", "tomato", 20));
        crates.save(new Crate("C2", "tomato", 15));
        crates.save(new Crate("C3", "spinach", 8));
        StockService stock = new StockService(crates);
        System.out.println("tomato kg: " + stock.kgAvailable("tomato"));
        System.out.println("okra kg: " + stock.kgAvailable("okra"));
        System.out.println("C3: " + crates.findById("C3").orElseThrow());
    }
}
