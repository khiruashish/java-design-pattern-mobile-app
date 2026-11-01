import java.util.List;

/** Specification: a business rule as an object with isSatisfiedBy(candidate), instead of one finder method per combination. */
public class SpecificationInterfaceDemo {

    record Product(String sku, String category, long pricePaise, boolean organic, int stock) { }

    static final List<Product> CATALOG = List.of(
            new Product("APPLE-1KG", "fruit", 18_000, true, 40),
            new Product("BANANA-6", "fruit", 6_000, false, 0),
            new Product("MILK-1L", "dairy", 6_400, false, 120),
            new Product("PANEER-200", "dairy", 9_000, true, 15));

    // ---------- before: a method for every combination the business asks for ----------
    static List<Product> findInStockOrganicFruit() {                                                // (1)!
        return CATALOG.stream().filter(p -> p.stock() > 0 && p.organic() && p.category().equals("fruit")).toList();
    }
    static List<Product> findInStockDairyUnder100() {
        return CATALOG.stream().filter(p -> p.stock() > 0 && p.category().equals("dairy") && p.pricePaise() < 10_000).toList();
    }

    // ---------- after: each rule once, with a name ----------
    interface Specification<T> {                                                                     // (2)!
        boolean isSatisfiedBy(T candidate);
    }
    record InStock() implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.stock() > 0; } }
    record Organic() implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.organic(); } }
    record InCategory(String c) implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.category().equals(c); } }
    record PriceBelow(long paise) implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.pricePaise() < paise; } }

    @SafeVarargs
    static <T> List<T> findAll(List<T> items, Specification<T>... rules) {                          // (3)!
        return items.stream().filter(i -> java.util.Arrays.stream(rules).allMatch(r -> r.isSatisfiedBy(i))).toList();
    }

    static List<String> skus(List<Product> ps) { return ps.stream().map(Product::sku).toList(); }

    public static void main(String[] args) {
        System.out.println("before: " + skus(findInStockOrganicFruit()) + " " + skus(findInStockDairyUnder100()));
        System.out.println("after:  " + skus(findAll(CATALOG, new InStock(), new Organic(), new InCategory("fruit")))
                + " " + skus(findAll(CATALOG, new InStock(), new InCategory("dairy"), new PriceBelow(10_000))));
        System.out.println("new ask, no new method: " + skus(findAll(CATALOG, new Organic(), new PriceBelow(10_000))));
    }
}
