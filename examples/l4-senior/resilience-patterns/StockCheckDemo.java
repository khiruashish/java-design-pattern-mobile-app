import java.util.function.IntSupplier;

/** Minimal: a stock check with a time budget and a safe fallback, so checkout never hangs on Inventory. */
public class StockCheckDemo {

    record Reply(int latencyMs, int units) {}

    static String canAddToCart(String sku, java.util.function.Supplier<Reply> inventory, int budgetMs) {
        Reply r = inventory.get();
        if (r.latencyMs() > budgetMs)                                                              // (1)!
            return sku + ": allowed, confirm at packing (inventory slow)";                         // (2)!
        return sku + (r.units() > 0 ? ": allowed (" + r.units() + " left)" : ": out of stock");
    }

    public static void main(String[] args) {
        System.out.println(canAddToCart("MILK-1L", () -> new Reply(40, 12), 150));
        System.out.println(canAddToCart("PANEER-200G", () -> new Reply(30, 0), 150));
        System.out.println(canAddToCart("CURD-500G", () -> new Reply(3000, 5), 150));
    }
}
