# Functional Patterns in Java

L3 🟡 Professional · ⏱ 40 min read · Prerequisites: [Patterns in Modern Java](patterns-in-modern-java.md), [Strategy](../l1-foundations/strategy.md)

> 📌 **TL;DR**  
> Functional programming has its own pattern catalogue, and Java 8–21 supports most of it. **Higher-order functions** wrap behaviour (retry, caching, logging) once. **Composition** builds pipelines from small functions. **Optional** and **Result/Either** make absence and failure into values you can chain. **Currying and partial application** fix arguments early. **Streams** express data pipelines declaratively. **Immutability** makes all of it safe to share and parallelise. The practical architecture is "functional core, imperative shell": pure functions inside, I/O at the edges.

## 1. What It Is

**Plain English.** Instead of objects that change over time and call each other, you write small functions that take values and return new values. You then build bigger behaviour by passing functions around and joining them together. FreshCart's pricing becomes "subtotal → member discount → coupon → delivery → tax", each step a function you can test alone and reorder.

**Definition.** Functional patterns treat **functions as values** and **data as immutable**:

- **Pure function:** output depends only on input; no side effects.
- **Higher-order function:** takes or returns a function.
- **Composition:** `f.andThen(g)` builds `x -> g(f(x))`.
- **Functor / monad (informally):** containers such as `Optional`, `Stream`, `CompletableFuture` and `Result` with `map` and `flatMap`.
- **Currying / partial application:** turning multi-argument functions into chains of one-argument functions, or fixing some arguments.
- **Persistent data structure:** an immutable collection where "updates" return new versions sharing structure with the old.

## 2. Why It Exists

Mutable shared state is the main source of bugs in concurrent and large codebases: you can't reason about a method without knowing who else changes the same objects. Exceptions and `null` hide failure paths from the type system. Functional patterns address both: pure functions are trivially testable and thread-safe, and container types (`Optional`, `Result`) make "might be missing" and "might fail" visible and composable.

## 3. Mental Model / Analogy

**Analogy.** An assembly line. Each station (function) takes a part, does one thing, and passes a new part on; no station reaches back to modify earlier parts. You can test each station alone, add or remove stations, and run several lines in parallel without them interfering.

**Technical model.** Data flows through a chain of transformations. Containers carry context along the chain: `Optional` carries "maybe absent", `Result` carries "maybe failed", `Stream` carries "many values, lazily", `CompletableFuture` carries "later". `map` transforms the value inside; `flatMap` chains a step that itself returns a container.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Higher-order functions

**Description.** A higher-order function takes a function, returns a function, or both. It lets you write cross-cutting behaviour once and wrap any function with it: retry, timing, logging, memoisation, authorisation checks. It is the functional form of [Decorator](../l1-foundations/decorator.md).

**Advantages / features.** Reusable wrappers without class hierarchies; behaviour is parameterised; wrappers stack in any order.

**HigherOrderFunctionsDemo.java**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Higher-order functions take or return functions, so cross-cutting behaviour (retry, timing, caching) is written once. */
public class HigherOrderFunctionsDemo {

    static <T> Supplier<T> withRetry(Supplier<T> call, int attempts) {
        return () -> {
            RuntimeException last = null;
            for (int i = 1; i <= attempts; i++) {
                try { return call.get(); } catch (RuntimeException e) { last = e; System.out.println("  attempt " + i + " failed: " + e.getMessage()); }
            }
            throw last;
        };
    }

    static <A, B> Function<A, B> memoize(Function<A, B> f) {
        Map<A, B> cache = new HashMap<>();
        return a -> cache.computeIfAbsent(a, f);
    }

    static <A, B> Function<A, B> logged(String name, Function<A, B> f) {
        return a -> { B b = f.apply(a); System.out.println("  " + name + "(" + a + ") = " + b); return b; };
    }

    public static void main(String[] args) {
        int[] calls = {0};
        Supplier<String> flakyStock = () -> { if (++calls[0] < 3) throw new IllegalStateException("timeout"); return "MILK: 42 left"; };
        System.out.println("stock: " + withRetry(flakyStock, 3).get());

        int[] lookups = {0};
        Function<String, Integer> pincodeToZone = memoize(logged("zoneLookup", pin -> { lookups[0]++; return pin.startsWith("411") ? 1 : 2; }));
        for (String pin : new String[] {"411001", "560001", "411001", "411001"}) pincodeToZone.apply(pin);
        System.out.println("4 calls, " + lookups[0] + " real lookups");
    }
}
```

1. Takes a call and returns a new call that retries it: a decorator as a function.
2. Returns a caching version of any function.
3. Wrappers stack: here memoisation wraps logging, so only real lookups are logged.

**Run it:** `java HigherOrderFunctionsDemo.java`

```text
  attempt 1 failed: timeout
  attempt 2 failed: timeout
stock: MILK: 42 left
  zoneLookup(411001) = 1
  zoneLookup(560001) = 2
4 calls, 2 real lookups
```

| Wrapper | Signature shape | OO equivalent |
|---------|-----------------|---------------|
| Retry | `Supplier<T> -> Supplier<T>` | Retry decorator / proxy |
| Memoise | `Function<A,B> -> Function<A,B>` | Caching proxy |
| Log / time | `Function<A,B> -> Function<A,B>` | Logging decorator, AOP advice |
| Guard | `Function<A,B> -> Function<A,B>` | Protection proxy |

**Use** higher-order wrappers for small, local cross-cutting concerns. **Avoid** them for concerns that need configuration and lifecycle (circuit breakers, metrics); use a library (Resilience4j) built on the same idea.
**Real-world Java:** `Comparator.comparing(keyExtractor)`, `CompletableFuture.thenApply`, Resilience4j `Retry.decorateSupplier`, Spring's `TransactionTemplate.execute(callback)`.
**Best practice:** keep wrapper signatures generic (`<A, B>`) so they apply anywhere. **Gotcha:** the `HashMap` memoiser above isn't thread-safe and fails with `ConcurrentModificationException` for recursive functions; use `ConcurrentHashMap` (non-recursive) or Caffeine.

### Function composition

**Description.** `Function.andThen(g)` and `compose(g)` join functions into pipelines; `Predicate.and/or` and `Comparator.thenComparing` do the same for their types. Each business rule becomes a small function, and the overall behaviour is their composition.

**Advantages / features.** Rules are testable alone; pipelines can be built from configuration; order is explicit.

**Before (one method doing everything) and after (composition):**

**FunctionCompositionDemo.java**

```java
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Compose small functions into a pipeline with andThen / compose instead of nesting calls or growing one big method. */
public class FunctionCompositionDemo {

    record Cart(long subtotalPaise, boolean member, String coupon) { }

    // ---------- before: one method that does everything, in an order you must read carefully ----------
    static long priceBefore(Cart c) {
        long p = c.subtotalPaise();
        if (c.member()) p = p * 95 / 100;
        if ("FRESH10".equals(c.coupon())) p = p - Math.min(p / 10, 10_000);
        p = p + (p >= 49_900 ? 0 : 2_500);
        return p + p * 5 / 100;
    }

    // ---------- after: each rule is a function; the pipeline is a composition ----------
    record Priced(Cart cart, long paise) { }
    static final UnaryOperator<Priced> MEMBER_DISCOUNT = p -> p.cart().member() ? new Priced(p.cart(), p.paise() * 95 / 100) : p;
    static final UnaryOperator<Priced> COUPON = p -> "FRESH10".equals(p.cart().coupon()) ? new Priced(p.cart(), p.paise() - Math.min(p.paise() / 10, 10_000)) : p;
    static final UnaryOperator<Priced> DELIVERY = p -> new Priced(p.cart(), p.paise() + (p.paise() >= 49_900 ? 0 : 2_500));
    static final UnaryOperator<Priced> TAX = p -> new Priced(p.cart(), p.paise() + p.paise() * 5 / 100);

    static final Function<Cart, Long> PRICE =
            ((Function<Cart, Priced>) c -> new Priced(c, c.subtotalPaise()))
                    .andThen(MEMBER_DISCOUNT).andThen(COUPON).andThen(DELIVERY).andThen(TAX)
                    .andThen(Priced::paise);

    static Function<Cart, Long> pipeline(List<UnaryOperator<Priced>> steps) {
        Function<Priced, Priced> all = steps.stream().map(s -> (Function<Priced, Priced>) s).reduce(Function.identity(), Function::andThen);
        return c -> all.apply(new Priced(c, c.subtotalPaise())).paise();
    }

    public static void main(String[] args) {
        Cart cart = new Cart(52_000, true, "FRESH10");
        System.out.println("before:   " + priceBefore(cart));
        System.out.println("composed: " + PRICE.apply(cart));
        System.out.println("festival config (no tax step, for a tax-free day): " + pipeline(List.of(MEMBER_DISCOUNT, COUPON, DELIVERY)).apply(cart));
    }
}
```

1. Before: four rules interleaved in one method. Changing order or switching one off means editing it.
2. After: a pipeline of named steps; the order reads top to bottom.
3. Pipelines built from a list, so configuration can choose the steps.

**Run it:** `java FunctionCompositionDemo.java`

```text
before:   49308
composed: 49308
festival config (no tax step, for a tax-free day): 46960
```

| Method | Result | Order |
|--------|--------|-------|
| `f.andThen(g)` | `x -> g(f(x))` | f first |
| `f.compose(g)` | `x -> f(g(x))` | g first |
| `Function.identity()` | `x -> x` | Neutral element for `reduce` |
| `p.and(q)` / `p.or(q)` / `p.negate()` | Combined predicates | Short-circuit |

**Use** composition for pipelines of independent rules. **Avoid** it when steps need to share lots of intermediate state; a small record carried through the pipeline (like `Priced`) solves that up to a point.
**Real-world Java:** `Comparator.comparing(...).thenComparing(...)`, Spring WebFlux `RouterFunction.and`, Reactor operator chains.
**Best practice:** test that the composed version matches the old imperative version over many inputs before switching (the page's test does this). **Gotcha:** `andThen` and `compose` read in opposite directions; pick one and use it consistently.

### Optional as a container

**Description.** `Optional<T>` holds zero or one value. `map` transforms the value if present, `flatMap` chains a step that returns `Optional`, `filter` keeps it only if it matches, `or` supplies another `Optional`, and `orElse` / `orElseGet` / `orElseThrow` end the chain.

**Advantages / features.** Nested null checks become a linear chain; absence is visible in the return type.

**Before (nested null checks) and after:**

**OptionalContainerDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Optional as a container: map / flatMap / filter / or, instead of nested null checks. */
public class OptionalContainerDemo {

    record Address(String city, String pincode) { }
    record Customer(String id, Address defaultAddress) { }

    static final Map<String, Customer> CUSTOMERS = new LinkedHashMap<>();
    static {
        CUSTOMERS.put("u1", new Customer("u1", new Address("Pune", "411001")));
        CUSTOMERS.put("u2", new Customer("u2", null));
    }

    // ---------- before ----------
    static String zoneBefore(String customerId) {
        Customer c = CUSTOMERS.get(customerId);
        if (c != null) {
            Address a = c.defaultAddress();
            if (a != null && a.pincode() != null && a.pincode().startsWith("411")) return "PUNE-ZONE";
        }
        return "ASK-FOR-ADDRESS";
    }

    // ---------- after ----------
    static Optional<Customer> find(String id) { return Optional.ofNullable(CUSTOMERS.get(id)); }

    static String zone(String customerId) {
        return find(customerId)
                .map(Customer::defaultAddress)
                .map(Address::pincode)
                .filter(pin -> pin.startsWith("411"))
                .map(pin -> "PUNE-ZONE")
                .orElse("ASK-FOR-ADDRESS");
    }

    static Optional<Address> lastOrderAddress(String id) { return id.equals("u2") ? Optional.of(new Address("Pune", "411045")) : Optional.empty(); }

    static Address deliveryAddress(String id) {
        return find(id).map(Customer::defaultAddress)
                .or(() -> lastOrderAddress(id))
                .orElseThrow(() -> new IllegalStateException("no address for " + id));
    }

    static String expensiveDefault() { System.out.println("  (expensive default computed)"); return "fallback"; }

    public static void main(String[] args) {
        for (String id : new String[] {"u1", "u2", "u9"}) System.out.println(id + ": before=" + zoneBefore(id) + " after=" + zone(id));
        System.out.println("u2 delivers to " + deliveryAddress("u2"));
        System.out.println("orElse:     " + Optional.of("value").orElse(expensiveDefault()));
        System.out.println("orElseGet:  " + Optional.of("value").orElseGet(OptionalContainerDemo::expensiveDefault));
    }
}
```

1. Each step runs only if the previous produced a value; `map` turns a `null` result into empty.
2. `or` (Java 9) tries a second source lazily.
3. `orElse` always evaluates its argument, even when the value is present; `orElseGet` doesn't.

**Run it:** `java OptionalContainerDemo.java`

```text
u1: before=PUNE-ZONE after=PUNE-ZONE
u2: before=ASK-FOR-ADDRESS after=ASK-FOR-ADDRESS
u9: before=ASK-FOR-ADDRESS after=ASK-FOR-ADDRESS
u2 delivers to Address[city=Pune, pincode=411045]
  (expensive default computed)
orElse:     value
orElseGet:  value
```

| Method | Use when |
|--------|----------|
| `map(f)` | Transform the value; a `null` result becomes empty |
| `flatMap(f)` | `f` itself returns `Optional` |
| `filter(p)` | Keep only matching values |
| `or(() -> other)` | Fall back to another optional source |
| `orElseGet(supplier)` | Default that's expensive to compute |
| `orElseThrow(...)` | Absence is an error here |
| `ifPresentOrElse(a, b)` | Side effects for both cases |

**Use** `Optional` as a **return type** for lookups. **Avoid** it for fields, parameters and collections (return an empty collection instead).
**Real-world Java:** `Stream.findFirst`, `Map`-backed repositories, Spring Data `findById`.
**Best practice:** never call `get()` without a check; prefer `orElseThrow()` which states intent. **Gotcha:** `Optional.of(null)` throws; use `ofNullable` at boundaries with nullable APIs.

### Result/Either types

**Description.** A `Result<T>` (also called `Either<E, T>` or `Try<T>`) is a value that is **either** a success with a `T` **or** a failure with an error. With `map` and `flatMap`, a chain of steps runs until the first failure, which then flows through unchanged: "railway-oriented programming". Java has no built-in type; a sealed interface with two records is enough, or use Vavr's `Either` / `Try`.

**Advantages / features.** Expected failures are in the type and can't be ignored; no exceptions for control flow; errors are data you can collect, log and return.

**ResultEitherDemo.java**

```java
import java.util.List;
import java.util.function.Function;

/** A Result (Either) type: success or failure as a value, chained with map / flatMap ("railway-oriented" code). */
public class ResultEitherDemo {

    sealed interface Result<T> permits Ok, Err {
        default <R> Result<R> map(Function<T, R> f) {
            return switch (this) { case Ok<T> ok -> new Ok<>(f.apply(ok.value())); case Err<T> err -> new Err<>(err.error()); };
        }
        default <R> Result<R> flatMap(Function<T, Result<R>> f) {
            return switch (this) { case Ok<T> ok -> f.apply(ok.value()); case Err<T> err -> new Err<>(err.error()); };
        }
        default T orElse(T fallback) { return this instanceof Ok<T> ok ? ok.value() : fallback; }
    }
    record Ok<T>(T value) implements Result<T> { }
    record Err<T>(String error) implements Result<T> { }

    record Qty(int value) { }
    record Line(String sku, Qty qty) { }

    static Result<String> sku(String raw) { return raw.matches("[A-Z0-9-]{3,20}") ? new Ok<>(raw) : new Err<>("bad SKU '" + raw + "'"); }
    static Result<Qty> qty(String raw) {
        try { int q = Integer.parseInt(raw); return q >= 1 && q <= 20 ? new Ok<>(new Qty(q)) : new Err<>("qty must be 1-20, got " + q); }
        catch (NumberFormatException e) { return new Err<>("qty is not a number: '" + raw + "'"); }
    }

    static Result<Line> parseLine(String csv) {
        String[] parts = csv.split(",");
        if (parts.length != 2) return new Err<>("expected 'SKU,QTY', got '" + csv + "'");
        return sku(parts[0].trim()).flatMap(s -> qty(parts[1].trim()).map(q -> new Line(s, q)));
    }

    public static void main(String[] args) {
        for (String row : List.of("MILK-1L,2", "milk,2", "BREAD,0", "EGGS,six", "PANEER"))
            System.out.println(String.format("%-10s -> ", row) + parseLine(row));
    }
}
```

1. A sealed `Result` with `map`, `flatMap` and `orElse` as default methods.
2. `flatMap` chains a step that can itself fail; an `Err` skips all later steps.
3. Parse SKU, then quantity, then build the line: the first error wins.

**Run it:** `java ResultEitherDemo.java`

```text
MILK-1L,2  -> Ok[value=Line[sku=MILK-1L, qty=Qty[value=2]]]
milk,2     -> Err[error=bad SKU 'milk']
BREAD,0    -> Err[error=qty must be 1-20, got 0]
EGGS,six   -> Err[error=qty is not a number: 'six']
PANEER     -> Err[error=expected 'SKU,QTY', got 'PANEER']
```

| Approach | Failure visible in type | Composable | Collects all errors | Cost |
|----------|------------------------|------------|---------------------|------|
| Exceptions | Checked only | Poorly (try/catch) | No | Stack traces are expensive |
| `null` / `Optional` | Absence only, no reason | `Optional` yes | No | Loses the error |
| `Result` / `Either` | ✅ with reason | ✅ `map`/`flatMap` | No (first error) | Small |
| `Validation` (applicative) | ✅ | ✅ | ✅ | Small |

**Use** `Result` for expected, recoverable failures in pure code (parsing, validation, business rules). **Avoid** it for programming errors and infrastructure failures, where exceptions are clearer. See [Error Handling Patterns](error-handling-patterns.md).
**Real-world Java:** Vavr `Either` and `Try`; Kotlin's `Result`; Rust's `Result` inspired many Java versions.
**Best practice:** errors should be typed (a sealed error hierarchy) in real code, not strings. **Gotcha:** wrapping everything in `Result` across a codebase that also throws exceptions gives you both problems; agree where each is used.

### Currying and partial application

**Description.** **Currying** turns `f(a, b, c)` into `a -> b -> c -> result`. **Partial application** fixes some arguments of a function and returns a function of the rest. Both let you configure a function early (tax rate, city fee table) and use it later where only the remaining argument is known.

**Advantages / features.** Configured functions fit into `map` and other one-argument APIs; configuration and use are separated.

**CurryingDemo.java**

```java
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Currying turns f(a, b) into a -> (b -> ...); partial application fixes some arguments now and supplies the rest later. */
public class CurryingDemo {

    static final BiFunction<Integer, Long, Long> addTax = (ratePercent, paise) -> paise + paise * ratePercent / 100;

    static final Function<Integer, Function<Long, Long>> addTaxCurried = rate -> paise -> paise + paise * rate / 100;

    static <A, B, R> Function<B, R> partial(BiFunction<A, B, R> f, A a) { return b -> f.apply(a, b); }

    static final Function<Long, Function<Double, Function<Boolean, Long>>> deliveryFee =
            base -> perKm -> raining -> base + Math.round(perKm * (raining ? 1.5 : 1.0) * 5);

    public static void main(String[] args) {
        Function<Long, Long> gst5 = addTaxCurried.apply(5);
        Function<Long, Long> gst18 = partial(addTax, 18);
        System.out.println("groceries at 5%:  " + List.of(10_000L, 25_000L).stream().map(gst5).toList());
        System.out.println("services at 18%: " + List.of(10_000L, 25_000L).stream().map(gst18).toList());

        Function<Boolean, Long> puneFee = deliveryFee.apply(2_000L).apply(600.0);
        System.out.println("Pune 5 km, dry: " + puneFee.apply(false) + ", raining: " + puneFee.apply(true));
    }
}
```

1. A curried function: give it the rate, get back a function of the amount.
2. Partial application of an existing `BiFunction`.
3. Three levels of configuration; Java's types get long, which is the main practical limit.

**Run it:** `java CurryingDemo.java`

```text
groceries at 5%:  [10500, 26250]
services at 18%: [11800, 29500]
Pune 5 km, dry: 5000, raining: 6500
```

| | Currying | Partial application | Factory method returning a lambda |
|-|----------|---------------------|-----------------------------------|
| Shape | `A -> B -> R` | `(A, B) -> R` plus `a` gives `B -> R` | `rate -> (paise -> ...)` |
| Java readability | Poor beyond two levels | Good | Best |
| Typical use | Functional libraries | Adapting to `map` | Most Java code |

**Use** partial application (often as a simple factory method like `percentOff(10)`) to adapt functions to one-argument APIs. **Avoid** deep currying in Java; named factory methods read better.
**Real-world Java:** `Comparator.comparing(keyExtractor)` returns a configured comparator; Spring's `RouterFunctions.route(predicate, handler)`.
**Best practice:** prefer `static Function<Long, Long> percentOff(int pct)` over `Function<Integer, Function<Long, Long>>`. **Gotcha:** boxing: `Function<Long, Long>` boxes every value; use `LongUnaryOperator` in hot paths.

### Streams as pipelines

**Description.** `Stream` expresses "filter, transform, group, reduce" as a pipeline of operations. Intermediate operations are lazy; a terminal operation (`collect`, `toList`, `reduce`) runs the pipeline. Collectors (`groupingBy`, `summingLong`, `teeing`) build results without mutable accumulators.

**Advantages / features.** Code reads as the requirement; no mutable loop state; parallelism is one call away when functions are pure.

**Before (loop) and after (pipeline):**

**StreamsPipelineDemo.java**

```java
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Streams as declarative pipelines: filter -> map -> group -> collect, compared with the equivalent loop. */
public class StreamsPipelineDemo {

    record Order(String id, String city, String status, long totalPaise) { }

    static final List<Order> ORDERS = List.of(
            new Order("O1", "Pune", "DELIVERED", 64_900), new Order("O2", "Pune", "CANCELLED", 12_000),
            new Order("O3", "Mumbai", "DELIVERED", 31_000), new Order("O4", "Pune", "DELIVERED", 22_000),
            new Order("O5", "Mumbai", "DELIVERED", 87_500), new Order("O6", "Nashik", "DELIVERED", 9_900));

    // ---------- before: a loop with a mutable map and the logic spread across lines ----------
    static Map<String, Long> revenueByCityBefore() {
        Map<String, Long> out = new HashMap<>();
        for (Order o : ORDERS) {
            if (!o.status().equals("DELIVERED")) continue;
            out.put(o.city(), out.getOrDefault(o.city(), 0L) + o.totalPaise());
        }
        return new TreeMap<>(out);
    }

    // ---------- after: the pipeline reads as the requirement ----------
    static Map<String, Long> revenueByCity() {
        return ORDERS.stream()
                .filter(o -> o.status().equals("DELIVERED"))
                .collect(Collectors.groupingBy(Order::city, TreeMap::new, Collectors.summingLong(Order::totalPaise)));
    }

    public static void main(String[] args) {
        System.out.println("loop:   " + revenueByCityBefore());
        System.out.println("stream: " + revenueByCity());

        var stats = ORDERS.stream().filter(o -> o.status().equals("DELIVERED"))
                .collect(Collectors.teeing(Collectors.counting(), Collectors.averagingLong(Order::totalPaise),
                        (n, avg) -> n + " delivered, average Rs " + Math.round(avg / 100)));
        System.out.println(stats);
        System.out.println("top 2: " + ORDERS.stream().sorted(Comparator.comparingLong(Order::totalPaise).reversed()).limit(2).map(Order::id).toList());

        List<String> ids = ORDERS.parallelStream().map(Order::id).toList();
        System.out.println("parallel, collected (not forEach-added) in encounter order: " + ids);
    }
}
```

1. Before: correct, but the intent (revenue per city of delivered orders) is spread over several lines of mutation.
2. After: filter, group, sum, into a sorted map, in one expression.
3. In parallel streams, collect results; never `forEach` into a shared `ArrayList`, which is not thread-safe.

**Run it:** `java StreamsPipelineDemo.java`

```text
loop:   {Mumbai=118500, Nashik=9900, Pune=86900}
stream: {Mumbai=118500, Nashik=9900, Pune=86900}
5 delivered, average Rs 431
top 2: [O5, O1]
parallel, collected (not forEach-added) in encounter order: [O1, O2, O3, O4, O5, O6]
```

| Collector | Produces |
|-----------|----------|
| `toList()` (Java 16, on Stream) | Unmodifiable list |
| `groupingBy(key, mapFactory, downstream)` | Map of groups, with a chosen map type |
| `partitioningBy(pred)` | `Map<Boolean, List<T>>` |
| `summingLong`, `averagingLong`, `counting` | Aggregates |
| `teeing(c1, c2, merger)` (Java 12) | Two aggregates in one pass |
| `toMap(k, v, merge, mapFactory)` | Map with explicit duplicate handling |

**Use** streams for transformations and aggregations of collections. **Avoid** them for logic with complex early exits, checked exceptions or index arithmetic; a loop is clearer.
**Real-world Java:** `Files.lines`, `BufferedReader.lines`, JDBC result streaming in Spring (`queryForStream`), `Map.entrySet().stream()`.
**Best practice:** use `Collectors.toMap` with a merge function, or it throws on duplicate keys. **Gotcha:** `parallelStream()` uses the shared common ForkJoinPool; blocking I/O inside it starves the whole JVM's parallel work.

### Immutability and persistent collections

**Description.** Immutable objects never change after construction. Java gives you records, `List.of`, `List.copyOf` and `Collections.unmodifiableList`. The last is only a **view**: the underlying list can still change. **Persistent** collections (Vavr, PCollections, Clojure's) go further: "adding" returns a new version that shares most of its structure with the old one, so both versions stay valid and cheap.

**Advantages / features.** Safe sharing between threads and caches; no defensive copies; time travel (keep old versions for undo and auditing).

**PersistentCollectionsDemo.java**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Immutability: unmodifiable VIEWS vs immutable COPIES, and a tiny persistent list that shares structure
 * so "adding" returns a new version cheaply without changing the old one.
 */
public class PersistentCollectionsDemo {

    sealed interface PList<T> permits Cons, Nil {
        default PList<T> prepend(T head) { return new Cons<>(head, this); }
        default int size() { return this instanceof Cons<T> c ? 1 + c.tail().size() : 0; }
        default <R> PList<R> map(Function<T, R> f) {
            return switch (this) { case Cons<T> c -> new Cons<>(f.apply(c.head()), c.tail().map(f)); case Nil<T> n -> new Nil<>(); };
        }
        default String show() { return this instanceof Cons<T> c ? c.head() + (c.tail() instanceof Cons<T> ? ", " : "") + c.tail().show() : ""; }
    }
    record Cons<T>(T head, PList<T> tail) implements PList<T> { }
    record Nil<T>() implements PList<T> { }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("MILK", "BREAD"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> copy = List.copyOf(source);
        source.add("EGGS");
        System.out.println("unmodifiable view sees later change: " + view);
        System.out.println("List.copyOf does not:              " + copy);

        PList<String> v1 = new Nil<String>().prepend("BREAD").prepend("MILK");
        PList<String> v2 = v1.prepend("EGGS");
        System.out.println("v1 = [" + v1.show() + "] size " + v1.size());
        System.out.println("v2 = [" + v2.show() + "] size " + v2.size());
        System.out.println("v2 shares v1's nodes: " + (((Cons<String>) v2).tail() == v1));
        System.out.println("mapped v2: [" + v2.map(String::toLowerCase).show() + "], v2 unchanged: [" + v2.show() + "]");
    }
}
```

1. A minimal persistent list: a sealed interface with `Cons` (head and tail) and `Nil`.
2. An unmodifiable view still reflects changes to its source; `List.copyOf` takes a real snapshot.
3. Prepending creates one new node and reuses the old list as the tail.

**Run it:** `java PersistentCollectionsDemo.java`

```text
unmodifiable view sees later change: [MILK, BREAD, EGGS]
List.copyOf does not:              [MILK, BREAD]
v1 = [MILK, BREAD] size 2
v2 = [EGGS, MILK, BREAD] size 3
v2 shares v1's nodes: true
mapped v2: [eggs, milk, bread], v2 unchanged: [EGGS, MILK, BREAD]
```

| Option | Can change? | Cost of "update" | Notes |
|--------|------------|------------------|-------|
| `ArrayList` | Yes | O(1) amortised | Not safe to share |
| `Collections.unmodifiableList(x)` | Through `x` | n/a | View, not snapshot |
| `List.of` / `List.copyOf` | No | Full copy, O(n) | No nulls allowed |
| Persistent (Vavr `List`, `Vector`) | No | O(1) to O(log n), shared | Extra dependency |

**Use** `List.copyOf` and records by default; persistent collections when you keep many versions or update large immutable structures often. **Avoid** copying large lists on every small change in hot paths.
**Real-world Java:** `List.of`, `Map.of`, `Set.copyOf`; Vavr, PCollections, Guava `ImmutableList`; `String` itself is immutable.
**Best practice:** expose immutable types from APIs and accept any `Collection` as input. **Gotcha:** `List.of(...).contains(null)` throws `NullPointerException`.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop runs several discounts at once. Marketing switches them on and off; the checkout just applies "all active discounts".

**DiscountPipelineDemo.java**

```java
import java.util.List;
import java.util.function.UnaryOperator;

/** Minimal: QuickDrop's active discounts are a list of functions, folded into one with andThen. */
public class DiscountPipelineDemo {

    static UnaryOperator<Long> percentOff(int pct) { return p -> p - p * pct / 100; }
    static UnaryOperator<Long> flatOff(long paise) { return p -> Math.max(0, p - paise); }

    public static void main(String[] args) {
        List<UnaryOperator<Long>> active = List.of(percentOff(10), flatOff(5_000));
        UnaryOperator<Long> all = active.stream().reduce(UnaryOperator.identity(), (a, b) -> x -> b.apply(a.apply(x)));
        for (long cart : new long[] {30_000, 80_000, 4_000})
            System.out.println("Rs " + cart / 100 + " -> Rs " + all.apply(cart) / 100.0);
    }
}
```

1. Each discount is a function built by a small factory (partial application).
2. `reduce` with the identity folds the list into one function.

**Run it:** `java DiscountPipelineDemo.java`

**Output:**

```text
Rs 300 -> Rs 220.0
Rs 800 -> Rs 670.0
Rs 40 -> Rs 0.0
```

**What to notice:**

- An empty list of discounts still works: the identity function returns the price unchanged.
- `flatOff` never goes below zero.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the checkout form has phone, pincode, slot and tip. Customers hate fixing one error only to be told about the next, so the form must report every problem at once, without exceptions.

**OrderFormValidationDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Realistic: FreshCart's checkout form. A Result type stops at the first error (good for dependent steps);
 * a Validation type collects EVERY error (good for forms). Both are values, not exceptions.
 */
public class OrderFormValidationDemo {

    record Form(String phone, String pincode, String slot, int tipRupees) { }
    record ValidOrder(String phone, String pincode, String slot, int tipRupees) { }

    /** Validation: either a value or a non-empty list of errors; combining two validations keeps both error lists. */
    sealed interface Validation<T> permits Valid, Invalid {
        static <T> Validation<T> check(boolean ok, T value, String error) { return ok ? new Valid<>(value) : new Invalid<>(List.of(error)); }
        default List<String> errors() { return this instanceof Invalid<T> i ? i.errs() : List.of(); }
    }
    record Valid<T>(T value) implements Validation<T> { }
    record Invalid<T>(List<String> errs) implements Validation<T> { }

    static Validation<ValidOrder> validate(Form f) {
        List<Validation<?>> checks = List.of(
                Validation.check(f.phone().matches("[6-9]\\d{9}"), f.phone(), "phone must be 10 digits starting 6-9"),
                Validation.check(f.pincode().matches("\\d{6}"), f.pincode(), "pincode must be 6 digits"),
                Validation.check(List.of("08-10", "18-20").contains(f.slot()), f.slot(), "slot " + f.slot() + " is not available"),
                Validation.check(f.tipRupees() >= 0 && f.tipRupees() <= 200, f.tipRupees(), "tip must be Rs 0-200"));
        List<String> errors = new ArrayList<>();
        checks.forEach(c -> errors.addAll(c.errors()));
        return errors.isEmpty() ? new Valid<>(new ValidOrder(f.phone(), f.pincode(), f.slot(), f.tipRupees())) : new Invalid<>(List.copyOf(errors));
    }

    static <T> String show(Validation<T> v) {
        return switch (v) { case Valid<T> ok -> "OK " + ok.value(); case Invalid<T> bad -> "rejected " + bad.errs(); };
    }

    public static void main(String[] args) {
        System.out.println(show(validate(new Form("9845012345", "411001", "18-20", 20))));
        System.out.println(show(validate(new Form("12345", "4110", "12-14", 500))));
        System.out.println(show(validate(new Form("9845012345", "411001", "12-14", 0))));
    }
}
```

1. A `Validation` type: like `Result`, but designed to accumulate errors.
2. Independent checks, each producing a value or an error.
3. All errors are combined; only if there are none is a `ValidOrder` built.

**Run it:** `java OrderFormValidationDemo.java`

**Output:**

```text
OK ValidOrder[phone=9845012345, pincode=411001, slot=18-20, tipRupees=20]
rejected [phone must be 10 digits starting 6-9, pincode must be 6 digits, slot 12-14 is not available, tip must be Rs 0-200]
rejected [slot 12-14 is not available]
```

**What to notice:**

- The second form got all four problems in one response.
- `ValidOrder` can only be created through validation, so the rest of checkout never sees bad input.

### Example 3: Production-grade

**Scenario:** ParcelGo pays riders nightly from a CSV of the day's deliveries. Requirements: one corrupt line must not stop the run, and every bad line must be reported; money must be exact; per-zone rates come from a slow lookup; the computation must be parallel for large days and give identical results on re-runs (finance re-runs it for audits).

**RiderSettlementDemo.java**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production: ParcelGo's nightly rider settlement. "Functional core, imperative shell":
 * - the shell reads raw CSV lines and prints results; everything between is pure functions on immutable data
 * - each line parses to a Result, so one bad line doesn't abort the night's run and every error is reported
 * - money is BigDecimal with explicit rounding; per-zone rates are memoized with a thread-safe cache
 * - the pure pipeline runs in parallel safely and gives the same, sorted output every time
 */
public class RiderSettlementDemo {

    record Delivery(String riderId, String zone, double km, boolean late) { }
    record Payout(String riderId, int deliveries, BigDecimal amount) { }

    sealed interface Parsed permits Good, Bad { }
    record Good(Delivery d) implements Parsed { }
    record Bad(int lineNo, String line, String reason) implements Parsed { }

    static Parsed parse(int lineNo, String line) {
        String[] f = line.split(",");
        if (f.length != 4) return new Bad(lineNo, line, "expected 4 fields");
        try {
            double km = Double.parseDouble(f[2]);
            if (km <= 0 || km > 50) return new Bad(lineNo, line, "km out of range");
            if (!f[3].equals("on_time") && !f[3].equals("late")) return new Bad(lineNo, line, "status must be on_time or late");
            return new Good(new Delivery(f[0], f[1], km, f[3].equals("late")));
        } catch (NumberFormatException e) { return new Bad(lineNo, line, "km is not a number"); }
    }

    record Rate(BigDecimal base, BigDecimal perKm) { }
    static final Map<String, Rate> RATE_CACHE = new ConcurrentHashMap<>();
    static int rateLookups;
    static Rate rate(String zone) {
        return RATE_CACHE.computeIfAbsent(zone, z -> {
            synchronized (RiderSettlementDemo.class) { rateLookups++; }
            return z.equals("CENTRAL") ? new Rate(new BigDecimal("25"), new BigDecimal("6.5")) : new Rate(new BigDecimal("30"), new BigDecimal("8"));
        });
    }

    static BigDecimal earning(Delivery d) {
        Rate r = rate(d.zone());
        BigDecimal gross = r.base().add(r.perKm().multiply(BigDecimal.valueOf(d.km())));
        BigDecimal adjusted = d.late() ? gross.multiply(new BigDecimal("0.90")) : gross;
        return adjusted.setScale(2, RoundingMode.HALF_EVEN);
    }

    static Map<String, Payout> settle(List<Delivery> deliveries) {
        return deliveries.parallelStream().collect(Collectors.groupingBy(Delivery::riderId, TreeMap::new,
                Collectors.collectingAndThen(Collectors.toList(), ds -> new Payout(ds.get(0).riderId(), ds.size(),
                        ds.stream().map(RiderSettlementDemo::earning).reduce(BigDecimal.ZERO, BigDecimal::add)))));
    }

    public static void main(String[] args) {
        List<String> csv = List.of(                                                                    // the shell: input
                "R1,CENTRAL,3.2,on_time", "R2,OUTER,7.5,late", "R1,CENTRAL,1.8,on_time",
                "R3,OUTER,12.0,on_time", "R2,OUTER,abc,on_time", "R1,CENTRAL,2.5", "R3,CENTRAL,4.0,late", "R2,OUTER,80,on_time");

        List<Parsed> parsed = java.util.stream.IntStream.range(0, csv.size()).mapToObj(i -> parse(i + 1, csv.get(i))).toList();
        List<Delivery> good = parsed.stream().flatMap(p -> p instanceof Good g ? java.util.stream.Stream.of(g.d()) : java.util.stream.Stream.empty()).toList();
        List<Bad> bad = parsed.stream().flatMap(p -> p instanceof Bad b ? java.util.stream.Stream.of(b) : java.util.stream.Stream.empty()).toList();

        Map<String, Payout> run1 = settle(good), run2 = settle(good);
        run1.values().forEach(p -> System.out.println(p.riderId() + ": deliveries=" + p.deliveries() + ", payout Rs " + p.amount()));
        System.out.println("total Rs " + run1.values().stream().map(Payout::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        bad.forEach(b -> System.out.println("skipped line " + b.lineNo() + " (" + b.reason() + "): " + b.line()));
        System.out.println("rate lookups: " + rateLookups + ", two parallel runs identical: " + run1.equals(run2));
    }
}
```

1. Parsing returns a value for every line, good or bad, so errors are collected instead of thrown.
2. A thread-safe memoised lookup: two zones, two real lookups, however many deliveries.
3. A pure earning function with `BigDecimal` and explicit rounding.
4. The pure core: grouped and summed in parallel into a sorted map.
5. Running twice and comparing is the cheapest determinism check; the test goes further and shuffles input.

**Run it:** `java RiderSettlementDemo.java`

**Output:**

```text
R1: deliveries=2, payout Rs 82.50
R2: deliveries=1, payout Rs 81.00
R3: deliveries=2, payout Rs 171.90
total Rs 335.40
skipped line 5 (km is not a number): R2,OUTER,abc,on_time
skipped line 6 (expected 4 fields): R1,CENTRAL,2.5
skipped line 8 (km out of range): R2,OUTER,80,on_time
rate lookups: 2, two parallel runs identical: true
```

**What to notice:**

- Three bad lines were reported with reasons; the other five were paid.
- R2's late delivery got 10% less: (30 + 8 × 7.5) × 0.9 = 81.00.
- Output is sorted by rider (TreeMap), so reports diff cleanly between runs.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Testability | Pure functions need no mocks | Effects still need testing at the shell |
| Concurrency | Immutable data is thread-safe | Copying can cost memory and GC |
| Readability | Pipelines read like requirements | Long chains and nested generics get dense |
| Error handling | Failures visible in types | Java lacks built-in Result; libraries differ |
| Debugging | Fewer state bugs | Stack traces through lambdas are noisier |
| Performance | Easy parallelism | Boxing, allocation and stream overhead in hot loops |

## 8. When to Use / When NOT to Use

**Use them when:**

- transforming and aggregating data (reports, settlements, pricing);
- business rules are independent steps that can be composed;
- failures are expected and should be reported, not thrown;
- data is shared across threads.

**Don't use them when:**

- the code is mostly I/O orchestration with little logic (plain imperative code is clearer);
- a tight numeric loop must avoid allocation and boxing;
- the team can't read the style yet: introduce it gradually.

## 9. Best Practices

1. **Functional core, imperative shell:** pure logic inside, I/O at the edges. *(Consensus.)*
2. **Name functions** (constants or methods) rather than writing long inline lambdas. *(Consensus.)*
3. **`Optional` for return values only.** *(Consensus.)*
4. **Collect, don't mutate,** in streams, especially parallel ones. *(Consensus.)*
5. **Use primitive specialisations** (`LongStream`, `LongUnaryOperator`) in hot paths. *(Consensus.)*
6. **Pick one error style per layer:** `Result`/`Validation` in the domain, exceptions for infrastructure. *(Opinion, widely shared.)*
7. **`BigDecimal` with explicit rounding for money.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Side effects in a parallel stream (production failure mode)**  
> **Symptom:** the nightly settlement occasionally paid some riders twice and others not at all, and once crashed with `ArrayIndexOutOfBoundsException`.
> **Root cause:** a `parallelStream().forEach(list::add)` appended to a shared `ArrayList`, and a shared `HashMap` accumulated totals.
> **Fix:** pure functions plus `collect` (`groupingBy`, `toMap`); add a test that shuffled input gives identical output.

> 🚨 **Blocking I/O in parallel streams**  
> **Symptom:** unrelated parts of the app slowed down whenever a report ran.
> **Root cause:** a parallel stream called an HTTP service per element, blocking common ForkJoinPool threads used across the JVM.
> **Fix:** use an executor (or virtual threads) for I/O, and keep parallel streams for CPU-bound work.

> 🚨 **`orElse` with an expensive or side-effecting default**  
> **Symptom:** every lookup created a new "guest" customer row in the database.
> **Root cause:** `find(id).orElse(createGuest())` always calls `createGuest()`.
> **Fix:** `orElseGet(this::createGuest)`.

> 🚨 **Lambda soup**  
> **Symptom:** a 40-line chained expression nobody could debug.
> **Root cause:** every step was an inline lambda with nested streams.
> **Fix:** extract named functions and intermediate variables; functional doesn't mean one expression.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `java.util.concurrent.ForkJoinPool.common.parallelism` | CPU cores − 1 | Threads for parallel streams | Containers with CPU limits | Too high oversubscribes CPUs |
| Custom `ForkJoinPool` for a parallel stream | n/a | Isolates heavy jobs | Big batch jobs in a server | Relies on implementation detail |
| Memoisation cache size / expiry | Unbounded (hand-written) | Bounds memory | Many distinct keys | Unbounded growth |
| `-XX:+UseStringDeduplication` / G1 settings | Off | Reduces memory from many immutable strings | Heavy immutable data | Small CPU cost |

## 12. Security Considerations

- Immutable value objects can't be modified after validation (time-of-check/time-of-use bugs disappear).
- `Validation`/`Result` errors returned to users must not include internal details (stack traces, SQL).
- Memoisation caches keyed by user input can grow without bound; cap them to avoid memory exhaustion.
- Pure functions are easy to fuzz and property-test, which helps find parsing bugs in untrusted input.

## 13. Performance & Scalability

Lambdas that capture nothing are allocated once; capturing ones may allocate per use, often removed by escape analysis. Streams add some overhead per element compared with loops, which matters only in tight loops; primitive streams avoid boxing. Parallel streams help for CPU-bound work over large inputs (roughly 10,000+ elements of non-trivial work) and hurt for small or I/O-bound work. Immutable data increases allocation but makes caching and parallelism lock-free. Measure with JMH; see [Performance Cost of Patterns](performance-of-patterns.md).

## 14. Testing It

Pure functions make tests short: exact earnings, parsing as values, order independence with shuffled input, error accumulation, and the composed pricing checked against the old imperative version:

**RiderSettlementTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RiderSettlementTest {

    @Test
    void earningUsesZoneRateAndLatePenalty() {
        // 25 + 6.5 * 2 = 38.00; late: 34.20
        assertEquals(new BigDecimal("38.00"), RiderSettlementDemo.earning(new RiderSettlementDemo.Delivery("R", "CENTRAL", 2, false)));
        assertEquals(new BigDecimal("34.20"), RiderSettlementDemo.earning(new RiderSettlementDemo.Delivery("R", "CENTRAL", 2, true)));
    }

    @Test
    void badLinesBecomeValuesNotExceptions() {
        assertInstanceOf(RiderSettlementDemo.Bad.class, RiderSettlementDemo.parse(1, "R1,CENTRAL,x,late"));
        assertInstanceOf(RiderSettlementDemo.Bad.class, RiderSettlementDemo.parse(2, "R1,CENTRAL,3"));
        assertInstanceOf(RiderSettlementDemo.Good.class, RiderSettlementDemo.parse(3, "R1,CENTRAL,3,late"));
    }

    @Test
    void settlementDoesNotDependOnInputOrder() {
        List<RiderSettlementDemo.Delivery> ds = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 500; i++)
            ds.add(new RiderSettlementDemo.Delivery("R" + random.nextInt(20), random.nextBoolean() ? "CENTRAL" : "OUTER", 1 + random.nextInt(15), random.nextInt(10) == 0));
        var expected = RiderSettlementDemo.settle(ds);
        for (int round = 0; round < 5; round++) {
            Collections.shuffle(ds, random);
            assertEquals(expected, RiderSettlementDemo.settle(ds));
        }
    }

    @Test
    void validationCollectsAllErrorsButResultStopsAtFirst() {
        var v = OrderFormValidationDemo.validate(new OrderFormValidationDemo.Form("1", "2", "3", -1));
        assertEquals(4, v.errors().size());
        var r = ResultEitherDemo.parseLine("milk,zero");
        assertEquals(new ResultEitherDemo.Err<>("bad SKU 'milk'"), r);
    }

    @Test
    void composedPricingMatchesTheImperativeVersion() {
        for (long subtotal : new long[] {1_000, 49_900, 52_000, 200_000})
            for (boolean member : new boolean[] {true, false}) {
                var cart = new FunctionCompositionDemo.Cart(subtotal, member, "FRESH10");
                assertEquals(FunctionCompositionDemo.priceBefore(cart), FunctionCompositionDemo.PRICE.apply(cart));
            }
    }
}
```

Property-based testing (jqwik) is a natural next step: "for any list of deliveries, the total equals the sum of individual earnings".

## 15. Observability & Operations

In the imperative shell, log counts of good and bad inputs, and the first few bad lines with reasons. Keep pure functions free of logging; return information (errors, statistics) as values and log it at the edge. For parallel streams, record the pool size and job duration; for memoisation, export hit rates.

## 16. Version Notes

| Java | Change relevant to functional patterns |
|------|----------------------------------------|
| 8 | Lambdas, `java.util.function`, `Optional`, `Stream`, `CompletableFuture` |
| 9 | `Optional.or/ifPresentOrElse/stream`, `Stream.takeWhile/dropWhile/iterate`, `List.of` |
| 10 | `List.copyOf`, `Collectors.toUnmodifiableList`, `Optional.orElseThrow()` |
| 11 | `Predicate.not` |
| 12 | `Collectors.teeing` |
| 16 | `Stream.toList()`, `Stream.mapMulti`, records |
| 21 | Sequenced collections; pattern matching for sealed Result types |
| 22–24 | Stream gatherers (`Stream.gather`, final in 24) for custom intermediate operations |

## 17. Expert Deep Dive

- **Functor and monad laws, informally.** `map(identity)` must change nothing; `map(f).map(g)` equals `map(f.andThen(g))`; `flatMap` must be associative. `Optional` mostly follows them, except that `map` turns `null` results into empty, which breaks the laws in edge cases.
- **Applicative validation.** `Validation` combines independent checks and accumulates errors, which a monad (`Result.flatMap`) can't, because each step depends on the previous success.
- **Effects at the edges.** The "functional core, imperative shell" architecture (Gary Bernhardt) keeps I/O in a thin layer; the core becomes deterministic and fast to test, and the shell is tested with a few integration tests.
- **Persistent data structures.** Hash array mapped tries (Clojure, Vavr `HashMap`) give O(log₃₂ n) updates with structural sharing; they are why immutable maps can be practical at scale.
- **Gatherers.** Java 24's `Stream.gather` allows custom stateful intermediate operations (windowing, deduplication) without leaving the stream.

> 📖 **War story: the settlement that changed on re-run**  
> ParcelGo's finance team re-ran a month of rider settlements for an audit and got different totals from the originals for 37 riders. The settlement code used `parallelStream()` with a shared `HashMap<String, Double>` for totals and `double` arithmetic. Lost updates under contention dropped some deliveries, and floating-point sums varied with the order of addition. **Fixes:** totals became a pure `groupingBy` collection; money moved to `BigDecimal` with `HALF_EVEN` rounding at the delivery level; output became a sorted map; and a test shuffles 500 deliveries five times and expects identical results. Re-runs have matched to the paisa since. **Lesson:** determinism is a feature of pure functions and exact arithmetic, and it's cheap to test.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is a pure function?** Same input gives **same output**, **no side effects**.
2. **What is a higher-order function?** One that **takes or returns a function**.
3. **`map` vs `flatMap` on Optional?** `flatMap` is for a function that **already returns Optional**.
4. **`orElse` vs `orElseGet`?** `orElse` **always evaluates** its argument; `orElseGet` is **lazy**.
5. **What does `andThen` do?** Runs one function **then another** on its result.

**▸ 🟡 L3**

1. **Why are immutable objects thread-safe?** **Nothing changes** after construction, so no races.
2. **What's a Result/Either type for?** **Expected failures as values**, chained with `flatMap`.
3. **When are parallel streams a bad idea?** **Small inputs, blocking I/O, shared mutable state**, or order-dependent side effects.
4. **`unmodifiableList` vs `List.copyOf`?** **View** vs **snapshot**.
5. **What's partial application?** **Fixing some arguments** to get a function of the rest.

**▸ 🔴 L4–L5**

1. **Explain functional core, imperative shell.** **Pure logic inside, I/O at the edges**; core unit-tested, shell integration-tested.
2. **Result vs Validation?** **First error (monadic)** vs **all errors (applicative)**.
3. **How do persistent collections stay efficient?** **Structural sharing** (tries, cons lists).
4. **How would you make a batch job deterministic?** **Pure functions, exact arithmetic, sorted output, shuffled-input tests.**
5. **Where would you not use functional style in Java?** **Hot numeric loops, heavy I/O orchestration**, or where it hurts readability.

**▸ 💻 Hands-on**

**Task:** Add a "peak hour bonus" (Rs 10 for deliveries marked `peak`) to `RiderSettlementDemo` without breaking determinism.

> ✅ **Solution**  
> ```java
> record Delivery(String riderId, String zone, double km, boolean late, boolean peak) { }
>
> // parse: accept a 5th field "peak" / "off_peak"
> static BigDecimal earning(Delivery d) {
>     Rate r = rate(d.zone());
>     BigDecimal gross = r.base().add(r.perKm().multiply(BigDecimal.valueOf(d.km())));
>     BigDecimal adjusted = d.late() ? gross.multiply(new BigDecimal("0.90")) : gross;
>     BigDecimal bonus = d.peak() ? BigDecimal.TEN : BigDecimal.ZERO;
>     return adjusted.add(bonus).setScale(2, RoundingMode.HALF_EVEN);
> }
> // the shuffled-input test keeps passing because earning() is still pure
> ```

## 19. Practice

- [ ] Write `timed(String name, Function<A,B> f)` that prints how long each call took, and stack it with `memoize`.
    > 💡 **Hint**  
    > `System.nanoTime()` before and after `f.apply(a)`.

- [ ] Replace `Result<T>` in `ResultEitherDemo` with Vavr's `Either<String, T>` and compare the code.
    > 💡 **Hint**  
    > `Either.right(value)`, `Either.left(error)`, `flatMap` works the same way.

- [ ] Rewrite a loop in your codebase that builds a `Map` into a `groupingBy` collector.
    > 💡 **Hint**  
    > Look for `map.put(k, map.getOrDefault(k, 0) + ...)`.

- [ ] Add `append` to the persistent list and explain why it's O(n) while `prepend` is O(1).
    > 💡 **Hint**  
    > Appending must copy every node to change the last tail.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Pure functions + immutable data = easy tests and safe parallelism.
> - Higher-order functions wrap behaviour (retry, cache, log); composition builds pipelines.
> - `Optional`: return types only; `map`/`flatMap`/`orElseGet`.
> - `Result`: first error; `Validation`: all errors.
> - Streams: collect, don't mutate; parallel only for CPU-bound, large, pure work.
> - Functional core, imperative shell.
>
> ```java
> Map<String, Long> revenue = orders.stream()
>     .filter(o -> o.status().equals("DELIVERED"))
>     .collect(groupingBy(Order::city, TreeMap::new, summingLong(Order::totalPaise)));
> ```

## 21. Further Reading

- Venkat Subramaniam, *Functional Programming in Java* (2nd ed., 2023).
- Raoul-Gabriel Urma, Mario Fusco, Alan Mycroft, *Modern Java in Action* (2nd ed., 2018).
- Scott Wlaschin, ["Railway Oriented Programming"](https://fsharpforfunandprofit.com/rop/).
- Gary Bernhardt, "Boundaries" (talk, 2012): functional core, imperative shell.
- [Vavr user guide](https://docs.vavr.io/) and [JEP 485: Stream Gatherers](https://openjdk.org/jeps/485).

---

[← Previous: Patterns in Modern Java (17–21)](patterns-in-modern-java.md) · [Next: Patterns in the JDK →](patterns-in-the-jdk.md)
