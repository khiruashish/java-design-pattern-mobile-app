# Template Method

L1 🟢 Foundations · ⏱ 35 min read · Prerequisites: [Strategy](strategy.md), [OOP Refresher](oop-refresher.md)

> 📌 **TL;DR**  
> Template Method fixes the steps of an algorithm in one `final` method of a base class and lets subclasses fill in the steps that vary. It's how frameworks keep the important parts (transactions, retries, validation) in one place while you supply the business bits. In modern Java, passing the varying steps as lambdas often does the same job without inheritance.

## 1. What It Is

**Plain English.** Every FreshCart order goes through the same fulfilment recipe: pick, pack, label. A dark store and a partner store do each step differently, but the order of steps, and the checks between them, must never change. Template Method writes the recipe once in a base class and leaves blanks (abstract methods) that each store fills in.

**Precise definition (GoF intent).** "Define the skeleton of an algorithm in an operation, deferring some steps to subclasses. Template Method lets subclasses redefine certain steps of an algorithm without changing the algorithm's structure." Parts:

- **Template method:** the `final` method with the fixed sequence (`fulfil`).
- **Primitive operations:** `abstract` steps subclasses must implement (`pick`, `pack`).
- **Hooks:** optional steps with a default that subclasses *may* override (`beforePacking`).

## 2. Why It Exists

When several classes follow the same procedure with small differences, copy-paste spreads the procedure everywhere. Then a fix (add validation, add a retry, close a resource) has to be made in every copy, and one copy always gets missed. Template Method keeps one copy of the procedure and isolates the differences, so the procedure is fixed and enforced in one place.

## 3. Mental Model / Analogy

**Analogy.** A FreshCart store-opening checklist printed by head office: "1. Disarm alarm. 2. Check fridge temperatures. 3. ____ (store-specific setup). 4. Open doors." Each store fills in step 3. Nobody can skip step 2 or reorder the list.

**Technical model.** A `final` public method calls `protected` abstract and hook methods in a fixed order. Subclasses override only the protected steps. Control stays with the base class ("don't call us, we'll call you").

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Abstract class with a final template method

**Description.** The base class has a `final` public method with the algorithm and `protected abstract` methods for steps. `final` stops subclasses from changing the order or skipping checks; `protected` keeps steps out of the public API.

**Advantages / features.** One copy of the procedure; enforced validation; subclasses stay small.

**TemplateBasicsDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Template Method: the fulfilment workflow is fixed in a final method; stores fill in the steps that differ. */
public class TemplateBasicsDemo {

    abstract static class Fulfilment {
        final List<String> fulfil(String orderId, List<String> skus) {
            List<String> log = new ArrayList<>();
            if (skus.isEmpty()) throw new IllegalArgumentException("nothing to fulfil");
            log.add(pick(skus));
            log.add(pack(skus.size()));
            log.add("label " + orderId + " via " + courier());
            return log;
        }
        protected abstract String pick(List<String> skus);
        protected abstract String pack(int items);
        protected abstract String courier();
    }

    static final class DarkStore extends Fulfilment {
        protected String pick(List<String> skus) { return "picker scans " + skus.size() + " items by aisle"; }
        protected String pack(int n) { return "FreshCart bags x" + ((n + 9) / 10); }
        protected String courier() { return "in-house rider"; }
    }

    static final class PartnerStore extends Fulfilment {
        protected String pick(List<String> skus) { return "send pick list to partner tablet"; }
        protected String pack(int n) { return "partner packs in their own bags"; }
        protected String courier() { return "QuickDrop"; }
    }

    public static void main(String[] args) {
        List<String> skus = List.of("MILK", "EGGS", "BREAD");
        System.out.println("dark store:    " + new DarkStore().fulfil("FC-1501", skus));
        System.out.println("partner store: " + new PartnerStore().fulfil("FC-1502", skus));
        try { new DarkStore().fulfil("FC-1503", List.of()); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
```

1. The template method is `final`: subclasses can't reorder or skip steps.
2. Steps run in a fixed order, after validation that applies to every store type.
3. Steps are `protected abstract`: subclasses must provide them, callers can't call them.

**Run it:** `java TemplateBasicsDemo.java`

```text
dark store:    [picker scans 3 items by aisle, FreshCart bags x1, label FC-1501 via in-house rider]
partner store: [send pick list to partner tablet, partner packs in their own bags, label FC-1502 via QuickDrop]
rejected: nothing to fulfil
```

| Pros | Cons |
|------|------|
| Procedure written and fixed once | Inheritance: one base class per subclass, tight coupling |
| Subclasses implement only what differs | Changing the base affects every subclass (fragile base class) |
| Checks can't be skipped | Hard to combine variations (dark store + gift packing) |

**Use it** when several variants share a fixed procedure and you control the class hierarchy. **Avoid it** when variants need to be combined, or when steps would be better as separate [strategies](strategy.md).
**Real-world Java:** `java.io.InputStream.read(byte[], int, int)` built on abstract `read()`; `java.util.AbstractMap`; Spring Batch's `AbstractItemCountingItemStreamItemReader`.
**Best practice:** make the template `final` and steps `protected`. **Gotcha:** don't call abstract steps from the base *constructor*; subclass fields aren't initialised yet.

### Hook methods

**Description.** Hooks are steps with a default implementation, often empty or returning a default answer, which subclasses may override. They give extension points without forcing every subclass to implement them.

**Advantages / features.** Subclasses override only what they need; new hooks can be added without breaking existing subclasses.

**HookMethodsDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Hooks: optional steps with a default (often "do nothing") that subclasses MAY override. */
public class HookMethodsDemo {

    abstract static class Packing {
        final List<String> pack(List<String> items) {
            List<String> steps = new ArrayList<>();
            beforePacking(steps);
            steps.add("pack " + items);
            if (needsColdChain(items)) steps.add("add ice packs");
            steps.add("seal bag");
            afterPacking(steps);
            return steps;
        }
        protected void beforePacking(List<String> steps) { }                              // hook: default no-op
        protected boolean needsColdChain(List<String> items) { return false; }           // hook: default answer
        protected void afterPacking(List<String> steps) { }
    }

    static final class StandardPacking extends Packing { }

    static final class GroceryPacking extends Packing {
        @Override protected boolean needsColdChain(List<String> items) { return items.stream().anyMatch(i -> i.startsWith("FROZEN")); }
        @Override protected void afterPacking(List<String> steps) { steps.add("photo of sealed bag for proof"); }
    }

    static final class GiftPacking extends Packing {
        @Override protected void beforePacking(List<String> steps) { steps.add("remove price tags"); }
        @Override protected void afterPacking(List<String> steps) { steps.add("add gift note"); }
    }

    public static void main(String[] args) {
        List<String> items = List.of("FROZEN-PEAS", "BREAD");
        System.out.println("standard: " + new StandardPacking().pack(items));
        System.out.println("grocery:  " + new GroceryPacking().pack(items));
        System.out.println("gift:     " + new GiftPacking().pack(List.of("CHOCOLATES")));
    }
}
```

1. A "before" hook with an empty default.
2. A hook that answers a question; the default says no cold chain.
3. A subclass that overrides nothing still works with all defaults.
4. "After" hooks add steps at the end without touching the core sequence.

**Run it:** `java HookMethodsDemo.java`

```text
standard: [pack [FROZEN-PEAS, BREAD], seal bag]
grocery:  [pack [FROZEN-PEAS, BREAD], add ice packs, seal bag, photo of sealed bag for proof]
gift:     [remove price tags, pack [CHOCOLATES], seal bag, add gift note]
```

| | Abstract step | Hook |
|---|---|---|
| Subclass must implement | Yes | No |
| Default behaviour | None | Yes (often no-op) |
| Adding one later | Breaks every subclass | Compatible |
| Typical names | `doX`, `createX` | `beforeX`, `afterX`, `onX`, `shouldX` |

**Use hooks** for optional extension points. **Avoid** too many hooks: a base class with 15 hooks is hard to understand.
**Real-world Java:** Servlet `HttpServlet.doGet`/`doPost` (defaults return 405), JUnit's `@BeforeEach`/`@AfterEach` (annotation-based hooks), Spring's `OncePerRequestFilter.shouldNotFilter`.
**Best practice:** name hooks by when they run (`beforePacking`). **Gotcha:** an overridden hook that forgets to call `super` can silently drop base behaviour; keep hook defaults empty so `super` calls aren't needed.

### The Hollywood Principle

**Description.** "Don't call us, we'll call you." In Template Method (and frameworks in general), the base class or framework owns the control flow and calls your code at defined points. You don't write the loop, error handling or lifecycle; you write `process(item)`.

**Advantages / features.** Framework code handles the hard, repetitive parts consistently; your code is small and focused.

**HollywoodDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** "Don't call us, we'll call you": the framework owns the loop and calls your code at the right moments. */
public class HollywoodDemo {

    // ===================== A tiny batch framework =====================
    abstract static class BatchJob<T> {
        final String run(List<T> items) {
            onStart();
            int ok = 0, failed = 0;
            for (T item : items) {
                try { process(item); ok++; }
                catch (RuntimeException e) { failed++; onItemFailed(item, e); }
            }
            onFinish(ok, failed);
            return getClass().getSimpleName() + ": " + ok + " ok, " + failed + " failed";
        }
        protected void onStart() { }
        protected abstract void process(T item);
        protected void onItemFailed(T item, RuntimeException e) { }
        protected void onFinish(int ok, int failed) { }
    }

    // ===================== Your code: only the parts that are yours =====================
    static final class ExpireOldCarts extends BatchJob<String> {
        final List<String> log = new ArrayList<>();
        protected void process(String cartId) {
            if (cartId.isBlank()) throw new IllegalArgumentException("blank cart id");
            log.add("expired " + cartId);
        }
        @Override protected void onItemFailed(String item, RuntimeException e) { log.add("skipped '" + item + "': " + e.getMessage()); }
    }

    public static void main(String[] args) {
        ExpireOldCarts job = new ExpireOldCarts();
        System.out.println(job.run(List.of("cart-1", " ", "cart-3")));
        job.log.forEach(l -> System.out.println("  " + l));
    }
}
```

1. The framework owns the loop, the counting and the lifecycle.
2. Per-item error handling is the framework's job; one bad item doesn't stop the batch.
3. Your only required job: process one item.
4. You start the job; the framework decides when to call `process` and the hooks.

**Run it:** `java HollywoodDemo.java`

```text
ExpireOldCarts: 2 ok, 1 failed
  expired cart-1
  skipped ' ': blank cart id
  expired cart-3
```

| | Library (you call it) | Framework (it calls you) |
|---|---|---|
| Who owns control flow | Your code | The framework |
| Example | `List.sort`, Jackson `ObjectMapper` | Spring MVC, JUnit, Spring Batch |
| Flexibility | Higher | Lower, but consistent |
| Boilerplate | Yours to write | Handled for you |

**Use it** to build internal frameworks for repetitive jobs (batch processing, imports, partner calls). **Avoid** building a framework for two use cases.
**Real-world Java:** JUnit calls your test methods; Spring calls your `@Controller` methods and `@Scheduled` jobs; servlet containers call `service()`.
**Best practice:** document exactly when each callback runs. **Gotcha:** frameworks hide control flow; debugging needs knowledge of the lifecycle.

### Template Method with lambdas instead of subclasses

**Description.** The fixed algorithm becomes a method (often generic) that takes the varying steps as functional parameters. Same structure, no inheritance: this is Template Method implemented with [Strategy](strategy.md)-style composition.

**Advantages / features.** No class hierarchy; steps combinable freely; easy to test with simple lambdas.

**LambdaTemplateDemo.java**

```java
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Template Method without inheritance: the fixed algorithm takes the varying steps as functions. */
public class LambdaTemplateDemo {

    record Row(String sku, String price) { }

    /** The template: parse -> validate -> transform, with counts. Steps are parameters, not overrides. */
    static <T> String importRows(List<Row> rows, Function<Row, T> parse, Predicate<T> valid, Function<T, String> apply) {
        int ok = 0, rejected = 0;
        StringBuilder out = new StringBuilder();
        for (Row r : rows) {
            T value;
            try { value = parse.apply(r); } catch (RuntimeException e) { rejected++; continue; }
            if (!valid.test(value)) { rejected++; continue; }
            out.append(apply.apply(value)).append("; ");
            ok++;
        }
        return out + "ok=" + ok + " rejected=" + rejected;
    }

    record Price(String sku, long cents) { }

    public static void main(String[] args) {
        List<Row> rows = List.of(new Row("MILK", "68.00"), new Row("EGGS", "abc"), new Row("RICE", "-5"), new Row("OIL", "189.50"));

        String prices = importRows(rows,
                r -> new Price(r.sku(), Math.round(Double.parseDouble(r.price()) * 100)),
                p -> p.cents() > 0,
                p -> p.sku() + "=" + p.cents());
        System.out.println("prices: " + prices);

        String skusOnly = importRows(rows, Row::sku, s -> s.length() <= 4, s -> s.toLowerCase());
        System.out.println("skus:   " + skusOnly);
    }
}
```

1. The template is a method; parse, validate and apply are parameters.
2. A price import: three small lambdas.
3. A completely different import reuses the same template.

**Run it:** `java LambdaTemplateDemo.java`

```text
prices: MILK=6800; OIL=18950; ok=2 rejected=2
skus:   milk; eggs; rice; oil; ok=4 rejected=0
```

| | Inheritance-based | Lambda-based |
|---|---|---|
| Coupling | Subclass tied to base class | None beyond function types |
| Combining variations | Hard (single inheritance) | Easy |
| Shared state between steps | Easy (fields) | Must pass through parameters |
| Number of steps | Fine with many | Awkward beyond 3–4 parameters |
| Discoverability | Abstract methods tell you what to write | Parameter names must be clear |

**Use lambdas** for 1–3 varying steps. **Use inheritance** when there are many steps and shared state, or the framework expects subclasses.
**Real-world Java:** Spring's `JdbcTemplate.query(sql, RowMapper)`, `TransactionTemplate.execute(callback)`, `RestTemplate.execute(..., RequestCallback, ResponseExtractor)`.
**Best practice:** when there are more than three function parameters, group them into a small interface. **Gotcha:** checked exceptions don't fit `Function`; define your own functional interface that declares them.

### Template Method in the JDK and Spring (`JdbcTemplate`)

**Description.** The JDK's abstract collections are templates: implement `get` and `size` in `AbstractList` and you inherit `iterator`, `contains`, `indexOf`, `subList`, `equals` and more. Spring's `*Template` classes (`JdbcTemplate`, `TransactionTemplate`) are callback-style templates: they own a resource's lifecycle (open, begin, commit or roll back, close), and you pass the work.

**Advantages / features.** Huge reuse from a few methods; resource handling that can't be done wrong by callers.

**JdkSpringTemplateDemo.java**

```java
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Template Method in the JDK (AbstractList) and the Spring JdbcTemplate/TransactionTemplate style, in miniature. */
public class JdkSpringTemplateDemo {

    // ===================== JDK: implement 2 primitive methods, inherit ~30 others =====================
    static final class DeliveryWindows extends AbstractList<String> {
        private final int firstHour, lastHour;
        DeliveryWindows(int firstHour, int lastHour) { this.firstHour = firstHour; this.lastHour = lastHour; }
        @Override public String get(int i) {
            if (i < 0 || i >= size()) throw new IndexOutOfBoundsException(i);
            return "%02d:00-%02d:00".formatted(firstHour + i, firstHour + i + 1);
        }
        @Override public int size() { return lastHour - firstHour; }
    }

    // ===================== Spring-style: the template owns the resource, you supply the callback =====================
    static final class FakeConnection {
        final List<String> log = new ArrayList<>();
        void begin() { log.add("BEGIN"); }
        void exec(String sql) { log.add(sql); if (sql.contains("FAIL")) throw new IllegalStateException("constraint violated"); }
        void commit() { log.add("COMMIT"); }
        void rollback() { log.add("ROLLBACK"); }
        void close() { log.add("CLOSE"); }
    }

    static final class TxTemplate {
        <T> T inTransaction(FakeConnection c, Function<FakeConnection, T> work) {
            c.begin();
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.close();
            }
        }
    }

    public static void main(String[] args) {
        DeliveryWindows windows = new DeliveryWindows(8, 12);
        System.out.println("windows " + windows + ", contains 10:00-11:00? " + windows.contains("10:00-11:00")
                + ", indexOf 11:00-12:00 = " + windows.indexOf("11:00-12:00") + ", subList " + windows.subList(1, 3));

        TxTemplate tx = new TxTemplate();
        FakeConnection ok = new FakeConnection();
        int rows = tx.inTransaction(ok, c -> { c.exec("UPDATE stock SET qty = qty - 1 WHERE sku = 'MILK'"); return 1; });
        System.out.println("ok (" + rows + " row): " + ok.log);

        FakeConnection bad = new FakeConnection();
        try { tx.inTransaction(bad, c -> { c.exec("INSERT order"); c.exec("INSERT FAIL"); return 0; }); }
        catch (IllegalStateException e) { System.out.println("failed: " + bad.log + " -> " + e.getMessage()); }
    }
}
```

1. Two primitive methods give a full read-only `List`.
2. The template owns begin, commit and close; the callback does the work.
3. On any exception, the template rolls back and always closes.

**Run it:** `java JdkSpringTemplateDemo.java`

```text
windows [08:00-09:00, 09:00-10:00, 10:00-11:00, 11:00-12:00], contains 10:00-11:00? true, indexOf 11:00-12:00 = 3, subList [09:00-10:00, 10:00-11:00]
ok (1 row): [BEGIN, UPDATE stock SET qty = qty - 1 WHERE sku = 'MILK', COMMIT, CLOSE]
failed: [BEGIN, INSERT order, INSERT FAIL, ROLLBACK, CLOSE] -> constraint violated
```

| Template | You implement | You inherit or get for free |
|----------|---------------|-----------------------------|
| `AbstractList` | `get`, `size` | iteration, search, `subList`, `equals`, `hashCode`, `toString` |
| `AbstractMap` | `entrySet` | `get`, `containsKey`, `equals`, `toString`, ... |
| `InputStream` | `read()` | `read(byte[])`, `readAllBytes`, `transferTo` |
| Spring `JdbcTemplate` | SQL + `RowMapper` | connections, statements, exception translation |
| Spring `TransactionTemplate` | the work callback | begin, commit, rollback |

**Use** JDK abstract collections when exposing custom data as a collection. **Avoid** hand-writing resource lifecycles when a template exists.
**Real-world Java:** see the table; also `HttpServlet`, `java.util.concurrent.AbstractExecutorService`.
**Best practice:** override more methods (like `read(byte[])`) only for performance, keeping behaviour identical. **Gotcha:** `AbstractList` subclasses that don't override `add` throw `UnsupportedOperationException`, which surprises callers expecting a mutable list.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L1)

**Scenario:** Every store report has the same header and footer; each report type has its own title and body.

**DailyReportDemo.java**

```java
import java.util.Map;
import java.util.TreeMap;

/** Minimal Template Method: every store report has the same layout; each report type fills the body. */
public class DailyReportDemo {

    abstract static class StoreReport {
        final String render(String storeId, String date) {
            return "== " + title() + " | " + storeId + " | " + date + " ==\n" + body() + "\n-- generated by FreshCart ops --";
        }
        protected abstract String title();
        protected abstract String body();
    }

    static final class SalesReport extends StoreReport {
        private final Map<String, Long> salesBySku;
        SalesReport(Map<String, Long> s) { salesBySku = new TreeMap<>(s); }
        protected String title() { return "Daily sales"; }
        protected String body() {
            long total = salesBySku.values().stream().mapToLong(Long::longValue).sum();
            return salesBySku + "\ntotal " + total;
        }
    }

    static final class WastageReport extends StoreReport {
        protected String title() { return "Wastage"; }
        protected String body() { return "expired: BREAD x4, MILK x2"; }
    }

    public static void main(String[] args) {
        System.out.println(new SalesReport(Map.of("MILK-1L", 5_418L, "BREAD", 4_233L)).render("pune-01", "2026-11-02"));
        System.out.println(new WastageReport().render("pune-01", "2026-11-02"));
    }
}
```

1. The layout is fixed in one method.
2. Each report type only writes its body.

**Run it:** `java DailyReportDemo.java`

**Output:**

```text
== Daily sales | pune-01 | 2026-11-02 ==
{BREAD=4233, MILK-1L=5418}
total 9651
-- generated by FreshCart ops --
== Wastage | pune-01 | 2026-11-02 ==
expired: BREAD x4, MILK x2
-- generated by FreshCart ops --
```

**What to notice:**

- Changing the footer once changes every report.
- A new report type is one small class.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** Suppliers send price files in different formats. Parsing differs per supplier; the safety rules don't: unknown SKUs and non-positive prices are rejected, and any change over 30% goes to a human for review instead of being applied.

**SupplierPriceImportDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Realistic Template Method: supplier price files differ in format; validation and safety rules are shared and fixed. */
public class SupplierPriceImportDemo {

    record PriceUpdate(String sku, long cents) { }
    record Result(List<PriceUpdate> applied, List<String> rejected, List<String> needsReview) { }

    abstract static class PriceImport {
        private static final double MAX_AUTO_CHANGE = 0.30;

        final Result run(String file, Map<String, Long> currentPrices) {
            List<PriceUpdate> applied = new ArrayList<>(); List<String> rejected = new ArrayList<>(), review = new ArrayList<>();
            for (String line : lines(file)) {
                PriceUpdate u;
                try { u = parse(line); }
                catch (RuntimeException e) { rejected.add("'" + line + "' (" + e.getMessage() + ")"); continue; }
                Long old = currentPrices.get(u.sku());
                if (old == null) { rejected.add(u.sku() + " (unknown sku)"); continue; }
                if (u.cents() <= 0) { rejected.add(u.sku() + " (non-positive price)"); continue; }
                if (Math.abs(u.cents() - old) > old * MAX_AUTO_CHANGE) {
                    review.add(u.sku() + " " + old + " -> " + u.cents());
                    continue;
                }
                applied.add(u);
            }
            return new Result(applied, rejected, review);
        }

        protected List<String> lines(String file) {                                       // hook: most suppliers have a header row
            return file.lines().skip(1).filter(l -> !l.isBlank()).toList();
        }
        protected abstract PriceUpdate parse(String line);
    }

    /** GreenValley sends CSV in rupees with decimals: sku,price */
    static final class GreenValleyImport extends PriceImport {
        protected PriceUpdate parse(String line) {
            String[] f = line.split(",");
            return new PriceUpdate(f[0].strip(), Math.round(Double.parseDouble(f[1].strip()) * 100));
        }
    }

    /** DairyDay sends pipe-separated paise, no header: price|sku */
    static final class DairyDayImport extends PriceImport {
        @Override protected List<String> lines(String file) { return file.lines().filter(l -> !l.isBlank()).toList(); }
        protected PriceUpdate parse(String line) {
            String[] f = line.split("\\|");
            return new PriceUpdate(f[1].strip(), Long.parseLong(f[0].strip()));
        }
    }

    public static void main(String[] args) {
        Map<String, Long> current = Map.of("RICE-5KG", 54_900L, "OIL-1L", 18_900L, "MILK-1L", 6_800L, "CURD-500", 4_500L);
        Result gv = new GreenValleyImport().run("sku,price\nRICE-5KG,559.00\nOIL-1L,299.00\nATTA-5KG,265\nOIL-2L,abc\n", current);
        System.out.println("GreenValley applied " + gv.applied() + "\n  rejected " + gv.rejected() + "\n  review " + gv.needsReview());
        Result dd = new DairyDayImport().run("7000|MILK-1L\n0|CURD-500\n", current);
        System.out.println("DairyDay applied " + dd.applied() + "\n  rejected " + dd.rejected() + "\n  review " + dd.needsReview());
    }
}
```

1. The import loop and every safety rule live in the base class.
2. Big price swings are held for review: a supplier typo can't triple a price on the app.
3. Each supplier implements only how to parse one line.
4. A hook: DairyDay files have no header row.

**Run it:** `java SupplierPriceImportDemo.java`

**Output:**

```text
GreenValley applied [PriceUpdate[sku=RICE-5KG, cents=55900]]
  rejected [ATTA-5KG (unknown sku), 'OIL-2L,abc' (For input string: "abc")]
  review [OIL-1L 18900 -> 29900]
DairyDay applied [PriceUpdate[sku=MILK-1L, cents=7000]]
  rejected [CURD-500 (non-positive price)]
  review []
```

**What to notice:**

- The oil price jump (189 to 299) was held for review, not applied.
- Bad rows are reported with the reason, and the rest of the file still imports.
- A new supplier can't forget the 30% rule, because it isn't theirs to implement.

### Example 3: Production-grade

**Scenario:** FreshCart calls many partner APIs (couriers, payments). Every call must validate input, retry only transient errors with exponential backoff, map failures into one exception type with the partner name, and record metrics. Partners differ in how they're called and which errors are retryable; payment status polling needs more attempts and shorter delays.

**PartnerCallTemplateDemo.java**

```java
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Production-grade Template Method: every call to a partner API (courier, payments) follows the same
 * resilience recipe: validate, call, classify errors, retry with exponential backoff, map errors, record metrics.
 * Each partner only implements the call itself and how to classify its own errors.
 */
public class PartnerCallTemplateDemo {

    interface Sleeper { void sleep(Duration d); }                                         // injected, so tests don't wait

    static final class PartnerException extends RuntimeException {
        PartnerException(String partner, String op, int attempts, Throwable cause) {
            super(partner + " " + op + " failed after " + attempts + " attempt(s): " + cause.getMessage(), cause);
        }
    }

    static final class TransientError extends RuntimeException { TransientError(String m) { super(m); } }

    abstract static class PartnerCall<REQ, RES> {
        private final Sleeper sleeper;
        final Map<String, LongAdder> metrics = new ConcurrentHashMap<>();
        final List<Duration> backoffs = new ArrayList<>();

        PartnerCall(Sleeper sleeper) { this.sleeper = sleeper; }

        final RES execute(REQ request) {
            validate(request);
            Duration delay = initialBackoff();
            for (int attempt = 1; ; attempt++) {
                try {
                    RES result = doCall(request);
                    count("ok");
                    return result;
                } catch (RuntimeException e) {
                    boolean retry = isRetryable(e) && attempt < maxAttempts();
                    count(retry ? "retry" : "failed");
                    if (!retry) throw new PartnerException(partner(), operation(), attempt, e);
                    backoffs.add(delay);
                    sleeper.sleep(delay);
                    delay = delay.multipliedBy(2);
                }
            }
        }

        // ---- steps every partner must provide ----
        protected abstract String partner();
        protected abstract String operation();
        protected abstract RES doCall(REQ request);

        // ---- hooks with safe defaults ----
        protected void validate(REQ request) { }
        protected boolean isRetryable(RuntimeException e) { return e instanceof TransientError; }
        protected int maxAttempts() { return 3; }
        protected Duration initialBackoff() { return Duration.ofMillis(200); }

        private void count(String outcome) { metrics.computeIfAbsent(partner() + "." + operation() + "." + outcome, k -> new LongAdder()).increment(); }
    }

    // ===================== Partner 1: courier booking =====================
    record Booking(String orderId, String pincode) { }

    static final class QuickDropBooking extends PartnerCall<Booking, String> {
        private final Deque<String> scriptedResponses;                                     // simulates the remote API
        QuickDropBooking(Sleeper s, String... responses) { super(s); scriptedResponses = new ArrayDeque<>(List.of(responses)); }
        protected String partner() { return "quickdrop"; }
        protected String operation() { return "book"; }
        @Override protected void validate(Booking b) {
            if (!b.pincode().matches("\\d{6}")) throw new IllegalArgumentException("bad pincode " + b.pincode());
        }
        protected String doCall(Booking b) {
            String r = scriptedResponses.poll();
            if (r.equals("503")) throw new TransientError("HTTP 503");
            if (r.equals("422")) throw new IllegalStateException("HTTP 422 address not serviceable");
            return "booking " + r + " for " + b.orderId();
        }
    }

    // ===================== Partner 2: payment status (different retry policy) =====================
    static final class PayLinkStatus extends PartnerCall<String, String> {
        private int calls;
        PayLinkStatus(Sleeper s) { super(s); }
        protected String partner() { return "paylink"; }
        protected String operation() { return "status"; }
        @Override protected int maxAttempts() { return 5; }
        @Override protected Duration initialBackoff() { return Duration.ofMillis(50); }
        @Override protected boolean isRetryable(RuntimeException e) { return super.isRetryable(e) || e.getMessage().contains("PENDING"); }
        protected String doCall(String paymentId) {
            if (++calls < 4) throw new IllegalStateException("status PENDING");
            return paymentId + " CAPTURED";
        }
    }

    public static void main(String[] args) {
        List<String> slept = new ArrayList<>();
        Sleeper fakeSleeper = d -> slept.add(d.toMillis() + "ms");

        var ok = new QuickDropBooking(fakeSleeper, "503", "503", "QD-778");
        System.out.println(ok.execute(new Booking("FC-1601", "411001")) + ", backoffs " + ok.backoffs.stream().map(d -> d.toMillis() + "ms").toList());

        var notServiceable = new QuickDropBooking(fakeSleeper, "422");
        try { notServiceable.execute(new Booking("FC-1602", "411001")); } catch (PartnerException e) { System.out.println(e.getMessage()); }

        var down = new QuickDropBooking(fakeSleeper, "503", "503", "503");
        try { down.execute(new Booking("FC-1603", "411001")); } catch (PartnerException e) { System.out.println(e.getMessage()); }

        try { ok.execute(new Booking("FC-1604", "41")); } catch (IllegalArgumentException e) { System.out.println("rejected before calling: " + e.getMessage()); }

        var pay = new PayLinkStatus(fakeSleeper);
        System.out.println(pay.execute("pay_91") + ", backoffs " + pay.backoffs.stream().map(d -> d.toMillis() + "ms").toList());

        var all = new TreeMap<String, Long>();
        for (var c : List.of(ok, notServiceable, down)) c.metrics.forEach((k, v) -> all.merge(k, v.sum(), Long::sum));
        pay.metrics.forEach((k, v) -> all.merge(k, v.sum(), Long::sum));
        System.out.println(all);
        System.out.println("total time slept (fake): " + slept);
    }
}
```

1. The resilience recipe is the `final` template: no partner can skip retries, metrics or error mapping.
2. Retry only if the error is retryable *and* attempts remain.
3. Exponential backoff: 200 ms, 400 ms, ...
4. A hook with a safe default: only `TransientError` is retried.
5. Payment status overrides hooks for more attempts, shorter delays, and treating "PENDING" as retryable.

**Run it:** `java PartnerCallTemplateDemo.java`

**Output:**

```text
booking QD-778 for FC-1601, backoffs [200ms, 400ms]
quickdrop book failed after 1 attempt(s): HTTP 422 address not serviceable
quickdrop book failed after 3 attempt(s): HTTP 503
rejected before calling: bad pincode 41
pay_91 CAPTURED, backoffs [50ms, 100ms, 200ms]
{paylink.status.ok=1, paylink.status.retry=3, quickdrop.book.failed=2, quickdrop.book.ok=1, quickdrop.book.retry=4}
total time slept (fake): [200ms, 400ms, 200ms, 400ms, 50ms, 100ms, 200ms]
```

**What to notice:**

- Two 503s then success: the caller just sees the booking.
- The 422 wasn't retried; retrying a permanent error only wastes time.
- Invalid input was rejected before any network call.
- Sleeping is injected, so the demo (and tests) run instantly.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Simplicity | Procedure in one place; small subclasses | Logic split between base and subclasses (yo-yo reading) |
| Correctness | Required steps and checks can't be skipped | Fragile base class: base changes ripple to subclasses |
| Extensibility | New variants by subclassing; hooks for optional steps | Single inheritance limits combinations |
| Performance | None to speak of | None |
| Testability | Base procedure tested once | Testing an abstract class needs a test subclass |
| Learning curve | Familiar from frameworks | Hook-heavy bases are hard to understand |

## 8. When to Use / When NOT to Use

**Use it when:**

- several classes share a procedure and differ in a few steps;
- some steps or checks must never be skipped (validation, transactions, retries);
- you're building a small internal framework (batch jobs, imports, API clients).

**Don't use it when:**

- variations must be combined at runtime: use [Strategy](strategy.md) or [Decorator](decorator.md);
- there's only one step that varies: pass a lambda;
- the base class would need many hooks to fit everyone: split it.

## 9. Best Practices

1. **Make the template method `final`.** *(Consensus.)*
2. **Make steps `protected`, and hooks no-op by default.** *(Consensus.)*
3. **Prefer composition (lambdas, strategies) when inheritance isn't otherwise needed.** *(Consensus: Effective Java Item 18 "Favor composition over inheritance".)*
4. **Keep the number of steps and hooks small** (fewer than about seven). *(Opinion.)*
5. **Inject time and sleep** into templates that wait or retry. *(Consensus.)*
6. **Document the call order of steps** in the base class Javadoc. *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Overridable template**  
> **Symptom:** one subclass skips validation, and bad data reaches production.
> **Root cause:** the template method wasn't `final`, and the subclass overrode it.
> **Fix:** `final` template; only steps are overridable.

> 🚨 **Retry storm (production failure mode)**  
> **Symptom:** a partner's brief outage turned into a 20-minute one; their API was flooded.
> **Root cause:** each client implemented its own retries without backoff; some retried permanent errors forever.
> **Fix:** one template with backoff, attempt limits and retryable classification (Example 3), plus jitter in real systems.

> 🚨 **Deep template hierarchies**  
> **Symptom:** `AbstractBaseImporter` → `AbstractCsvImporter` → `AbstractSupplierCsvImporter` → `GreenValleyImporter`, each overriding some steps.
> **Root cause:** inheritance used to share every bit of code.
> **Fix:** one template level; share helpers through composition.

> 🚨 **Constructor calls a step**  
> **Symptom:** `NullPointerException` in a subclass step during construction.
> **Root cause:** the base constructor called an overridable step before subclass fields were set.
> **Fix:** never call overridable methods from constructors.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `maxAttempts` (Example 3) | 3 | Total attempts per call | Partner reliability or SLA | Too high: slow failures, load on partner |
| `initialBackoff` | 200 ms | First retry delay, doubled each time | Partner rate limits | Too low: retry storms |
| `MAX_AUTO_CHANGE` (Example 2) | 30% | Price change applied without review | Volatile categories (vegetables) | Bad prices published automatically |

## 12. Security Considerations

- Put security checks (authorisation, input validation, audit logging) in the `final` template so no subclass can forget them.
- Don't let subclasses override hooks that decide security outcomes unless reviewed; mark such methods `final`.
- Error mapping in templates should avoid leaking partner responses with sensitive data into logs and user messages.

## 13. Performance & Scalability

Template Method adds a few virtual calls, which the JIT inlines when there are few subclasses. The real performance effects come from what templates enforce: retries add latency and load, so cap them; validation up front saves wasted calls. For high-throughput templates, add jitter to backoff so many clients don't retry at the same moment.

## 14. Testing It

Test the template's behaviour once, using a concrete subclass with scripted responses and a fake sleeper:

**PartnerCallTemplateTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerCallTemplateTest {

    private final List<Duration> slept = new ArrayList<>();
    private final PartnerCallTemplateDemo.Sleeper sleeper = slept::add;
    private final PartnerCallTemplateDemo.Booking booking = new PartnerCallTemplateDemo.Booking("o1", "411001");

    @Test
    void retriesTransientErrorsWithDoublingBackoff() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper, "503", "503", "QD-1");
        assertEquals("booking QD-1 for o1", call.execute(booking));
        assertEquals(List.of(Duration.ofMillis(200), Duration.ofMillis(400)), slept);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper, "422", "QD-never");
        assertThrows(PartnerCallTemplateDemo.PartnerException.class, () -> call.execute(booking));
        assertEquals(List.of(), slept);
        assertEquals(1, call.metrics.get("quickdrop.book.failed").sum());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper, "503", "503", "503", "QD-late");
        var e = assertThrows(PartnerCallTemplateDemo.PartnerException.class, () -> call.execute(booking));
        assertEquals("quickdrop book failed after 3 attempt(s): HTTP 503", e.getMessage());
    }

    @Test
    void validationRunsBeforeAnyCall() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper);   // no scripted responses: a call would fail
        assertThrows(IllegalArgumentException.class, () -> call.execute(new PartnerCallTemplateDemo.Booking("o2", "12")));
    }
}
```

Each partner subclass then only needs tests for its own `doCall` and `isRetryable`.

## 15. Observability & Operations

Templates are the ideal place for consistent telemetry: one metric name pattern (`partner.operation.outcome`), one log format, one trace span per attempt. Alert on retry ratio (retries / calls) per partner; a rising ratio is an early warning before failures reach customers.

## 16. Version Notes

| Java | Change relevant to Template Method |
|------|-----------------------------------|
| 1.2 | `AbstractList`, `AbstractMap` and other collection templates |
| 8 | Lambdas: callback-style templates; default methods as hooks in interfaces |
| 9 | `InputStream.readAllBytes`, `transferTo` built on the `read` template |
| 17 | Sealed classes can restrict which subclasses may extend a template |

## 17. Expert Deep Dive

- **Internal frameworks are Template Methods at scale.** Every platform team builds them: job runners, API clients, importers. The senior skill is keeping them small, documented and optional, so teams aren't forced into inheritance hierarchies that don't fit.
- **Interface default methods as templates.** An interface can hold a default "template" method calling abstract methods. It avoids the single-inheritance limit, but can't be `final`, so implementers can override it.
- **Composition-first templates.** Spring's evolution from `JdbcDaoSupport` (inherit) to `JdbcTemplate` (pass callbacks) to `JdbcClient` (fluent API) shows the industry moving from inheritance to composition for the same idea.
- **Sealed templates.** `sealed abstract class Fulfilment permits DarkStore, PartnerStore` documents and enforces the complete set of variants.

> 📖 **War story: the importer nobody could change**  
> A grocery company's catalogue team had an `AbstractImporter` with 22 hook methods built over five years. Every supplier subclass overrode a different mix, and two overrode the template method itself to "fix" ordering. A change to the validation step broke four suppliers' imports in production. The team replaced it with a short `final` pipeline (read, parse, validate, apply) whose parse step was a function per supplier; validation became non-overridable. **Lesson:** a template's value is in what it *prevents* subclasses from changing; once everything is overridable, it's just shared code with extra risk.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is Template Method?** A **fixed algorithm skeleton** in a base class, with **steps filled in by subclasses**.
2. **Why make the template method `final`?** So subclasses **can't change the order or skip steps**.
3. **What is a hook?** An **optional step with a default** that subclasses may override.
4. **Give a JDK example.** **`AbstractList`** (implement `get`, `size`), **`InputStream.read()`**.
5. **What's the Hollywood Principle?** **"Don't call us, we'll call you"**: the framework calls your code.

**▸ 🟡 L3**

1. **Template Method vs Strategy?** **Inheritance and fixed structure** vs **composition and swappable whole algorithms**.
2. **How would you implement it without inheritance?** Pass steps as **lambdas or a small interface** (callback style, like `JdbcTemplate`).
3. **What's the fragile base class problem?** **Changes in the base class break subclasses** that depend on its internal behaviour.
4. **Why not call abstract steps from a constructor?** Subclass **fields aren't initialised** yet.
5. **How does Factory Method relate?** A factory method is often **one step of a template method**.

**▸ 🔴 L4–L5**

1. **Design a resilient partner-call framework.** **Final template** with validation, **classified retries with backoff and jitter**, error mapping, metrics, **per-partner hooks**.
2. **When do internal frameworks go wrong?** **Too many hooks, deep hierarchies**, forced inheritance; fix with composition and small APIs.
3. **How do you migrate from inheritance templates to callbacks?** Add a **callback-based template**, adapt old subclasses to it, migrate callers incrementally.
4. **How do you keep templates evolvable?** **Add hooks, never remove**; keep steps few; version the framework.
5. **Now 10x traffic and retries amplify partner outages. What do you change?** **Retry budgets, jitter, circuit breakers**, and lower attempt counts under load.

**▸ 💻 Hands-on**

**Task:** Add jitter to `PartnerCallTemplateDemo` backoff (±20%) without making tests random.

> ✅ **Solution**  
> ```java
> interface Jitter { double factor(); }                      // returns 0.8..1.2 in production
>
> // in PartnerCall: inject Jitter alongside Sleeper
> Duration jittered = Duration.ofMillis(Math.round(delay.toMillis() * jitter.factor()));
> sleeper.sleep(jittered);
>
> // production: () -> 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4
> // tests:      () -> 1.0
> ```
> Randomness is injected like time, so tests stay deterministic.

## 19. Practice

- [ ] Add a `CashOnDeliveryFulfilment` to `TemplateBasicsDemo` with a hook that adds "collect cash" after labelling.
    > 💡 **Hint**  
    > Add an `afterLabel(List<String> log)` hook with an empty default to the base class first.

- [ ] Rewrite `SupplierPriceImportDemo` so the parse step is a `Function<String, PriceUpdate>` instead of a subclass.
    > 💡 **Hint**  
    > `new PriceImport(line -> ...)` with the header-skipping hook as a boolean parameter.

- [ ] Make `PartnerCallTemplateDemo` stop retrying when total time slept would exceed 1 second.
    > 💡 **Hint**  
    > Track the sum of delays; treat "budget exceeded" like "no attempts left".

- [ ] Implement a read-only `Map<String, Long>` of SKU prices by extending `AbstractMap`.
    > 💡 **Hint**  
    > You only need `entrySet()`; return a set backed by your data.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: fixed algorithm skeleton; subclasses fill steps.
> - `final` template, `protected abstract` steps, no-op hooks.
> - Hollywood Principle: the base or framework calls you.
> - Modern Java: pass steps as lambdas (callback templates like `JdbcTemplate`).
> - Prefer composition when variations must combine; keep hooks few.
>
> ```java
> abstract class Import {
>     final Result run(String file) { /* read, parse(line), validate, apply */ }
>     protected abstract PriceUpdate parse(String line);
> }
> ```

## 21. Further Reading

- Gamma et al., *Design Patterns* (1994): Template Method.
- Joshua Bloch, *Effective Java*, 3rd edition, Item 18 "Favor composition over inheritance" and Item 19 "Design and document for inheritance or else prohibit it".
- [Spring Framework reference: Data Access with JDBC (JdbcTemplate)](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)
- [java.util.AbstractList API documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/AbstractList.html)

---

[← Previous: Observer](observer.md) · [Next: Adapter →](adapter.md)
