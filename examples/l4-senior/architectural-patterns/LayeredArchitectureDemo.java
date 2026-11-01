import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Layered architecture: presentation -> application -> domain -> infrastructure, each layer calling only the one below. */
public class LayeredArchitectureDemo {

    // ---- Infrastructure layer ----
    static final class ProductTable {                                                   // (1)!
        final Map<String, long[]> rows = new TreeMap<>(Map.of("MILK-1L", new long[] {6_800, 40}, "PANEER-200G", new long[] {9_500, 0}));
    }

    // ---- Domain layer ----
    record Product(String sku, long pricePaise, int stock) { boolean available() { return stock > 0; } }

    // ---- Application layer ----
    static final class CatalogService {
        private final ProductTable table;
        CatalogService(ProductTable table) { this.table = table; }
        List<Product> availableProducts() {
            return table.rows.entrySet().stream()
                    .map(e -> new Product(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
                    .filter(Product::available).toList();
        }
    }

    // ---- Presentation layer ----
    static final class CatalogController {                                              // (2)!
        private final CatalogService service;
        CatalogController(CatalogService service) { this.service = service; }
        String get() {
            return service.availableProducts().stream().map(p -> p.sku() + " Rs " + p.pricePaise() / 100).toList().toString();
        }
    }

    /** Before: a "shortcut" controller that reads the table directly and repeats (and forgets) a rule. */
    static final class ShortcutController {                                             // (3)!
        private final ProductTable table;
        ShortcutController(ProductTable table) { this.table = table; }
        String get() { return table.rows.keySet().toString(); }    // forgot the "in stock" rule
    }

    public static void main(String[] args) {
        ProductTable table = new ProductTable();
        System.out.println("layered:  " + new CatalogController(new CatalogService(table)).get());
        System.out.println("shortcut: " + new ShortcutController(table).get());
    }
}
