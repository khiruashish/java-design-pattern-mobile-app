import java.util.function.ToIntFunction;

/**
 * Minimal: legacy code calls a static method that talks to the old stock database, so it can't be tested. A seam
 * (a parameter with the legacy call as default) makes it testable without changing behaviour for production.
 */
public class LegacySeamDemo {

    static final class LegacyStock {                      // imagine a 2009 class that opens a DB connection
        static int unitsFor(String sku) { return 7; }
    }

    static final class ReorderAdvisor {
        private final ToIntFunction<String> stock;
        ReorderAdvisor() { this(LegacyStock::unitsFor); }                                             // (1)!
        ReorderAdvisor(ToIntFunction<String> stock) { this.stock = stock; }                            // (2)!
        String advise(String sku) { return stock.applyAsInt(sku) < 10 ? "reorder " + sku : "ok " + sku; }
    }

    public static void main(String[] args) {
        System.out.println("production: " + new ReorderAdvisor().advise("PANEER-200G"));
        System.out.println("test with stub stock=25: " + new ReorderAdvisor(sku -> 25).advise("PANEER-200G"));
    }
}
