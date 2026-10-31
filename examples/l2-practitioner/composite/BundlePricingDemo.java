import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Production-grade Composite for bundle pricing:
 * - immutable sealed tree (records), so no cycles and safe sharing between threads
 * - validated at build time: max depth, no empty bundles, no duplicate SKUs inside one tree
 * - money in BigDecimal; discounts per bundle with a cap; rounding once, at the end
 * - availability = every leaf available; itemised receipt lines for the customer and for support
 */
public class BundlePricingDemo {

    sealed interface Line permits Sku, Bundle { String name(); }
    record Sku(String code, String name, BigDecimal price, boolean available) implements Line { }
    record Bundle(String name, int discountPercent, BigDecimal maxDiscount, List<Line> lines) implements Line {
        Bundle {
            lines = List.copyOf(lines);                                                    // (1)!
            if (lines.isEmpty()) throw new IllegalArgumentException("bundle '" + name + "' is empty");
            if (discountPercent < 0 || discountPercent > 50) throw new IllegalArgumentException("discount out of range in '" + name + "'");
        }
    }

    static final int MAX_DEPTH = 4;

    static Line validate(Line root) {                                                      // (2)!
        Set<String> seen = new HashSet<>();
        check(root, 1, seen);
        return root;
    }
    private static void check(Line l, int depth, Set<String> seen) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("bundles nested deeper than " + MAX_DEPTH + " at '" + l.name() + "'");
        switch (l) {
            case Sku s -> { if (!seen.add(s.code())) throw new IllegalArgumentException("SKU " + s.code() + " appears twice"); }
            case Bundle b -> b.lines().forEach(c -> check(c, depth + 1, seen));
        }
    }

    static BigDecimal price(Line l) {                                                      // (3)!
        return switch (l) {
            case Sku s -> s.price();
            case Bundle b -> {
                BigDecimal subtotal = b.lines().stream().map(BundlePricingDemo::price).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(b.discountPercent())).divide(BigDecimal.valueOf(100));
                yield subtotal.subtract(discount.min(b.maxDiscount()));
            }
        };
    }

    static boolean available(Line l) {
        return switch (l) {
            case Sku s -> s.available();
            case Bundle b -> b.lines().stream().allMatch(BundlePricingDemo::available);
        };
    }

    static List<String> receipt(Line l, String indent) {                                   // (4)!
        List<String> out = new ArrayList<>();
        switch (l) {
            case Sku s -> out.add(String.format("%-28s %9s%s", indent + s.name(), s.price().setScale(2, RoundingMode.HALF_EVEN), s.available() ? "" : "  OUT OF STOCK"));
            case Bundle b -> {
                out.add(indent + b.name() + (b.discountPercent() > 0 ? " (" + b.discountPercent() + "% off, max " + b.maxDiscount() + ")" : ""));
                b.lines().forEach(c -> out.addAll(receipt(c, indent + "  ")));
                out.add(String.format("%-28s %9s", indent + "= " + b.name(), price(b).setScale(2, RoundingMode.HALF_EVEN)));
            }
        }
        return out;
    }

    static Line nested(int levels) {
        Line l = sku("X", "x", "1");
        for (int i = levels; i >= 1; i--) l = new Bundle("L" + i, 0, BigDecimal.ZERO, List.of(l));
        return l;
    }

    static Sku sku(String code, String name, String price) { return new Sku(code, name, new BigDecimal(price), true); }

    public static void main(String[] args) {
        Line diwali = validate(new Bundle("Diwali hamper", 10, new BigDecimal("150"), List.of(
                new Bundle("Sweets box", 5, new BigDecimal("40"), List.of(sku("S1", "Kaju katli 500g", "450.00"), sku("S2", "Soan papdi 250g", "120.00"))),
                new Bundle("Dry fruits", 0, BigDecimal.ZERO, List.of(sku("D1", "Almonds 200g", "280.00"), sku("D2", "Cashews 200g", "310.00"))),
                sku("G1", "Diya set", "199.00"))));
        receipt(diwali, "").forEach(System.out::println);
        System.out.println("payable: Rs " + price(diwali).setScale(2, RoundingMode.HALF_EVEN) + ", available: " + available(diwali));

        Line soldOut = new Bundle("Tea time", 0, BigDecimal.ZERO, List.of(sku("T1", "Tea 250g", "140"), new Sku("B1", "Biscuits", new BigDecimal("30"), false)));
        System.out.println("Tea time available: " + available(soldOut));

        for (Runnable bad : List.<Runnable>of(
                () -> validate(new Bundle("Dup", 0, BigDecimal.ZERO, List.of(sku("S1", "a", "1"), new Bundle("Inner", 0, BigDecimal.ZERO, List.of(sku("S1", "a", "1")))))),
                () -> new Bundle("Empty", 0, BigDecimal.ZERO, List.of()),
                () -> validate(nested(5)))) {                                         // (5)!
            try { bad.run(); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
