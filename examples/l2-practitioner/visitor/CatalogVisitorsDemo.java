import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Production-grade visitors over a product tree that includes bundles (Composite):
 * - a validation visitor that collects ALL problems with their path in the tree (not just the first)
 * - a feed-export visitor that produces escaped JSON, recursing into bundles
 * - a pricing visitor using BigDecimal, rounding once at the end
 * - a generic fold, so new operations don't rewrite the recursion
 */
public class CatalogVisitorsDemo {

    sealed interface Product permits Loose, Packaged, Bundle { String sku(); }
    record Loose(String sku, String name, BigDecimal perKg, int grams) implements Product { }
    record Packaged(String sku, String name, BigDecimal price, String barcode) implements Product { }
    record Bundle(String sku, String name, int discountPercent, List<Product> items) implements Product {
        Bundle { items = List.copyOf(items); }
    }

    // ---------- generic fold: one recursion, many operations ----------
    interface Fold<R> {                                                                                 // (1)!
        R loose(Loose l); R packaged(Packaged p); R bundle(Bundle b, List<R> children);
    }
    static <R> R fold(Product p, Fold<R> f) {
        return switch (p) {
            case Loose l -> f.loose(l);
            case Packaged pk -> f.packaged(pk);
            case Bundle b -> f.bundle(b, b.items().stream().map(c -> fold(c, f)).toList());
        };
    }

    // ---------- pricing ----------
    static final Fold<BigDecimal> PRICE = new Fold<>() {                                                // (2)!
        public BigDecimal loose(Loose l) { return l.perKg().multiply(BigDecimal.valueOf(l.grams())).divide(BigDecimal.valueOf(1000)); }
        public BigDecimal packaged(Packaged p) { return p.price(); }
        public BigDecimal bundle(Bundle b, List<BigDecimal> kids) {
            BigDecimal sum = kids.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.subtract(sum.multiply(BigDecimal.valueOf(b.discountPercent())).divide(BigDecimal.valueOf(100)));
        }
    };

    // ---------- JSON export ----------
    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    static final Fold<String> JSON = new Fold<>() {                                                    // (3)!
        public String loose(Loose l) { return "{\"sku\":\"" + esc(l.sku()) + "\",\"name\":\"" + esc(l.name()) + "\",\"grams\":" + l.grams() + "}"; }
        public String packaged(Packaged p) { return "{\"sku\":\"" + esc(p.sku()) + "\",\"name\":\"" + esc(p.name()) + "\",\"barcode\":\"" + p.barcode() + "\"}"; }
        public String bundle(Bundle b, List<String> kids) { return "{\"sku\":\"" + esc(b.sku()) + "\",\"items\":[" + String.join(",", kids) + "]}"; }
    };

    // ---------- validation with paths (needs the path, so it walks itself) ----------
    static List<String> validate(Product root) {                                                      // (4)!
        List<String> problems = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        walk(root, path, problems);
        return problems;
    }
    private static void walk(Product p, Deque<String> path, List<String> problems) {
        path.addLast(p.sku());
        String where = String.join(" > ", path);
        switch (p) {
            case Loose l -> { if (l.grams() <= 0) problems.add(where + ": weight must be positive"); }
            case Packaged pk -> { if (!pk.barcode().matches("\\d{13}")) problems.add(where + ": barcode must be 13 digits"); }
            case Bundle b -> {
                if (b.items().isEmpty()) problems.add(where + ": empty bundle");
                if (b.discountPercent() > 30) problems.add(where + ": discount over 30% needs approval");
                b.items().forEach(c -> walk(c, path, problems));
            }
        }
        path.removeLast();                                                                            // (5)!
    }

    public static void main(String[] args) {
        Product breakfast = new Bundle("B-1", "Breakfast box", 10, List.of(
                new Packaged("P-1", "Bread \"multigrain\"", new BigDecimal("45.00"), "8901000000011"),
                new Packaged("P-2", "Eggs x6", new BigDecimal("84.00"), "8901000000028"),
                new Bundle("B-2", "Fruit bag", 0, List.of(new Loose("L-1", "Banana", new BigDecimal("60.00"), 1_000)))));
        System.out.println("price: Rs " + fold(breakfast, PRICE).setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("json:  " + fold(breakfast, JSON));
        System.out.println("valid: " + validate(breakfast));

        Product broken = new Bundle("B-9", "Mystery box", 40, List.of(
                new Packaged("P-9", "Jam", new BigDecimal("120"), "12345"),
                new Bundle("B-10", "Empty", 0, List.of()),
                new Loose("L-9", "Grapes", new BigDecimal("90"), 0)));
        validate(broken).forEach(p -> System.out.println("  problem: " + p));
    }
}
