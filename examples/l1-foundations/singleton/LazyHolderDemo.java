import java.util.Map;

/** Initialization-on-demand holder: lazy, thread-safe, no locks. */
public class LazyHolderDemo {

    static final class ProductCatalog {
        private final Map<String, Long> prices;

        private ProductCatalog() {
            System.out.println("  loading catalog (expensive)...");
            prices = Map.of("MILK-1L", 129L, "BREAD-WHT", 249L);
        }

        private static final class Holder {                                           // (1)!
            static final ProductCatalog INSTANCE = new ProductCatalog();
        }

        static ProductCatalog getInstance() { return Holder.INSTANCE; }               // (2)!

        static String describe() { return "ProductCatalog (no loading needed for this)"; }  // (3)!

        long priceOf(String sku) {
            Long p = prices.get(sku);
            if (p == null) throw new IllegalArgumentException("unknown sku " + sku);
            return p;
        }
    }

    public static void main(String[] args) {
        System.out.println("1. calling a static helper: " + ProductCatalog.describe());
        System.out.println("2. first getInstance():");
        long milk = ProductCatalog.getInstance().priceOf("MILK-1L");
        System.out.println("   milk = " + milk);
        System.out.println("3. second getInstance(): no loading line below");
        System.out.println("   bread = " + ProductCatalog.getInstance().priceOf("BREAD-WHT"));
    }
}
