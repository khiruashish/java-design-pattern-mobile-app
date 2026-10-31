# Specification

L2 🔵 Practitioner · ⏱ 35 min read · Prerequisites: [Composite](composite.md), [Interpreter](interpreter.md)

> 📌 **TL;DR**  
> A Specification is a business rule packaged as an object with one question: "does this candidate satisfy me?". Small named rules (`inStock`, `organic`, `cheaperThan(100)`) combine with `and`, `or` and `not` into bigger ones, so you stop writing a finder method for every combination. In memory, `java.util.function.Predicate` is often enough. When the data lives in a database, the specification builds a query instead (Spring Data JPA's `Specification<T>`), so filtering runs in SQL with bind parameters.

## 1. What It Is

**Plain English.** FreshCart's product search has filters: category, price range, organic, in stock, text. Customers combine them freely. Writing `findOrganicFruitInStockUnder100()` for each combination is impossible. Instead, each filter is a small rule object, and the search combines whichever rules the customer chose.

**Definition (Evans and Fowler, "Specifications", 1997; *Domain-Driven Design*, 2003).** "A specification is a predicate that determines if an object does or does not satisfy some criteria." Parts:

- **Specification:** an interface with `isSatisfiedBy(candidate)`.
- **Leaf specifications:** single rules (`InStock`, `PriceBelow(100)`).
- **Composite specifications:** `And`, `Or`, `Not`, built from other specifications (a [Composite](composite.md)).
- **Clients:** use specifications to **select** (filter a collection or query), **validate** (does this object meet the rule?) or **build to order** (describe what to create).

## 2. Why It Exists

Business rules end up copied across services, repositories and UI code, each copy slightly different: "eligible for free delivery" in checkout, in the cart banner and in the marketing email. Repositories grow a method per filter combination. Specification gives each rule one home and one name, lets you combine rules without new code, and lets the same rule drive in-memory checks and database queries.

## 3. Mental Model / Analogy

**Analogy.** A hiring checklist: "5+ years Java" AND ("led a team" OR "open-source maintainer") AND NOT "on non-compete". Each line is reusable on other checklists, and a recruiter can say exactly which line a candidate failed.

**Technical model.** A tree of boolean rules (an [Interpreter](interpreter.md) AST without the text). Evaluating the tree against an object answers in memory; translating the tree produces a SQL `WHERE` clause, an Elasticsearch query or a JPA `Predicate`.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Specification interface

**Description.** One method, `isSatisfiedBy(T candidate)`, and one class (or record) per rule. Rules with parameters (`PriceBelow(10_000)`) are records. Clients filter with any combination of rules instead of calling one finder per combination.

**Advantages / features.** Each rule is named, written once and unit-testable; new combinations need no new code; rules can be passed around, stored and logged.

**Before (finder per combination) and after (specifications):**

**SpecificationInterfaceDemo.java**

```java
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
    static List<Product> findInStockOrganicFruit() {
        return CATALOG.stream().filter(p -> p.stock() > 0 && p.organic() && p.category().equals("fruit")).toList();
    }
    static List<Product> findInStockDairyUnder100() {
        return CATALOG.stream().filter(p -> p.stock() > 0 && p.category().equals("dairy") && p.pricePaise() < 10_000).toList();
    }

    // ---------- after: each rule once, with a name ----------
    interface Specification<T> {
        boolean isSatisfiedBy(T candidate);
    }
    record InStock() implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.stock() > 0; } }
    record Organic() implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.organic(); } }
    record InCategory(String c) implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.category().equals(c); } }
    record PriceBelow(long paise) implements Specification<Product> { public boolean isSatisfiedBy(Product p) { return p.pricePaise() < paise; } }

    @SafeVarargs
    static <T> List<T> findAll(List<T> items, Specification<T>... rules) {
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
```

1. Before: each new combination of filters needs a new method, and the rules (`stock > 0`) are repeated in every one.
2. The interface: a single question about a candidate.
3. A generic finder that accepts any rules; new combinations are new calls, not new methods.

**Run it:** `java SpecificationInterfaceDemo.java`

```text
before: [APPLE-1KG] [MILK-1L, PANEER-200]
after:  [APPLE-1KG] [MILK-1L, PANEER-200]
new ask, no new method: [PANEER-200]
```

| Use of a specification | Question | Example |
|------------------------|----------|---------|
| Selection | Which items match? | Product search |
| Validation | Does this object meet the rule? | Can this order be cancelled? |
| Building to order | What should be created? | Pick a delivery slot that satisfies these rules |

**Use** specifications when the same rules are combined in many ways or used in several places. **Avoid** them for a single, fixed filter; a lambda is clearer.
**Real-world Java:** Spring Data JPA `Specification<T>`, Querydsl `Predicate`/`BooleanExpression`, Hibernate Criteria API.
**Best practice:** name specifications after business terms (`EligibleForFreeDelivery`), not after implementation (`TotalGt499AndKmLt5`). **Gotcha:** rules that need external data (stock from another service) make `isSatisfiedBy` slow and side-effecting; fetch the data first and pass it in.

### and/or/not composition

**Description.** Default methods `and`, `or` and `not` on the interface return new composite specifications, so rules read like the business sentence: `GOLD.or(FREQUENT).and(VERIFIED)`. Adding a `describe()` method makes the composed rule explain itself.

**Advantages / features.** Rules compose without new classes; the description lets you log, display and test the exact rule in force.

**CompositionDemo.java**

```java
import java.util.List;

/** and / or / not as default methods: small named rules combine into bigger ones, and the result can describe itself. */
public class CompositionDemo {

    record Customer(String name, String tier, int ordersLast30Days, boolean emailVerified) { }

    interface Spec<T> {
        boolean isSatisfiedBy(T t);
        String describe();

        default Spec<T> and(Spec<T> other) { return of("(" + describe() + " and " + other.describe() + ")", t -> isSatisfiedBy(t) && other.isSatisfiedBy(t)); }
        default Spec<T> or(Spec<T> other) { return of("(" + describe() + " or " + other.describe() + ")", t -> isSatisfiedBy(t) || other.isSatisfiedBy(t)); }
        default Spec<T> not() { return of("not " + describe(), t -> !isSatisfiedBy(t)); }

        static <T> Spec<T> of(String description, java.util.function.Predicate<T> test) {
            return new Spec<>() {
                public boolean isSatisfiedBy(T t) { return test.test(t); }
                public String describe() { return description; }
            };
        }
    }

    static final Spec<Customer> GOLD = Spec.of("gold tier", c -> c.tier().equals("gold"));
    static final Spec<Customer> FREQUENT = Spec.of("5+ orders in 30 days", c -> c.ordersLast30Days() >= 5);
    static final Spec<Customer> VERIFIED = Spec.of("verified email", Customer::emailVerified);

    static final Spec<Customer> EARLY_ACCESS = GOLD.or(FREQUENT).and(VERIFIED);

    public static void main(String[] args) {
        System.out.println("rule: " + EARLY_ACCESS.describe());
        for (Customer c : List.of(new Customer("Asha", "gold", 1, true), new Customer("Ravi", "silver", 7, true),
                                  new Customer("Meena", "silver", 2, true), new Customer("Kiran", "gold", 9, false)))
            System.out.println("  " + c.name() + ": " + EARLY_ACCESS.isSatisfiedBy(c));
        System.out.println("rule: " + VERIFIED.not().describe() + " -> Kiran " + VERIFIED.not().isSatisfiedBy(new Customer("Kiran", "gold", 9, false)));
    }
}
```

1. Each combinator builds a new specification and a description that shows its grouping.
2. A factory that pairs a test with a human-readable name.
3. The business rule reads almost like the requirement.

**Run it:** `java CompositionDemo.java`

```text
rule: ((gold tier or 5+ orders in 30 days) and verified email)
  Asha: true
  Ravi: true
  Meena: false
  Kiran: false
rule: not verified email -> Kiran true
```

| Combinator | Meaning | Short-circuits |
|------------|---------|----------------|
| `a.and(b)` | Both | Skips `b` if `a` is false |
| `a.or(b)` | Either | Skips `b` if `a` is true |
| `a.not()` | Opposite | n/a |
| `allOf(list)` / `anyOf(list)` | N-ary versions | Yes |

**Use** composition for rules built from reusable parts. **Avoid** deep chains that nobody can read; extract named intermediate specifications.
**Real-world Java:** `Predicate.and/or/negate`, Spring Data `Specification.where(a).and(b)`, Querydsl `BooleanExpression.and/or/not`, Hamcrest `allOf/anyOf/not` matchers.
**Best practice:** put cheap and selective rules first in `and` chains. **Gotcha:** method chaining is left to right, so `a.or(b).and(c)` means `(a or b) and c`, unlike `a || b && c` in Java, which means `a || (b && c)`.

### Predicate&lt;T&gt; as specification

**Description.** `java.util.function.Predicate<T>` has `test`, `and`, `or`, `negate` and `Predicate.not(...)`. For in-memory filtering with streams, it **is** a specification, with no extra code.

**Advantages / features.** Standard, works with streams and collections (`removeIf`), and lambdas are concise.

**PredicateAsSpecDemo.java**

```java
import java.util.List;
import java.util.function.Predicate;

/** java.util.function.Predicate already has and/or/negate: often all the "Specification" you need in memory. */
public class PredicateAsSpecDemo {

    record Slot(String id, int hour, int capacityLeft, boolean express) { }

    static final Predicate<Slot> HAS_ROOM = s -> s.capacityLeft() > 0;
    static final Predicate<Slot> EVENING = s -> s.hour() >= 17;
    static final Predicate<Slot> EXPRESS = Slot::express;

    public static void main(String[] args) {
        List<Slot> slots = List.of(new Slot("S-09", 9, 4, false), new Slot("S-18", 18, 0, false),
                                   new Slot("S-19", 19, 2, true), new Slot("S-20", 20, 6, false));

        Predicate<Slot> eveningStandard = HAS_ROOM.and(EVENING).and(Predicate.not(EXPRESS));
        System.out.println("evening, standard, with room: " + slots.stream().filter(eveningStandard).map(Slot::id).toList());
        System.out.println("express or morning: " + slots.stream().filter(EXPRESS.or(EVENING.negate())).map(Slot::id).toList());

        System.out.println("can it explain itself? toString() is a lambda class name: " + eveningStandard.toString().contains("$$Lambda"));
    }
}
```

1. Named constants make each rule reusable and testable.
2. Composition with `and`, `or`, `negate` and `Predicate.not` (Java 11).
3. The limitation: a lambda can't describe itself, can't be translated to SQL, and can't say which part failed.

**Run it:** `java PredicateAsSpecDemo.java`

```text
evening, standard, with room: [S-20]
express or morning: [S-09, S-19]
can it explain itself? toString() is a lambda class name: true
```

| Need | `Predicate<T>` | Custom specification |
|------|----------------|----------------------|
| In-memory filtering | ✅ | ✅ |
| Describe / explain | ❌ | ✅ |
| Translate to SQL or search | ❌ (opaque lambda) | ✅ (inspectable tree) |
| Serialise / store | ❌ | ✅ (records) |
| Zero extra code | ✅ | ❌ |

**Use** `Predicate` for in-memory rules. **Avoid** it when the rule must run in a database or explain itself.
**Real-world Java:** `Collection.removeIf`, `Stream.filter`, `Predicate.isEqual`, `Pattern.asPredicate()`.
**Best practice:** start with `Predicate`, and switch to a custom specification type when you need descriptions or query translation. **Gotcha:** filtering a large table in memory with a `Predicate` after `findAll()` loads every row; push filters to the database.

### Specifications in Spring Data JPA

**Description.** Spring Data JPA's `Specification<T>` doesn't test objects. Its method `toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb)` **builds** a JPA criteria predicate, which Hibernate turns into SQL. Repositories extend `JpaSpecificationExecutor<T>` to get `findAll(spec)`, `findAll(spec, pageable)` and `count(spec)`. Typical code:

```java
public final class ProductSpecs {
    public static Specification<Product> inCategory(String c) {
        return (root, query, cb) -> cb.equal(root.get("category"), c);
    }
    public static Specification<Product> cheaperThan(long paise) {
        return (root, query, cb) -> cb.lessThan(root.get("pricePaise"), paise);
    }
    public static Specification<Product> organic() {
        return (root, query, cb) -> cb.isTrue(root.get("organic"));
    }
}

List<Product> result = productRepository.findAll(
        organic().and(inCategory("fruit").or(cheaperThan(7_000))));
```

The runnable model below does the same translation by hand so you can see the SQL and the bind parameters, and runs the same tree in memory.

**Advantages / features.** Filters run in the database with indexes; values become bind parameters (safe from SQL injection); specifications combine with paging and sorting.

**JpaStyleSpecificationDemo.java**

```java
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
        String toSql(Query q);
        boolean test(Map<String, Object> row);
        default Spec and(Spec o) { return new And(this, o); }
        default Spec or(Spec o) { return new Or(this, o); }
    }
    record Eq(String column, Object value) implements Spec {
        public String toSql(Query q) { return column + " = " + q.param(value); }
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
    static Spec inCategory(String c) { return new Eq("category", c); }
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
        System.out.println("hostile input stays a parameter: " + inCategory(hostile).toSql(q2) + "  params=" + q2.params);

        List<Product> table = List.of(new Product("APPLE", "fruit", 18_000, true), new Product("MILK", "dairy", 6_400, false),
                                      new Product("CURD", "dairy", 4_000, true), new Product("PANEER", "dairy", 9_000, true));
        System.out.println("in memory: " + table.stream().filter(p -> spec.test(row(p))).map(Product::sku).toList());
    }
}
```

1. Each specification knows how to render itself into a query, like `toPredicate` does with `CriteriaBuilder`.
2. Values are never concatenated into SQL; they're added as bind parameters.
3. Reusable specs as static factory methods, the usual Spring style.
4. A hostile value stays data, not SQL.

**Run it:** `java JpaStyleSpecificationDemo.java`

```text
SELECT * FROM product WHERE (organic = TRUE AND (category = ? OR price_paise < ?))
params: [fruit, 7000]
hostile input stays a parameter: category = ?  params=[fruit' OR '1'='1]
in memory: [APPLE, CURD]
```

| Option | Type safety | Dynamic filters | Notes |
|--------|-------------|-----------------|-------|
| Derived query methods (`findByCategoryAndOrganic`) | ✅ | ❌ | One method per combination |
| `@Query` JPQL | ❌ (strings) | ❌ | Good for fixed, complex queries |
| `Specification<T>` + Criteria API | ✅ with JPA metamodel | ✅ | Verbose, but composable |
| Querydsl | ✅ (generated Q-classes) | ✅ | Fluent; extra build step |
| Query by Example | ✅ | Equality only | Simple forms |

**Use** JPA specifications for search screens with many optional filters. **Avoid** them for fixed reports; a readable `@Query` is simpler.
**Real-world Java:** Spring Data JPA `JpaSpecificationExecutor`; Spring Data MongoDB's `Criteria`; Elasticsearch's `BoolQueryBuilder` plays the same role.
**Best practice:** use the JPA static metamodel (`Product_.category`) instead of strings like `root.get("category")`. **Gotcha:** joins inside specifications can duplicate rows; call `query.distinct(true)`, and know that count queries for paging reuse the same specification.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop offers free delivery on orders of Rs 499 or more, or on a customer's first order, within 5 km. Checkout, the cart banner and the receipt must agree.

**FreeDeliverySpecDemo.java**

```java
import java.util.List;

/** Minimal: QuickDrop's free-delivery rule as one named, reusable specification. */
public class FreeDeliverySpecDemo {

    record Order(String id, long totalPaise, double distanceKm, boolean firstOrder) { }

    interface Spec<T> {
        boolean isSatisfiedBy(T t);
        default Spec<T> and(Spec<T> o) { return t -> isSatisfiedBy(t) && o.isSatisfiedBy(t); }
        default Spec<T> or(Spec<T> o) { return t -> isSatisfiedBy(t) || o.isSatisfiedBy(t); }
    }

    static final Spec<Order> FREE_DELIVERY =
            ((Spec<Order>) o -> o.totalPaise() >= 49_900).or(Order::firstOrder)
                    .and(o -> o.distanceKm() <= 5.0);

    public static void main(String[] args) {
        for (Order o : List.of(new Order("Q1", 52_000, 3.2, false), new Order("Q2", 21_000, 2.0, true),
                               new Order("Q3", 21_000, 2.0, false), new Order("Q4", 80_000, 7.5, false)))
            System.out.println(o.id() + " free delivery: " + FREE_DELIVERY.isSatisfiedBy(o));
    }
}
```

1. The rule is defined once, with a business name, and composed from three conditions.
2. Every screen asks the same question of the same object.

**Run it:** `java FreeDeliverySpecDemo.java`

**Output:**

```text
Q1 free delivery: true
Q2 free delivery: true
Q3 free delivery: false
Q4 free delivery: false
```

**What to notice:**

- Q4 is a big order but too far away; the `and` with distance applies to both branches of the `or`.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** dispatch assigns each order to the first eligible rider. A rider must be on shift, have bag space, have a cold box if the order has chilled items, and be within 3 km. When an order waits, support needs to know why each nearby rider was skipped.

**RiderAssignmentDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Realistic: FreshCart assigns orders to riders. A rider is eligible if on shift AND has bag capacity AND
 * (has a cold box OR the order needs no cold chain) AND is within 3 km. Dispatch support must see WHY a rider was skipped.
 */
public class RiderAssignmentDemo {

    record Rider(String name, boolean onShift, int bagsFree, boolean coldBox, double kmAway) { }
    record Order(String id, int bags, boolean needsCold) { }

    /** A specification over (rider, order) that can say which leaf rules failed. */
    record Rule(String name, java.util.function.BiPredicate<Rider, Order> test) {
        List<String> failures(Rider r, Order o) { return test.test(r, o) ? List.of() : List.of(name); }
    }

    record AllOf(List<Rule> rules) {
        boolean isSatisfiedBy(Rider r, Order o) { return rules.stream().allMatch(x -> x.test().test(r, o)); }
        List<String> whyNot(Rider r, Order o) {
            List<String> out = new ArrayList<>();
            rules.forEach(x -> out.addAll(x.failures(r, o)));
            return out;
        }
    }

    static final Rule ON_SHIFT = new Rule("not on shift", (r, o) -> r.onShift());
    static final Rule CAPACITY = new Rule("no bag space", (r, o) -> r.bagsFree() >= o.bags());
    static final Rule COLD_CHAIN = new Rule("no cold box for chilled items", (r, o) -> r.coldBox() || !o.needsCold());
    static final Rule NEARBY = new Rule("more than 3 km away", (r, o) -> r.kmAway() <= 3.0);

    static final AllOf CAN_TAKE = new AllOf(List.of(ON_SHIFT, CAPACITY, COLD_CHAIN, NEARBY));

    public static void main(String[] args) {
        Order order = new Order("FC-3001", 2, true);
        List<Rider> riders = List.of(new Rider("Arjun", true, 3, false, 1.2), new Rider("Bina", true, 2, true, 2.5),
                                     new Rider("Chetan", false, 4, true, 4.1), new Rider("Divya", true, 1, true, 0.8));
        for (Rider r : riders) {
            List<String> why = CAN_TAKE.whyNot(r, order);
            System.out.println(r.name() + ": " + (why.isEmpty() ? "eligible" : "skipped " + why));
        }
        Predicate<Rider> eligible = r -> CAN_TAKE.isSatisfiedBy(r, order);
        System.out.println("assign to: " + riders.stream().filter(eligible).map(Rider::name).findFirst().orElse("nobody, queue the order"));
    }
}
```

1. An `AllOf` specification that can both answer and explain.
2. A rule that depends on the order as well as the rider: "cold box, or the order doesn't need one".
3. All failures are reported, not just the first, so support sees the full picture.

**Run it:** `java RiderAssignmentDemo.java`

**Output:**

```text
Arjun: skipped [no cold box for chilled items]
Bina: eligible
Chetan: skipped [not on shift, more than 3 km away]
Divya: skipped [no bag space]
assign to: Bina
```

**What to notice:**

- Chetan failed two rules, and both are listed.
- The same `CAN_TAKE` drives assignment (`isSatisfiedBy`) and support tooling (`whyNot`).

### Example 3: Production-grade

**Scenario:** FreshCart's product search API takes query parameters (`category=fruit,dairy&max_price=100&in_stock=true&q=...`). The same filter must run as SQL against the database and in memory against a cached "hot" catalogue of top sellers. Requirements: no SQL injection, user text with `%` or `_` searched literally, unknown or invalid filters rejected with a 400, no filter meaning "everything", and SQL and in-memory results provably consistent.

**ProductSearchSpecDemo.java**

```java
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

    static Spec fromRequest(Map<String, String> q) {
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

    static String toSql(Spec s, List<Object> params) {
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
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static boolean test(Spec s, Product p) {
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
            try { fromRequest(bad); } catch (IllegalArgumentException e) { System.out.println("400 Bad Request: " + e.getMessage()); }
    }
}
```

1. The boundary: only whitelisted filters, typed and validated values, and a limit on the number of filters. Multiple categories become an `AnyOf`.
2. One interpreter over the tree produces SQL; every value goes into the bind list.
3. User text is escaped so `%` and `_` are matched literally, not as wildcards.
4. A second interpreter over the same tree filters in memory.
5. Invalid requests fail with messages the API can return as 400 responses.

**Run it:** `java ProductSearchSpecDemo.java`

**Output:**

```text
WHERE ((category = ? OR category = ?) AND price_paise BETWEEN ? AND ? AND stock > 0)
binds [fruit, dairy, 0, 10000]
cache hit: [MLK, PNR]
WHERE LOWER(name) LIKE ? ESCAPE '\'  binds [%100\%%]  memory [SPN]
no filters: WHERE TRUE, 6 products
400 Bad Request: unknown category 'fruit' --'
400 Bad Request: min_price is above max_price
400 Bad Request: unknown filter 'sort'
400 Bad Request: search text must be 2 to 40 characters
```

**What to notice:**

- The search for `100%` matched only "Spinach 100% organic", because `%` was escaped.
- An empty request became `WHERE TRUE`, not an exception or an empty result.
- `fruit' --` was rejected at the whitelist, and would have been a bind parameter anyway.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Reuse | Each rule written once, used everywhere | Indirection: logic is spread over small classes |
| Flexibility | Any combination without new methods | Easy to build queries the database can't index |
| Testability | Each rule tested alone | Combined behaviour still needs tests |
| Explainability | Composite specs can say which rule failed | Only with a custom type, not plain lambdas |
| Persistence | Translate to SQL / search queries | Two interpreters (SQL and memory) can drift apart |

## 8. When to Use / When NOT to Use

**Use it when:**

- users or business people combine filters freely (search screens, segments, eligibility);
- the same rule appears in several places (checkout, banner, email);
- you need to explain decisions (why wasn't this rider assigned?).

**Don't use it when:**

- there's one fixed filter: a lambda or a query method is clearer;
- the query is complex and fixed (reports): write SQL;
- performance needs hand-tuned SQL per combination.

## 9. Best Practices

1. **Name specifications in business language.** *(Consensus, from DDD.)*
2. **Start with `Predicate`;** introduce a custom type when you need descriptions or query translation. *(Opinion, widely shared.)*
3. **Push filtering to the database** with JPA specifications or Querydsl; don't `findAll()` and filter in memory. *(Consensus.)*
4. **Always bind values;** never concatenate them into queries. *(Consensus.)*
5. **Whitelist fields and validate values** at the API boundary. *(Consensus.)*
6. **Test that SQL and in-memory interpretations agree** on fixtures if you have both. *(Opinion, widely shared.)*
7. **Make specifications immutable** so they can be shared and cached. *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Filtering in memory after loading everything (production failure mode)**  
> **Symptom:** product search took 8 seconds and the service ran out of memory during a sale.
> **Root cause:** the repository returned `findAll()` and the code applied a `Predicate` specification in Java, loading 400,000 products per request.
> **Fix:** translate specifications to queries (JPA `Specification`, Querydsl) so the database filters with indexes, and page results.

> 🚨 **Building SQL by string concatenation**  
> **Symptom:** a search for `fruit' OR '1'='1` returned the whole catalogue, including hidden products.
> **Root cause:** the "specification" rendered `"category = '" + value + "'"`.
> **Fix:** bind parameters for every value; whitelist column names, which can't be bound.

> 🚨 **Unescaped LIKE wildcards**  
> **Symptom:** searching for `_` returned every product; searching for `100%` returned many unrelated items.
> **Root cause:** user text was put inside `%...%` without escaping `%` and `_`.
> **Fix:** escape `\`, `%` and `_` and declare `ESCAPE '\'`.

> 🚨 **Two interpreters that disagree**  
> **Symptom:** a product appeared in cached search results but not in database results.
> **Root cause:** in-memory text matching was case-insensitive but SQL `LIKE` was case-sensitive on that column.
> **Fix:** define semantics once (lower-case both sides), and test that both interpreters return the same results on fixtures.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Max filters per request | 6 (example) | Bounds query complexity | Advanced search screens | Too high allows expensive queries |
| Search text length | 2–40 chars | Avoids `LIKE '%a%'` scans and abuse | Product names are longer | Short terms scan the whole table |
| Page size (`Pageable`) | 20 | Limits rows per query | Infinite scroll, exports | Large pages load slowly |
| `spring.jpa.properties.hibernate.query.in_clause_parameter_padding` | false | Pads `IN` lists to reuse query plans | Many `AnyOf` sizes | None significant |
| Indexes on filter columns | none | Makes filters fast | Columns used in common specs | Slower writes, storage |

## 12. Security Considerations

- **Bind every value**; specifications that concatenate strings are SQL injection.
- **Whitelist fields**: never let a client choose a column name (`root.get(userInput)`), which can expose hidden fields or cause errors that leak schema.
- **Enforce tenant and visibility rules inside the repository**, by always adding `and(visibleTo(user))`, so no combination of filters can bypass them.
- **Escape `LIKE` wildcards** in user text.
- **Limit filter count and text length** to stop expensive queries (a form of denial of service).

## 13. Performance & Scalability

In memory, specifications cost a virtual call per rule per item; order `and` chains so cheap, selective rules run first. In the database, performance depends on the generated SQL: make sure common filters hit indexes, avoid leading-wildcard `LIKE '%text%'` on large tables (use full-text search), and watch for `OR` across different columns, which can defeat index use. Paging with `count(spec)` runs the filter twice; cache counts or use keyset pagination for deep pages.

## 14. Testing It

Test that SQL and binds line up, the in-memory results, escaping, validation, descriptions and explanations:

**ProductSearchSpecTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class ProductSearchSpecTest {

    static List<Map<String, String>> requests() {
        return List.of(
                Map.of(),
                Map.of("category", "fruit,dairy", "max_price", "100", "in_stock", "true"),
                Map.of("organic", "true", "exclude_category", "dairy"),
                Map.of("q", "milk", "min_price", "50"),
                Map.of("q", "100%"));
    }

    @ParameterizedTest
    @MethodSource("requests")
    void everyPlaceholderHasExactlyOneBindValue(Map<String, String> request) {
        var spec = ProductSearchSpecDemo.fromRequest(request);
        List<Object> binds = new ArrayList<>();
        String sql = ProductSearchSpecDemo.toSql(spec, binds);
        assertEquals(sql.chars().filter(c -> c == '?').count(), binds.size());
    }

    @Test
    void inMemoryMatchesTheExpectedProducts() {
        var spec = ProductSearchSpecDemo.fromRequest(Map.of("organic", "true", "exclude_category", "dairy"));
        List<String> skus = ProductSearchSpecDemo.HOT_CATALOG.stream().filter(p -> ProductSearchSpecDemo.test(spec, p)).map(ProductSearchSpecDemo.Product::sku).toList();
        assertEquals(List.of("APL", "SPN"), skus);
    }

    @Test
    void likeWildcardsInUserTextAreEscaped() {
        assertEquals("100\\%", ProductSearchSpecDemo.escapeLike("100%"));
        assertEquals("a\\_b", ProductSearchSpecDemo.escapeLike("a_b"));
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProductSearchSpecDemo.fromRequest(Map.of("category", "toys")));
        assertThrows(IllegalArgumentException.class, () -> ProductSearchSpecDemo.fromRequest(Map.of("min_price", "-1")));
        assertThrows(IllegalArgumentException.class, () -> ProductSearchSpecDemo.fromRequest(Map.of("sort", "price")));
    }

    @Test
    void composedSpecsDescribeThemselves() {
        assertEquals("((gold tier or 5+ orders in 30 days) and verified email)", CompositionDemo.EARLY_ACCESS.describe());
        assertFalse(CompositionDemo.EARLY_ACCESS.isSatisfiedBy(new CompositionDemo.Customer("K", "gold", 9, false)));
    }

    @Test
    void riderRulesExplainEveryFailure() {
        var why = RiderAssignmentDemo.CAN_TAKE.whyNot(new RiderAssignmentDemo.Rider("C", false, 4, true, 4.1),
                                                      new RiderAssignmentDemo.Order("O", 1, false));
        assertEquals(List.of("not on shift", "more than 3 km away"), why);
        assertTrue(RiderAssignmentDemo.CAN_TAKE.isSatisfiedBy(new RiderAssignmentDemo.Rider("B", true, 2, true, 1), new RiderAssignmentDemo.Order("O", 2, true)));
    }
}
```

In a real service, add a Testcontainers test that runs each fixture request against PostgreSQL and asserts the same SKUs as the in-memory interpreter.

## 15. Observability & Operations

Log the rendered specification (its description, not the bind values if they're personal data) with query duration, and count requests by filter combination. That shows which combinations need indexes. Slow-query logs in the database, tagged with a request ID, connect slow SQL back to the filters that produced it.

## 16. Version Notes

| Java / library | Change relevant to Specification |
|----------------|----------------------------------|
| Java 8 | `Predicate<T>` with `and`, `or`, `negate`; streams |
| Java 11 | `Predicate.not(...)` |
| Java 16–21 | Records and sealed interfaces for inspectable spec trees; pattern-matching `switch` for interpreters |
| JPA 2.0 | Criteria API and static metamodel |
| Spring Data JPA 1.x | `Specification<T>` and `JpaSpecificationExecutor` |
| Spring Data JPA 3.x | `Specification.allOf/anyOf`; Jakarta Persistence namespace |

## 17. Expert Deep Dive

- **Specification is a Composite of predicates, and translating it is an Interpreter.** The same tree supports many interpretations: SQL, Elasticsearch JSON, in-memory test, explanation, and even code generation.
- **Subsumption.** Evans and Fowler describe asking whether one specification is more general than another (`price < 100` subsumes `price < 50`), useful for caching ("can this query be answered from the cached result of a broader one?") and for detecting redundant rules.
- **Partially satisfied specs.** Returning *which* parts failed (as in Example 2) supports "what would make this eligible?" features, such as "add Rs 120 more for free delivery".
- **Algebra.** `and` and `or` are associative and commutative with identities `TRUE` and `FALSE`; an empty `AllOf` is `TRUE`, an empty `AnyOf` is `FALSE`. Getting these identities right avoids "no filters returns nothing" bugs.
- **Beyond databases.** The same idea powers authorization policies (Open Policy Agent, AWS Cedar) and feature-flag targeting rules.

> 📖 **War story: the filter that showed hidden products**  
> GreenValley's farm-produce marketplace hid products whose supplier licence had expired by adding `and(visible())` in the controller. A new mobile endpoint reused the repository's `findAll(spec)` but built its own specification from query parameters and forgot `visible()`. For three weeks the app listed products that could not legally be sold, and a regulator noticed before the team did. **Fixes:** the repository method became `search(Specification<Product> userFilters, Viewer viewer)` and always adds `visibleTo(viewer)` itself; the raw `findAll(spec)` was hidden behind a package-private interface; and a test runs every public search endpoint against a fixture with an expired-licence product. **Lesson:** rules that must always apply belong inside the repository, not in each caller's specification.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is a Specification?** A **business rule as an object** answering `isSatisfiedBy(candidate)`.
2. **Why not a finder method per filter?** **Combinations explode**; specs combine freely.
3. **How do you combine specs?** **`and`, `or`, `not`.**
4. **What JDK type is a simple spec?** **`Predicate<T>`.**
5. **Give an example.** **Free delivery**: total ≥ 499 or first order, and within 5 km.

**▸ 🟡 L3**

1. **How does Spring Data JPA use specifications?** `toPredicate(root, query, cb)` **builds a criteria predicate**; `JpaSpecificationExecutor.findAll(spec)`.
2. **Why not filter in memory with Predicate?** It **loads every row**; push filters to the database.
3. **What can a custom spec type do that a lambda can't?** **Describe, explain, translate, serialise.**
4. **How do you prevent SQL injection in specs?** **Bind parameters**, whitelist fields.
5. **What's an empty AllOf?** **TRUE** (matches everything).

**▸ 🔴 L4–L5**

1. **Design a product search API with dynamic filters.** **Whitelist and validate**, build a spec tree, render to SQL/search with binds, paging, mandatory visibility rule, index the common filters.
2. **How do you keep in-memory and SQL interpretations consistent?** Define semantics once; **fixture tests comparing both** (Testcontainers).
3. **How would you implement "add Rs 120 for free delivery"?** **Partially satisfied specs** returning the failing parts and the gap.
4. **Specification vs Querydsl vs Criteria API?** Specification is the **pattern**; Criteria and Querydsl are **query builders** a spec can use.
5. **How do you stop callers bypassing tenant filters?** The **repository adds them**; don't expose raw `findAll(spec)`.

**▸ 💻 Hands-on**

**Task:** Add `min_rating` (1 to 5) to `ProductSearchSpecDemo`.

> ✅ **Solution**  
> ```java
> record Product(String sku, String name, String category, long pricePaise, boolean organic, int stock, int rating) { }
> record AtLeast(String field, int min) implements Spec { }    // add to the permits list
>
> // fromRequest: add "min_rating" to the whitelist, then
> if (q.containsKey("min_rating")) {
>     int r = Integer.parseInt(q.get("min_rating"));
>     if (r < 1 || r > 5) throw new IllegalArgumentException("min_rating must be 1 to 5");
>     parts.add(new AtLeast("rating", r));
> }
> // toSql:  case AtLeast a -> a.field() + " >= ?" + bind(params, a.min());
> // test:   case AtLeast a -> p.rating() >= a.min();
> ```

## 19. Practice

- [ ] Add `describe()` to `FreeDeliverySpecDemo` and print the rule.
    > 💡 **Hint**  
    > Copy the `Spec.of(description, predicate)` factory from `CompositionDemo`.

- [ ] Extend `RiderAssignmentDemo` to rank eligible riders by distance.
    > 💡 **Hint**  
    > Filter with the spec, then sort with `Comparator.comparingDouble(Rider::kmAway)`.

- [ ] Add a mandatory `visibleTo(viewer)` rule to `ProductSearchSpecDemo` that callers can't skip.
    > 💡 **Hint**  
    > Wrap the user spec: `new AllOf(List.of(visible, userSpec))` inside the search method.

- [ ] Implement "add Rs X more for free delivery" using the failing parts of the spec.
    > 💡 **Hint**  
    > Return the failing rule and compute the gap for the total rule only.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Specification: a named business rule with `isSatisfiedBy`.
> - Combine with `and`, `or`, `not`; empty AllOf = TRUE.
> - In memory: `Predicate<T>` is often enough.
> - Database: Spring Data JPA `Specification.toPredicate` builds the query.
> - Bind values, whitelist fields, escape LIKE, enforce visibility in the repository.
>
> ```java
> Specification<Product> spec = organic().and(inCategory("fruit").or(cheaperThan(7_000)));
> Page<Product> page = productRepository.findAll(spec, PageRequest.of(0, 20));
> ```

## 21. Further Reading

- Eric Evans and Martin Fowler, ["Specifications"](https://martinfowler.com/apsupp/spec.pdf) (1997).
- Eric Evans, *Domain-Driven Design* (2003), chapter 9, "Specification".
- [Spring Data JPA reference: Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)
- [Querydsl reference](http://querydsl.com/static/querydsl/latest/reference/html/)
- OWASP, "SQL Injection Prevention Cheat Sheet".

---

[← Previous: Null Object](null-object.md) · [Next: Testing Patterns →](testing-patterns.md)
