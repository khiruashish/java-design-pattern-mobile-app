import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

/** Adapting legacy and third-party APIs: old JDK types, nulls, checked exceptions and vendor codes into our model. */
public class LegacyApiAdapterDemo {

    // ---- a legacy inventory library we must keep using ----
    static final class LegacyInventory {
        Enumeration<String> skus() { return new Vector<>(List.of("MILK", "EGGS", "RICE")).elements(); }
        Date lastCount() { return new Date(1_793_520_000_000L); }
        String stockCode(String sku) { return switch (sku) { case "MILK" -> "A"; case "EGGS" -> "L"; default -> null; }; }   // A=available, L=low, null=unknown
        int reserve(String sku, int qty) throws java.io.IOException {
            if (sku.equals("RICE")) throw new java.io.IOException("socket closed");
            return qty;
        }
    }

    enum Stock { AVAILABLE, LOW, UNKNOWN }

    /** Our clean API. */
    interface Inventory {
        Iterator<String> skus();
        java.time.Instant lastCount();
        Stock stock(String sku);
        Optional<Integer> tryReserve(String sku, int qty);
    }

    static final class LegacyInventoryAdapter implements Inventory {
        private final LegacyInventory legacy;
        LegacyInventoryAdapter(LegacyInventory legacy) { this.legacy = legacy; }

        public Iterator<String> skus() { return legacy.skus().asIterator(); }            // (1)!
        public java.time.Instant lastCount() { return legacy.lastCount().toInstant(); }   // (2)!
        public Stock stock(String sku) {
            String code = legacy.stockCode(sku);
            if (code == null) return Stock.UNKNOWN;                                       // (3)!
            return switch (code) { case "A" -> Stock.AVAILABLE; case "L" -> Stock.LOW; default -> Stock.UNKNOWN; };
        }
        public Optional<Integer> tryReserve(String sku, int qty) {
            try { return Optional.of(legacy.reserve(sku, qty)); }
            catch (java.io.IOException e) { return Optional.empty(); }                    // (4)!
        }
    }

    public static void main(String[] args) {
        Inventory inv = new LegacyInventoryAdapter(new LegacyInventory());
        Iterator<String> it = inv.skus();
        while (it.hasNext()) {
            String sku = it.next();
            System.out.println(sku + ": " + inv.stock(sku) + ", reserve 2 -> " + inv.tryReserve(sku, 2).map(String::valueOf).orElse("failed"));
        }
        System.out.println("last count: " + inv.lastCount());
        System.out.println("JDK's own adapters: " + Collections.list(new Vector<>(List.of(1, 2)).elements()) + ", " + java.util.Arrays.asList("a", "b"));
    }
}
