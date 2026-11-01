import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Realistic Command: a category manager prepares a bulk price change, previews it, applies it, and can undo the whole batch. */
public class BulkPriceChangeDemo {

    static final Map<String, Long> prices = new LinkedHashMap<>();
    static { prices.put("MANGO-1KG", 18_000L); prices.put("APPLE-1KG", 22_000L); prices.put("BANANA-12", 6_000L); }

    static final class SetPrice {                                                             // (1)!
        final String sku; final long newPaise; Long oldPaise;
        SetPrice(String sku, long newPaise) { this.sku = sku; this.newPaise = newPaise; }
        String preview() { return sku + ": " + prices.get(sku) / 100 + " -> " + newPaise / 100; }
        void execute() { oldPaise = prices.put(sku, newPaise); }
        void undo() { prices.put(sku, oldPaise); }
    }

    static List<SetPrice> discountCategory(List<String> skus, int percent) {
        List<SetPrice> batch = new ArrayList<>();
        for (String s : skus) batch.add(new SetPrice(s, prices.get(s) * (100 - percent) / 100));
        return batch;
    }

    public static void main(String[] args) {
        List<SetPrice> batch = discountCategory(List.of("MANGO-1KG", "APPLE-1KG", "BANANA-12"), 10);
        System.out.println("preview (nothing changed yet):");
        batch.forEach(c -> System.out.println("  " + c.preview()));                              // (2)!
        batch.forEach(SetPrice::execute);
        System.out.println("applied: " + prices);
        for (int i = batch.size() - 1; i >= 0; i--) batch.get(i).undo();                       // (3)!
        System.out.println("undone:  " + prices);
    }
}
