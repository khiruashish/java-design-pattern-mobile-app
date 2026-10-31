import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/** Strategies as lambdas: Comparator for sorting, and a custom functional interface for discounts. */
public class LambdaStrategyDemo {

    record Product(String name, long priceCents, double rating, int popularity) { }

    @FunctionalInterface
    interface Discount { long apply(long cents); }                                         // (1)!

    static Discount percentOff(int p) { return c -> c - c * p / 100; }                   // (2)!
    static Discount capped(Discount d, long maxOffCents) { return c -> Math.max(d.apply(c), c - maxOffCents); }

    public static void main(String[] args) {
        List<Product> products = new ArrayList<>(List.of(
                new Product("Basmati 5kg", 54_900, 4.6, 900), new Product("Sona Masoori 5kg", 38_900, 4.2, 1_500),
                new Product("Brown rice 1kg", 14_900, 4.8, 300)));

        Comparator<Product> cheapest = Comparator.comparingLong(Product::priceCents);   // (3)!
        Comparator<Product> bestRated = Comparator.comparingDouble(Product::rating).reversed();
        Comparator<Product> popular = Comparator.comparingInt(Product::popularity).reversed().thenComparing(cheapest);

        for (var entry : List.of(java.util.Map.entry("cheapest", cheapest), java.util.Map.entry("best rated", bestRated), java.util.Map.entry("popular", popular))) {
            products.sort(entry.getValue());
            System.out.println(entry.getKey() + ": " + products.stream().map(Product::name).toList());
        }

        Discount festive = capped(percentOff(20), 5_000);                                  // (4)!
        System.out.println("20% capped at $50 on $120.00: " + festive.apply(12_000));
        System.out.println("20% capped at $50 on $400.00: " + festive.apply(40_000));

        UnaryOperator<String> normalise = s -> s.strip().toLowerCase();                   // JDK functional interfaces work too
        System.out.println("search text strategy: '" + normalise.apply("  Basmati ") + "'");
    }
}
