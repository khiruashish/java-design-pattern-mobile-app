import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Realistic Decorator: add caching and call metrics to the product catalog without touching it or its callers. */
public class CachingCatalogDemo {

    record Product(String sku, String name, long priceCents) { }

    interface Catalog { Optional<Product> find(String sku); }

    static final class DbCatalog implements Catalog {
        final AtomicInteger queries = new AtomicInteger();
        private final Map<String, Product> table = Map.of(
                "MILK-1L", new Product("MILK-1L", "Toned milk 1L", 6_800), "RICE-5KG", new Product("RICE-5KG", "Basmati 5kg", 54_900));
        public Optional<Product> find(String sku) { queries.incrementAndGet(); return Optional.ofNullable(table.get(sku)); }
    }

    static final class CachingCatalog implements Catalog {
        private final Catalog inner;
        private final Map<String, Optional<Product>> cache = new ConcurrentHashMap<>();
        CachingCatalog(Catalog inner) { this.inner = inner; }
        public Optional<Product> find(String sku) { return cache.computeIfAbsent(sku, inner::find); }   // (1)!
        void evict(String sku) { cache.remove(sku); }                                     // (2)!
    }

    static final class LoggingCatalog implements Catalog {
        private final Catalog inner; final List<String> log = new ArrayList<>();
        LoggingCatalog(Catalog inner) { this.inner = inner; }
        public Optional<Product> find(String sku) {
            Optional<Product> p = inner.find(sku);
            log.add(sku + (p.isPresent() ? " hit" : " missing"));                         // (3)!
            return p;
        }
    }

    public static void main(String[] args) {
        DbCatalog db = new DbCatalog();
        CachingCatalog cached = new CachingCatalog(db);
        LoggingCatalog catalog = new LoggingCatalog(cached);                                  // (4)!

        for (String sku : List.of("MILK-1L", "MILK-1L", "RICE-5KG", "MILK-1L", "GHEE-500", "GHEE-500")) catalog.find(sku);
        System.out.println("lookups: " + catalog.log.size() + ", database queries: " + db.queries.get());
        cached.evict("MILK-1L");
        catalog.find("MILK-1L");
        System.out.println("after evicting MILK-1L, database queries: " + db.queries.get());
        System.out.println("log: " + catalog.log);
    }
}
