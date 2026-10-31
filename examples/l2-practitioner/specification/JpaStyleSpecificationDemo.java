import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How Spring Data JPA uses Specification: the spec doesn't filter objects, it BUILDS A QUERY (a JPA Predicate via
 * CriteriaBuilder) so filtering happens in the database. This single-file model translates a spec to parameterised
 * SQL the same way, and can also run in memory, so both can be compared.
 */
public class JpaStyleSpecificationDemo {

    record Product(String sku, String category, long pricePaise, boolean organic) { }

    /** Collects the SQL fragment and bind parameters; plays the role of CriteriaBuilder. */
    static final class Query {
        final List<Object> params = new ArrayList<>();
        String param(Object v) { params.add(v); return "?"; }
    }

    sealed interface Spec permits Eq, Lt, IsTrue, And, Or, Not {
        String toSql(Query q);                                                                    // (1)!
        boolean test(Map<String, Object> row);
        default Spec and(Spec o) { return new And(this, o); }
        default Spec or(Spec o) { return new Or(this, o); }
    }
    record Eq(String column, Object value) implements Spec {
        public String toSql(Query q) { return column + " = " + q.param(value); }                   // (2)!
        public boolean test(Map<String, Object> r) { return r.get(column).equals(value); }
    }
    record Lt(String column, long value) implements Spec {
        public String toSql(Query q) { return column + " < " + q.param(value); }
        public boolean test(Map<String, Object> r) { return (Long) r.get(column) < value; }
    }
    record IsTrue(String column) implements Spec {
        public String toSql(Query q) { return column + " = TRUE"; }
        public boolean test(Map<String, Object> r) { return (Boolean) r.get(column); }
    }
    record And(Spec a, Spec b) implements Spec {
        public String toSql(Query q) { return "(" + a.toSql(q) + " AND " + b.toSql(q) + ")"; }
        public boolean test(Map<String, Object> r) { return a.test(r) && b.test(r); }
    }
    record Or(Spec a, Spec b) implements Spec {
        public String toSql(Query q) { return "(" + a.toSql(q) + " OR " + b.toSql(q) + ")"; }
        public boolean test(Map<String, Object> r) { return a.test(r) || b.test(r); }
    }
    record Not(Spec s) implements Spec {
        public String toSql(Query q) { return "NOT " + s.toSql(q); }
        public boolean test(Map<String, Object> r) { return !s.test(r); }
    }

    // reusable, named specs, like static factory methods in a ProductSpecs class
    static Spec inCategory(String c) { return new Eq("category", c); }                               // (3)!
    static Spec cheaperThan(long paise) { return new Lt("price_paise", paise); }
    static Spec organic() { return new IsTrue("organic"); }

    static Map<String, Object> row(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", p.sku()); m.put("category", p.category()); m.put("price_paise", p.pricePaise()); m.put("organic", p.organic());
        return m;
    }

    public static void main(String[] args) {
        Spec spec = organic().and(inCategory("fruit").or(cheaperThan(7_000)));
        Query q = new Query();
        System.out.println("SELECT * FROM product WHERE " + spec.toSql(q));
        System.out.println("params: " + q.params);

        String hostile = "fruit' OR '1'='1";
        Query q2 = new Query();
        System.out.println("hostile input stays a parameter: " + inCategory(hostile).toSql(q2) + "  params=" + q2.params);   // (4)!

        List<Product> table = List.of(new Product("APPLE", "fruit", 18_000, true), new Product("MILK", "dairy", 6_400, false),
                                      new Product("CURD", "dairy", 4_000, true), new Product("PANEER", "dairy", 9_000, true));
        System.out.println("in memory: " + table.stream().filter(p -> spec.test(row(p))).map(Product::sku).toList());
    }
}
