import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Four classic proxy kinds. Each implements the SAME interface as the real object and controls access to it. */
public class ProxyKindsDemo {

    // ===================== Virtual proxy: create the expensive object only when first used =====================
    interface Catalog { String find(String sku); }
    static final class FullCatalog implements Catalog {
        FullCatalog() { System.out.println("  (loading 80,000 products into memory...)"); }
        public String find(String sku) { return sku + " = Basmati rice 5kg"; }
    }
    static final class LazyCatalog implements Catalog {                                    // (1)!
        private Catalog real;
        public String find(String sku) { if (real == null) real = new FullCatalog(); return real.find(sku); }
    }

    // ===================== Protection proxy: check permission before delegating =====================
    interface Refunds { String refund(String orderId, long paise); }
    record RealRefunds() implements Refunds { public String refund(String id, long p) { return "refunded " + p + " on " + id; } }
    record GuardedRefunds(Refunds real, Set<String> roles) implements Refunds {          // (2)!
        public String refund(String id, long p) {
            if (p > 100_000 && !roles.contains("SUPERVISOR")) throw new SecurityException("refunds over Rs 1000 need a supervisor");
            return real.refund(id, p);
        }
    }

    // ===================== Caching proxy: remember answers from a slow source =====================
    interface Prices { long price(String sku); }
    static final class SupplierPrices implements Prices { int calls; public long price(String s) { calls++; return 4_200; } }
    record CachedPrices(Prices real, Map<String, Long> cache) implements Prices {           // (3)!
        public long price(String s) { return cache.computeIfAbsent(s, real::price); }
    }

    // ===================== Remote proxy: looks local, actually sends a message =====================
    interface StockService { int stock(String sku); }
    static final class StockStub implements StockService {                                // (4)!
        public int stock(String sku) {
            String request = "GET /stock/" + sku;
            String response = fakeNetwork(request);
            return Integer.parseInt(response);
        }
        static String fakeNetwork(String req) { System.out.println("  -> wire: " + req); return "37"; }
    }

    public static void main(String[] args) {
        Catalog catalog = new LazyCatalog();
        System.out.println("virtual: proxy created, nothing loaded yet");
        System.out.println("virtual: " + catalog.find("R-5KG"));

        Refunds agent = new GuardedRefunds(new RealRefunds(), Set.of("AGENT"));
        System.out.println("protection: " + agent.refund("FC-1", 50_000));
        try { agent.refund("FC-2", 250_000); } catch (SecurityException e) { System.out.println("protection: denied, " + e.getMessage()); }

        SupplierPrices supplier = new SupplierPrices();
        Prices prices = new CachedPrices(supplier, new HashMap<>());
        for (int i = 0; i < 3; i++) prices.price("ATTA-10KG");
        System.out.println("caching: 3 lookups, " + supplier.calls + " supplier call");

        System.out.println("remote: stock = " + new StockStub().stock("MILK-1L"));
    }
}
