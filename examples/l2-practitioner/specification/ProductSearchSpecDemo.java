import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production: FreshCart product search. Query parameters from the app become ONE specification tree that is
 * (a) rendered to parameterised SQL for the database and (b) evaluated in memory against the cached hot catalogue.
 * - only whitelisted fields; values validated and typed at the boundary
 * - LIKE wildcards in user text are escaped; values are always bind parameters
 * - limits on filter count; empty filter = "everything" (an AllOf with no children), not a crash
 * - a check that SQL and in-memory agree, run in tests against fixtures
 */
public class ProductSearchSpecDemo {

    record Product(String sku, String name, String category, long pricePaise, boolean organic, int stock) { }

    sealed interface Spec permits Eq, Between, NameContains, Flag, AllOf, AnyOf, Not { }
    record Eq(String field, String value) implements Spec { }
    record Between(String field, long min, long max) implements Spec { }
    record NameContains(String text) implements Spec { }
    record Flag(String field) implements Spec { }
    record AllOf(List<Spec> parts) implements Spec { }
    record AnyOf(List<Spec> parts) implements Spec { }
    record Not(Spec spec) implements Spec { }

    static final Set<String> CATEGORIES = Set.of("fruit", "vegetables", "dairy", "bakery");
    static final int MAX_FILTERS = 6;

    static Spec fromRequest(Map<String, String> q) {                                                   // (1)!
        List<Spec> parts = new ArrayList<>();
        for (String key : q.keySet())
            if (!Set.of("category", "min_price", "max_price", "q", "organic", "in_stock", "exclude_category").contains(key))
                throw new IllegalArgumentException("unknown filter '" + key + "'");
        if (q.size() > MAX_FILTERS) throw new IllegalArgumentException("too many filters");
        if (q.containsKey("category")) {
            List<Spec> any = new ArrayList<>();
            for (String c : q.get("category").split(",")) any.add(new Eq("category", category(c)));
            parts.add(any.size() == 1 ? any.get(0) : new AnyOf(any));
        }
        if (q.containsKey("min_price") || q.containsKey("max_price")) {
            long min = rupees(q.getOrDefault("min_price", "0")), max = rupees(q.getOrDefault("max_price", "100000"));
            if (min > max) throw new IllegalArgumentException("min_price is above max_price");
            parts.add(new Between("price_paise", min, max));
        }
        if (q.containsKey("q")) {
            String text = q.get("q").trim();
            if (text.length() < 2 || text.length() > 40) throw new IllegalArgumentException("search text must be 2 to 40 characters");
            parts.add(new NameContains(text.toLowerCase()));
        }
        if ("true".equals(q.get("organic"))) parts.add(new Flag("organic"));
        if ("true".equals(q.get("in_stock"))) parts.add(new Flag("in_stock"));
        if (q.containsKey("exclude_category")) parts.add(new Not(new Eq("category", category(q.get("exclude_category")))));
        return new AllOf(List.copyOf(parts));
    }
    static String category(String c) { String v = c.trim().toLowerCase(); if (!CATEGORIES.contains(v)) throw new IllegalArgumentException("unknown category '" + c + "'"); return v; }
    static long rupees(String s) { try { long r = Long.parseLong(s.trim()); if (r < 0) throw new NumberFormatException(); return r * 100; } catch (NumberFormatException e) { throw new IllegalArgumentException("bad price '" + s + "'"); } }

    static String toSql(Spec s, List<Object> params) {                                                 // (2)!
        return switch (s) {
            case Eq e -> e.field() + " = ?" + bind(params, e.value());
            case Between b -> b.field() + " BETWEEN ? AND ?" + bind(params, b.min()) + bind(params, b.max());
            case NameContains n -> "LOWER(name) LIKE ? ESCAPE '\\'" + bind(params, "%" + escapeLike(n.text()) + "%");
            case Flag f -> f.field().equals("in_stock") ? "stock > 0" : f.field() + " = TRUE";
            case AllOf a -> a.parts().isEmpty() ? "TRUE" : join(a.parts(), " AND ", params);
            case AnyOf a -> a.parts().isEmpty() ? "FALSE" : join(a.parts(), " OR ", params);
            case Not n -> "NOT (" + toSql(n.spec(), params) + ")";
        };
    }
    static String bind(List<Object> params, Object v) { params.add(v); return ""; }
    static String join(List<Spec> parts, String op, List<Object> params) {
        List<String> sql = new ArrayList<>();
        for (Spec p : parts) sql.add(toSql(p, params));
        return parts.size() == 1 ? sql.get(0) : "(" + String.join(op, sql) + ")";
    }
    static String escapeLike(String text) {                                                         // (3)!
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static boolean test(Spec s, Product p) {                                                        // (4)!
        return switch (s) {
            case Eq e -> p.category().equals(e.value());
            case Between b -> p.pricePaise() >= b.min() && p.pricePaise() <= b.max();
            case NameContains n -> p.name().toLowerCase().contains(n.text());
            case Flag f -> f.field().equals("in_stock") ? p.stock() > 0 : p.organic();
            case AllOf a -> a.parts().stream().allMatch(x -> test(x, p));
            case AnyOf a -> a.parts().stream().anyMatch(x -> test(x, p));
            case Not n -> !test(n.spec(), p);
        };
    }

    static final List<Product> HOT_CATALOG = List.of(
            new Product("APL", "Shimla Apple 1kg", "fruit", 18_000, true, 40),
            new Product("BAN", "Banana (6)", "fruit", 6_000, false, 0),
            new Product("MLK", "Toned Milk 1L", "dairy", 6_400, false, 120),
            new Product("PNR", "Malai Paneer 200g", "dairy", 9_000, true, 15),
            new Product("SPN", "Spinach 100% organic", "vegetables", 3_000, true, 25),
            new Product("BRD", "Multigrain Bread", "bakery", 4_500, false, 8));

    static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static void main(String[] args) {
        Spec spec = fromRequest(params("category", "fruit,dairy", "max_price", "100", "in_stock", "true"));
        List<Object> binds = new ArrayList<>();
        System.out.println("WHERE " + toSql(spec, binds));
        System.out.println("binds " + binds);
        System.out.println("cache hit: " + HOT_CATALOG.stream().filter(p -> test(spec, p)).map(Product::sku).toList());

        Spec percent = fromRequest(params("q", "100%"));
        List<Object> b2 = new ArrayList<>();
        System.out.println("WHERE " + toSql(percent, b2) + "  binds " + b2 + "  memory " + HOT_CATALOG.stream().filter(p -> test(percent, p)).map(Product::sku).toList());

        Spec none = fromRequest(params());
        System.out.println("no filters: WHERE " + toSql(none, new ArrayList<>()) + ", " + HOT_CATALOG.stream().filter(p -> test(none, p)).count() + " products");

        for (Map<String, String> bad : List.of(params("category", "fruit' --"), params("min_price", "500", "max_price", "100"),
                                               params("sort", "price"), params("q", "a")))
            try { fromRequest(bad); } catch (IllegalArgumentException e) { System.out.println("400 Bad Request: " + e.getMessage()); }   // (5)!
    }
}
