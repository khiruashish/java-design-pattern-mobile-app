# Testing Patterns

L2 🔵 Practitioner · ⏱ 40 min read · Prerequisites: [Dependency Injection](dependency-injection.md), [Strategy](../l1-foundations/strategy.md)

> 📌 **TL;DR**  
> Tests have their own design patterns. **Test doubles** (dummy, stub, fake, spy, mock) stand in for collaborators. **Object Mother** and **Test Data Builder** make test data short and readable. **Arrange-Act-Assert** gives every test the same three-part shape. Design patterns in production code (DI, Strategy, Decorator) create **seams** where doubles plug in. **Contract tests** run one suite against every implementation of an interface, including your fakes, so the fakes stay honest.

## 1. What It Is

**Plain English.** FreshCart's checkout talks to payments, inventory, SMS and the clock. A unit test can't charge real cards or wait until 10 pm. Testing patterns are the standard ways to replace those collaborators, build test data, structure each test, and check that replacements behave like the real thing.

**Definition.** The vocabulary comes from Gerard Meszaros, *xUnit Test Patterns* (2007), and Michael Feathers, *Working Effectively with Legacy Code* (2004):

- **Test double:** any object that replaces a real collaborator in a test. Five kinds: dummy, stub, fake, spy, mock.
- **Object Mother / Test Data Builder:** factories for test data.
- **Four-phase test / Arrange-Act-Assert:** set up, exercise, verify (and tear down).
- **Seam:** a place where behaviour can change without editing the code there.
- **Contract test:** an abstract test suite for an interface, run against each implementation.

## 2. Why It Exists

Tests that hit real services are slow, flaky and sometimes dangerous (real SMS, real charges). Tests that build data by hand are long and break whenever a constructor changes. Tests that mix many behaviours fail with "check failed" and no clue. And fakes written without checks drift from reality, so tests pass while production breaks. Each testing pattern targets one of these problems.

## 3. Mental Model / Analogy

**Analogy.** A film shoot. **Dummies** stand in the background, **stunt doubles** (stubs) do one scripted action, a **rehearsal set** (fake) works but isn't the real city, a **camera** (spy) records what actors did, and a **director with a script** (mock) stops the take if an actor says the wrong line. Before using the rehearsal set, you check it has the same doors as the real building (contract test).

**Technical model.** Code under test depends on interfaces. The test chooses the implementations (through constructor injection), arranges their state, performs one action, and asserts on outputs or on the recorded interactions.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Test doubles: dummy, stub, fake, spy, mock

**Description.** Meszaros's five kinds, from simplest to most opinionated:

- **Dummy:** fills a parameter; never used on this path.
- **Stub:** returns canned answers to steer the code (a declining payment, a fixed clock).
- **Fake:** a real, simplified implementation (an in-memory repository).
- **Spy:** records calls so the test can check them afterwards.
- **Mock:** pre-programmed with expectations and verifies them itself.

**Advantages / features.** Fast, deterministic tests; failure paths (declines, timeouts) on demand; no external side effects.

**TestDoublesDemo.java**

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The five test doubles (Meszaros), hand-written so you can see what each one is for.
 * The code under test: CheckoutService, with four collaborators.
 */
public class TestDoublesDemo {

    interface PaymentGateway { boolean charge(String orderId, long paise); }
    interface OrderRepository { void save(String orderId, String status); Optional<String> status(String orderId); }
    interface Notifier { void send(String to, String text); }
    interface AuditLog { void record(String event); }

    record CheckoutService(PaymentGateway payments, OrderRepository orders, Notifier notifier, AuditLog audit, Clock clock) {
        String checkout(String orderId, String phone, long paise) {
            if (!payments.charge(orderId, paise)) { orders.save(orderId, "PAYMENT_FAILED"); return "declined"; }
            orders.save(orderId, "PAID");
            notifier.send(phone, "Order " + orderId + " paid at " + clock.instant());
            return "ok";
        }
    }

    // DUMMY: passed only to fill a parameter; never used by this test path.
    static final AuditLog DUMMY_AUDIT = e -> { throw new AssertionError("dummy must not be called"); };

    // STUB: returns canned answers to drive the code down a path.
    static PaymentGateway decliningStub() { return (id, p) -> false; }

    // FAKE: a working, simplified implementation (an in-memory repository).
    static final class InMemoryOrders implements OrderRepository {
        final Map<String, String> rows = new LinkedHashMap<>();
        public void save(String id, String s) { rows.put(id, s); }
        public Optional<String> status(String id) { return Optional.ofNullable(rows.get(id)); }
    }

    // SPY: records calls so the test can check them AFTER the act step.
    static final class SpyNotifier implements Notifier {
        final List<String> sent = new ArrayList<>();
        public void send(String to, String text) { sent.add(to + " | " + text); }
    }

    // MOCK: pre-programmed with EXPECTATIONS; it fails itself if they aren't met.
    static final class MockPayments implements PaymentGateway {
        private final String expectedOrder; private final long expectedPaise; private int calls;
        MockPayments(String order, long paise) { expectedOrder = order; expectedPaise = paise; }
        public boolean charge(String id, long p) {
            calls++;
            if (!id.equals(expectedOrder) || p != expectedPaise) throw new AssertionError("unexpected charge " + id + " " + p);
            return true;
        }
        void verify() { if (calls != 1) throw new AssertionError("expected exactly 1 charge, got " + calls); }
    }

    public static void main(String[] args) {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-31T10:15:00Z"), ZoneOffset.UTC);

        InMemoryOrders orders = new InMemoryOrders();
        var declined = new CheckoutService(decliningStub(), orders, new SpyNotifier(), DUMMY_AUDIT, fixed);
        System.out.println("stub declines -> " + declined.checkout("FC-1", "98450", 64_900) + ", saved status " + orders.status("FC-1").orElseThrow());

        MockPayments mock = new MockPayments("FC-2", 64_900);
        SpyNotifier spy = new SpyNotifier();
        var happy = new CheckoutService(mock, orders, spy, DUMMY_AUDIT, fixed);
        System.out.println("mock expects one charge -> " + happy.checkout("FC-2", "98450", 64_900));
        mock.verify();
        System.out.println("mock verified; spy saw " + spy.sent);
        System.out.println("fake now holds " + orders.rows);
    }
}
```

1. A dummy that fails loudly if it's ever used, which proves this path doesn't touch auditing.
2. A stub: one canned answer that sends checkout down the decline path.
3. A fake: genuinely stores and returns orders, just in memory.
4. A spy: records what was sent; the test inspects it after the act step.
5. A mock: knows what call to expect and fails if it's wrong or missing.

**Run it:** `java TestDoublesDemo.java`

```text
stub declines -> declined, saved status PAYMENT_FAILED
mock expects one charge -> ok
mock verified; spy saw [98450 | Order FC-2 paid at 2026-10-31T10:15:00Z]
fake now holds {FC-1=PAYMENT_FAILED, FC-2=PAID}
```

| Double | Answers calls? | Records calls? | Has real logic? | Verifies itself? | Typical use |
|--------|---------------|----------------|-----------------|------------------|-------------|
| Dummy | No | No | No | No | Unused parameter |
| Stub | Canned | No | No | No | Drive a path (decline, time) |
| Fake | Yes | Via state | Simplified | No | Repository, cache, queue |
| Spy | Optional | Yes | No | No | Check outgoing messages |
| Mock | Programmed | Yes | No | Yes | Strict interaction protocols |

**Use** stubs and fakes by default, spies for outgoing side effects, mocks sparingly. **Avoid** mocking types you don't own (HTTP clients, JDBC); wrap them in your own interface and fake that.
**Real-world Java:** Mockito (`mock`, `when(...).thenReturn`, `verify`, `spy`), `Clock.fixed`, Spring's `MockMvc`, Testcontainers (real dependencies instead of doubles), WireMock (a fake HTTP server).
**Best practice:** assert on outcomes (state, return values) before interactions; interaction checks make tests brittle to refactoring. **Gotcha:** Mockito's "mock" is technically a stub or spy until you call `verify`; the words are used loosely, so say what you mean in code reviews.

### Object Mother and Test Data Builder

**Description.** An **Object Mother** is a class of named factory methods for typical test objects (`Orders.goldExpressOrder()`). A **Test Data Builder** (Nat Pryce) starts from sensible defaults and lets each test override only what it's about: `anOrder().coupon("FRESH10").build()`. They combine well: mother methods are built from the builder.

**Advantages / features.** Tests show only the data that matters to them; constructor changes are fixed in one place; names document the scenario.

**Before (every field in every test) and after:**

**TestDataBuilderDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Making test data readable: Object Mother for common named cases, Test Data Builder for "like the default, except...". */
public class TestDataBuilderDemo {

    record Item(String sku, int qty, long unitPaise) { }
    record Order(String id, String customerTier, String city, List<Item> items, boolean paid, String couponCode, boolean express) {
        long total() { return items.stream().mapToLong(i -> i.qty() * i.unitPaise()).sum(); }
    }

    // ---------- before: every test spells out every field ----------
    static Order before() {
        return new Order("FC-1", "silver", "Pune", List.of(new Item("MILK", 2, 6_400)), true, null, false);
    }

    // ---------- Object Mother: named, ready-made examples ----------
    static final class Orders {
        static Order typicalPaidOrder() { return anOrder().build(); }
        static Order goldExpressOrder() { return anOrder().tier("gold").express().build(); }
        static Order unpaidOrder() { return anOrder().unpaid().build(); }
    }

    // ---------- Test Data Builder: sensible defaults, override only what matters ----------
    static OrderBuilder anOrder() { return new OrderBuilder(); }
    static final class OrderBuilder {
        private String id = "FC-1", tier = "silver", city = "Pune", coupon = null;
        private final List<Item> items = new ArrayList<>(List.of(new Item("MILK", 2, 6_400)));
        private boolean paid = true, express = false;
        OrderBuilder id(String v) { id = v; return this; }
        OrderBuilder tier(String v) { tier = v; return this; }
        OrderBuilder city(String v) { city = v; return this; }
        OrderBuilder coupon(String v) { coupon = v; return this; }
        OrderBuilder unpaid() { paid = false; return this; }
        OrderBuilder express() { express = true; return this; }
        OrderBuilder withItem(String sku, int qty, long unitPaise) { items.add(new Item(sku, qty, unitPaise)); return this; }
        OrderBuilder withOnlyItem(String sku, int qty, long unitPaise) { items.clear(); return withItem(sku, qty, unitPaise); }
        Order build() { return new Order(id, tier, city, List.copyOf(items), paid, coupon, express); }
    }

    public static void main(String[] args) {
        System.out.println("before:  " + before());
        System.out.println("mother:  " + Orders.goldExpressOrder());
        Order bigCart = anOrder().withOnlyItem("RICE-5KG", 2, 42_000).coupon("FRESH10").build();
        System.out.println("builder: total=" + bigCart.total() + " coupon=" + bigCart.couponCode() + " (everything else default)");
        System.out.println("unpaid?  " + !Orders.unpaidOrder().paid());
    }
}
```

1. Before: the reader can't tell which of seven arguments matter, and every test breaks when a field is added.
2. Object Mother: named, reusable scenarios.
3. Test Data Builder: defaults for everything, fluent overrides for what the test is about.

**Run it:** `java TestDataBuilderDemo.java`

```text
before:  Order[id=FC-1, customerTier=silver, city=Pune, items=[Item[sku=MILK, qty=2, unitPaise=6400]], paid=true, couponCode=null, express=false]
mother:  Order[id=FC-1, customerTier=gold, city=Pune, items=[Item[sku=MILK, qty=2, unitPaise=6400]], paid=true, couponCode=null, express=true]
builder: total=84000 coupon=FRESH10 (everything else default)
unpaid?  true
```

| | Object Mother | Test Data Builder |
|-|---------------|-------------------|
| Strength | Named scenarios | Small, local variations |
| Weakness | Grows into a "god mother" with many near-duplicates | More code up front |
| Reads like | `Orders.unpaidOrder()` | `anOrder().unpaid().build()` |
| Best together | Mother methods call the builder | |

**Use** builders for domain objects with more than three or four fields. **Avoid** random data in assertions; if you use generated data, make it reproducible with a seed.
**Real-world Java:** Lombok `@Builder(toBuilder = true)` in test fixtures, Instancio and EasyRandom for generated data, AssertJ's soft assertions alongside builders.
**Best practice:** builder defaults should create a **valid** object, so tests only change what makes them interesting. **Gotcha:** shared mutable fixtures (one static order reused by many tests) cause order-dependent failures; build a fresh object per test.

### Arrange-Act-Assert

**Description.** Each test has three visible parts: **arrange** the state, **act** once, **assert** the outcome. Behaviour-driven teams call it Given-When-Then. One behaviour per test, named as a sentence, with assertions that print expected and actual values.

**Advantages / features.** A failing test's name says which behaviour broke; its message says how; readers find the action instantly.

**Before (one long test with bare checks) and after:**

**ArrangeActAssertDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Arrange-Act-Assert (Given-When-Then): one behaviour per test, three visible phases.
 * Both test styles run against a correct cart and against a cart with a bug, to show which failure message helps.
 */
public class ArrangeActAssertDemo {

    static final class Cart {
        private final boolean bug; private final Map<String, Integer> lines = new LinkedHashMap<>();
        Cart(boolean bug) { this.bug = bug; }
        void add(String sku, int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
            if (bug) lines.put(sku, qty); else lines.merge(sku, qty, Integer::sum);       // the bug: replaces instead of adding
        }
        int quantityOf(String sku) { return lines.getOrDefault(sku, 0); }
        int lineCount() { return lines.size(); }
    }

    static Supplier<Cart> newCart;

    // ---------- before: one long test checking many behaviours with bare booleans ----------
    static void cartWorks() {
        Cart c = newCart.get(); c.add("MILK", 1); check(c.quantityOf("MILK") == 1);
        c.add("MILK", 2); check(c.quantityOf("MILK") == 3); c.add("BREAD", 1); check(c.lineCount() == 2);
        try { c.add("EGG", 0); check(false); } catch (IllegalArgumentException e) { check(true); }
    }

    // ---------- after: one behaviour each, named as a sentence, with descriptive assertions ----------
    static void addingTheSameSkuTwiceAddsQuantities() {
        // arrange
        Cart cart = newCart.get();
        cart.add("MILK", 1);
        // act
        cart.add("MILK", 2);
        // assert
        assertEquals("MILK quantity", 3, cart.quantityOf("MILK"));
        assertEquals("line count", 1, cart.lineCount());
    }

    static void zeroQuantityIsRejected() {
        Cart cart = newCart.get();                                                                   // arrange
        IllegalArgumentException e = expectThrows(() -> cart.add("EGG", 0));                         // act
        assertEquals("message", "qty must be positive", e.getMessage());                             // assert
    }

    static void check(boolean condition) { if (!condition) throw new AssertionError("check failed"); }
    static void assertEquals(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected " + expected + " but was " + actual);
    }
    static IllegalArgumentException expectThrows(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException e) { return e; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    static void run(String name, Runnable test) {
        try { test.run(); System.out.println("  PASS " + name); }
        catch (AssertionError e) { System.out.println("  FAIL " + name + ": " + e.getMessage()); }
    }

    public static void main(String[] args) {
        for (boolean bug : new boolean[] {false, true}) {
            newCart = () -> new Cart(bug);
            System.out.println(bug ? "cart with a bug:" : "correct cart:");
            run("cartWorks", ArrangeActAssertDemo::cartWorks);
            run("adding the same SKU twice adds quantities", ArrangeActAssertDemo::addingTheSameSkuTwiceAddsQuantities);
            run("zero quantity is rejected", ArrangeActAssertDemo::zeroQuantityIsRejected);
        }
    }
}
```

1. Before: four behaviours in one test, checked with booleans. When it fails, you learn only that something failed.
2. After: one behaviour, three marked phases, a name that reads as a requirement.
3. A descriptive assertion: what was checked, expected and actual.

**Run it:** `java ArrangeActAssertDemo.java`

```text
correct cart:
  PASS cartWorks
  PASS adding the same SKU twice adds quantities
  PASS zero quantity is rejected
cart with a bug:
  FAIL cartWorks: check failed
  FAIL adding the same SKU twice adds quantities: MILK quantity: expected 3 but was 2
  PASS zero quantity is rejected
```

| Smell | AAA fix |
|-------|---------|
| Several act steps | Split into several tests |
| Asserting before acting | Move setup checks into arrange or delete them |
| `assertTrue(a == b)` | `assertEquals(b, a)` for a useful message |
| Test name `test1` | Name the behaviour: `zeroQuantityIsRejected` |
| Logic (`if`, loops) in tests | Parameterised tests |

**Use** AAA for every unit test. **Avoid** "and" in test names; it usually means two tests.
**Real-world Java:** JUnit 5 `@DisplayName`, `@ParameterizedTest`, `assertAll`; AssertJ's fluent assertions; Spock's `given:/when:/then:` blocks.
**Best practice:** several assertions are fine when they describe one outcome (quantity and line count after one add). **Gotcha:** a setup that needs 30 lines is a design smell in the production code, not just the test.

### Testing through seams created by patterns

**Description.** Michael Feathers defines a **seam** as "a place where you can alter behaviour in your program without editing in that place". Constructor injection ([Dependency Injection](dependency-injection.md)), [Strategy](../l1-foundations/strategy.md) and [Decorator](../l1-foundations/decorator.md) all create seams. `new RealSms()` and `LocalTime.now()` inside a method remove them.

**Advantages / features.** Time, randomness, network and files become controllable; tests become deterministic; the same seams later allow features like fault injection.

**Before (hard-wired) and after (seams):**

**SeamsDemo.java**

```java
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * A "seam" (Feathers) is a place where you can change behaviour without editing the code under test.
 * Dependency Injection, Strategy and Decorator create seams; `new` and static calls inside a method remove them.
 */
public class SeamsDemo {

    interface Sms { void send(String phone, String text); }
    static final class RealSms implements Sms { public void send(String p, String t) { throw new IllegalStateException("would call the real SMS provider"); } }

    // ---------- before: hard-wired; can't test without sending a real SMS at the real time of day ----------
    static final class SlotReminderBefore {
        String remind(String phone) {
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
            if (now.isAfter(LocalTime.of(21, 0))) return "too late, skipped";
            new RealSms().send(phone, "Your slot is tomorrow 8-10 am");
            return "sent";
        }
    }

    // ---------- after: the clock and the SMS sender are injected (seams) ----------
    record SlotReminder(Clock clock, Sms sms) {
        String remind(String phone) {
            LocalTime now = LocalTime.now(clock);
            if (now.isAfter(LocalTime.of(21, 0))) return "too late, skipped";
            sms.send(phone, "Your slot is tomorrow 8-10 am");
            return "sent";
        }
    }

    /** A decorator is also a seam: wrap the real collaborator to record or fault-inject without changing it. */
    record RecordingSms(Sms inner, List<String> log) implements Sms {
        public void send(String p, String t) { log.add(p + ": " + t); inner.send(p, t); }
    }

    static Clock at(String isoInstant) { return Clock.fixed(Instant.parse(isoInstant), ZoneId.of("Asia/Kolkata")); }

    public static void main(String[] args) {
        String outcome;
        try { outcome = new SlotReminderBefore().remind("98450"); } catch (IllegalStateException e) { outcome = "tried the real SMS provider"; }
        System.out.println("before: untestable; outcome depends on the wall clock (" + (outcome.contains("SMS") || outcome.contains("skipped") ? "skipped or real SMS" : outcome) + ")");

        List<String> log = new ArrayList<>();
        Sms quiet = new RecordingSms((p, t) -> { }, log);
        System.out.println("after, 18:30 IST: " + new SlotReminder(at("2026-10-31T13:00:00Z"), quiet).remind("98450") + " " + log);
        System.out.println("after, 21:30 IST: " + new SlotReminder(at("2026-10-31T16:00:00Z"), quiet).remind("98450"));
    }
}
```

1. Before: the wall clock and a real SMS client are created inside the method. The result depends on when you run the test.
2. After: the clock and the sender come in through the constructor, so tests choose them.
3. A decorator seam: wraps any sender to record (or delay, or fail) without changing it.

**Run it:** `java SeamsDemo.java`

```text
before: untestable; outcome depends on the wall clock (skipped or real SMS)
after, 18:30 IST: sent [98450: Your slot is tomorrow 8-10 am]
after, 21:30 IST: too late, skipped
```

| Hidden dependency | Seam | Test replacement |
|-------------------|------|------------------|
| `LocalDateTime.now()` | Inject `java.time.Clock` | `Clock.fixed(...)` |
| `new Random()` / `UUID.randomUUID()` | Inject `RandomGenerator` / `Supplier<UUID>` | Seeded or fixed values |
| `new HttpClient()` | Your own interface (port) | Fake or WireMock |
| Static utility with side effects | Instance behind an interface | Stub |
| `System.getenv` | Config object | Test config |

**Use** constructor injection for every collaborator with side effects or nondeterminism. **Avoid** making methods `protected` just to override them in tests; inject instead.
**Real-world Java:** `java.time.Clock`, Spring's constructor injection and `@TestConfiguration`, `java.util.random.RandomGenerator` (Java 17).
**Best practice:** treat "hard to test" as feedback about coupling. **Gotcha:** mocking static methods (`Mockito.mockStatic`) works but hides the missing seam; prefer adding the seam.

### Contract tests for interface implementations

**Description.** A contract test is one suite of tests written against an interface and run against every implementation. In JUnit 5: an abstract class (or interface) with the `@Test` methods and an abstract factory method, plus one subclass per implementation, often `@Nested`. Running the same contract against your **fakes** makes them **verified fakes**.

**Advantages / features.** Liskov substitution checked automatically; new implementations get a full suite for free; fakes can't silently drift from the real thing.

**ContractTestDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Contract tests: ONE set of tests describing what every implementation of an interface must do,
 * run against EACH implementation. Here: a price cache with a plain map, a bounded LRU, and a buggy "optimised" version.
 */
public class ContractTestDemo {

    interface PriceCache {
        void put(String sku, long paise);
        Optional<Long> get(String sku);
        void evict(String sku);
    }

    static final class MapCache implements PriceCache {
        private final Map<String, Long> m = new LinkedHashMap<>();
        public void put(String s, long p) { m.put(s, p); }
        public Optional<Long> get(String s) { return Optional.ofNullable(m.get(s)); }
        public void evict(String s) { m.remove(s); }
    }

    static final class LruCache implements PriceCache {
        private final Map<String, Long> m = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 100; }
        };
        public void put(String s, long p) { m.put(s, p); }
        public Optional<Long> get(String s) { return Optional.ofNullable(m.get(s)); }
        public void evict(String s) { m.remove(s); }
    }

    /** "Optimised": ignores a put when the key exists, to save writes. Breaks price updates. */
    static final class WriteOnceCache implements PriceCache {
        private final Map<String, Long> m = new LinkedHashMap<>();
        public void put(String s, long p) { m.putIfAbsent(s, p); }
        public Optional<Long> get(String s) { return Optional.ofNullable(m.get(s)); }
        public void evict(String s) { m.remove(s); }
    }

    /** The contract, written once against the interface. */
    record Contract(Supplier<PriceCache> factory) {
        List<String> run() {
            List<String> failures = new ArrayList<>();
            check(failures, "missing key is empty", () -> factory.get().get("NOPE").isEmpty());
            check(failures, "put then get returns the price", () -> { var c = factory.get(); c.put("MILK", 6_400); return c.get("MILK").equals(Optional.of(6_400L)); });
            check(failures, "a second put replaces the price", () -> { var c = factory.get(); c.put("MILK", 6_400); c.put("MILK", 6_800); return c.get("MILK").equals(Optional.of(6_800L)); });
            check(failures, "evict removes the entry", () -> { var c = factory.get(); c.put("MILK", 6_400); c.evict("MILK"); return c.get("MILK").isEmpty(); });
            return failures;
        }
        static void check(List<String> failures, String name, Supplier<Boolean> test) { if (!test.get()) failures.add(name); }
    }

    public static void main(String[] args) {
        Map<String, Supplier<PriceCache>> impls = new LinkedHashMap<>();
        impls.put("MapCache", MapCache::new);
        impls.put("LruCache", LruCache::new);
        impls.put("WriteOnceCache", WriteOnceCache::new);
        impls.forEach((name, factory) -> {
            List<String> failures = new Contract(factory).run();
            System.out.println(name + ": " + (failures.isEmpty() ? "meets the contract" : "BREAKS " + failures));
        });
    }
}
```

1. The contract, written once against the interface.
2. A rule an "optimisation" could easily break.
3. Each implementation runs the same suite.

**Run it:** `java ContractTestDemo.java`

```text
MapCache: meets the contract
LruCache: meets the contract
WriteOnceCache: BREAKS [a second put replaces the price]
```

| Where | Contract runs against | When |
|-------|----------------------|------|
| Library interfaces | Every implementation in the repo | Every build |
| Fakes of external services | Fake (every build) and real sandbox (nightly) | Both |
| Consumer-driven contracts (Pact) | Provider service, using consumers' expectations | Provider's CI |

**Use** contract tests for any interface with more than one implementation, and for every fake. **Avoid** copying tests per implementation; they diverge.
**Real-world Java:** the JDK's TCK and Guava's `guava-testlib` (`ListTestSuiteBuilder`, `MapTestSuiteBuilder`) test collection implementations against a contract; Pact and Spring Cloud Contract for services.
**Best practice:** run the contract against the real system on a schedule, not only on every build, if it's slow or rate-limited. **Gotcha:** a contract that's too loose ("returns something") passes for broken implementations; include the edge cases that matter.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** DairyDay delivers tomorrow if you order before 10 pm IST, otherwise the day after. The rule must be tested exactly at the boundary and over New Year.

**SlotCutoffDemo.java**

```java
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Minimal: DairyDay's "order by 10 pm for tomorrow's delivery" rule, tested at exact moments with a fixed Clock (a stub). */
public class SlotCutoffDemo {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    record DeliveryPlanner(Clock clock) {
        LocalDate deliveryDate() {
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(IST));
            return now.toLocalTime().isBefore(LocalTime.of(22, 0)) ? now.toLocalDate().plusDays(1) : now.toLocalDate().plusDays(2);
        }
    }

    static Clock istAt(String localDateTime) {
        return Clock.fixed(ZonedDateTime.parse(localDateTime + "+05:30[Asia/Kolkata]").toInstant(), IST);
    }

    public static void main(String[] args) {
        for (String t : new String[] {"2026-10-31T21:59:59", "2026-10-31T22:00:00", "2026-12-31T23:30:00"})
            System.out.println("ordered " + t + " -> delivered " + new DeliveryPlanner(istAt(t)).deliveryDate());
        System.out.println("same instant, UTC clock: " + new DeliveryPlanner(Clock.fixed(Instant.parse("2026-10-31T16:45:00Z"), ZoneId.of("UTC"))).deliveryDate());
    }
}
```

1. The clock is a constructor parameter: a seam.
2. A helper that builds a stub clock at an exact local time.

**Run it:** `java SlotCutoffDemo.java`

**Output:**

```text
ordered 2026-10-31T21:59:59 -> delivered 2026-11-01
ordered 2026-10-31T22:00:00 -> delivered 2026-11-02
ordered 2026-12-31T23:30:00 -> delivered 2027-01-02
same instant, UTC clock: 2026-11-02
```

**What to notice:**

- 21:59:59 and 22:00:00 give different days, tested in milliseconds, not by waiting.
- The last line passes a UTC clock for 22:15 IST; the planner converts to IST itself, so it's still "after cutoff".

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** checkout reserves stock, charges, and publishes an event. The team wants each test to be a few lines, so they build a **test kit**: a builder for carts, a fake inventory, a spy payment gateway and a fixed clock.

**CheckoutTestKitDemo.java**

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Realistic: a FreshCart "test kit" that bundles builders, fakes, spies and a fixed clock, so each checkout test
 * is a few readable lines. The tests are run by a tiny runner here; in the repo they'd be JUnit tests.
 */
public class CheckoutTestKitDemo {

    // ----- production code -----
    record Item(String sku, int qty, long unitPaise) { }
    record Cart(String customerId, String tier, List<Item> items) {
        long subtotal() { return items.stream().mapToLong(i -> i.qty() * i.unitPaise()).sum(); }
    }
    interface Inventory { boolean reserve(String sku, int qty); void release(String sku, int qty); }
    interface Payments { String charge(String customerId, long paise); }
    interface Events { void publish(String event); }

    record Checkout(Inventory inventory, Payments payments, Events events, Clock clock) {
        String placeOrder(Cart cart) {
            List<Item> reserved = new ArrayList<>();
            for (Item i : cart.items()) {
                if (!inventory.reserve(i.sku(), i.qty())) {
                    reserved.forEach(r -> inventory.release(r.sku(), r.qty()));                    // don't leak earlier reservations
                    return "OUT_OF_STOCK:" + i.sku();
                }
                reserved.add(i);
            }
            long delivery = cart.tier().equals("gold") || cart.subtotal() >= 49_900 ? 0 : 2_500;
            String ref = payments.charge(cart.customerId(), cart.subtotal() + delivery);
            events.publish("OrderPlaced " + ref + " at " + clock.instant());
            return "PLACED total=" + (cart.subtotal() + delivery);
        }
    }

    // ----- the test kit -----
    static final class Kit {
        final Map<String, Integer> stock = new LinkedHashMap<>();
        final List<String> charges = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final Clock clock = Clock.fixed(Instant.parse("2026-10-31T09:00:00Z"), ZoneOffset.UTC);

        Kit inStock(String sku, int qty) { stock.put(sku, qty); return this; }
        Checkout checkout() {
            Inventory fakeInventory = new Inventory() {                                                  // fake: real logic, in memory
                public boolean reserve(String sku, int qty) {
                    int have = stock.getOrDefault(sku, 0);
                    if (have < qty) return false;
                    stock.put(sku, have - qty); return true;
                }
                public void release(String sku, int qty) { stock.merge(sku, qty, Integer::sum); }
            };
            Payments spyPayments = (c, p) -> { charges.add(c + ":" + p); return "PAY-" + charges.size(); };   // spy
            return new Checkout(fakeInventory, spyPayments, events::add, clock);
        }
        static CartBuilder aCart() { return new CartBuilder(); }
    }
    static final class CartBuilder {
        private String customer = "u1", tier = "silver"; private final List<Item> items = new ArrayList<>();
        CartBuilder gold() { tier = "gold"; return this; }
        CartBuilder with(String sku, int qty, long unitPaise) { items.add(new Item(sku, qty, unitPaise)); return this; }
        Cart build() { return new Cart(customer, tier, items.isEmpty() ? List.of(new Item("MILK", 1, 6_400)) : List.copyOf(items)); }
    }

    // ----- tests -----
    static void smallSilverCartPaysDelivery() {
        Kit kit = new Kit().inStock("MILK", 10);                                                     // arrange
        String result = kit.checkout().placeOrder(Kit.aCart().build());                              // act
        expect(result.equals("PLACED total=8900"), result);                                          // assert
        expect(kit.charges.equals(List.of("u1:8900")), kit.charges.toString());
    }
    static void goldCustomersGetFreeDelivery() {
        Kit kit = new Kit().inStock("MILK", 10);
        String result = kit.checkout().placeOrder(Kit.aCart().gold().build());
        expect(result.equals("PLACED total=6400"), result);
    }
    static void outOfStockChargesNothing() {
        Kit kit = new Kit().inStock("MILK", 10).inStock("PANEER", 0);
        String result = kit.checkout().placeOrder(Kit.aCart().with("MILK", 1, 6_400).with("PANEER", 1, 9_000).build());
        expect(result.equals("OUT_OF_STOCK:PANEER"), result);
        expect(kit.charges.isEmpty() && kit.events.isEmpty(), "charged " + kit.charges);
        expect(kit.stock.get("MILK") == 10, "MILK stock " + kit.stock.get("MILK") + ", reservation leaked");
    }
    static void eventCarriesTheClockTime() {
        Kit kit = new Kit().inStock("MILK", 1);
        kit.checkout().placeOrder(Kit.aCart().build());
        expect(kit.events.equals(List.of("OrderPlaced PAY-1 at 2026-10-31T09:00:00Z")), kit.events.toString());
    }

    static void expect(boolean ok, String actual) { if (!ok) throw new AssertionError("got " + actual); }

    public static void main(String[] args) {
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("small silver cart pays delivery", CheckoutTestKitDemo::smallSilverCartPaysDelivery);
        tests.put("gold customers get free delivery", CheckoutTestKitDemo::goldCustomersGetFreeDelivery);
        tests.put("out of stock charges nothing", CheckoutTestKitDemo::outOfStockChargesNothing);
        tests.put("event carries the clock time", CheckoutTestKitDemo::eventCarriesTheClockTime);
        tests.forEach((name, t) -> {
            try { t.run(); System.out.println("PASS " + name); } catch (AssertionError e) { System.out.println("FAIL " + name + ": " + e.getMessage()); }
        });
    }
}
```

1. The kit bundles all the doubles and builders a checkout test needs.
2. A failure-path test that also checks nothing was charged and no stock was leaked by the earlier reservation.
3. Because the clock is fixed, the event text can be compared exactly.

**Run it:** `java CheckoutTestKitDemo.java`

**Output:**

```text
PASS small silver cart pays delivery
PASS gold customers get free delivery
PASS out of stock charges nothing
PASS event carries the clock time
```

**What to notice:**

- Each test is arrange (one line), act (one line), assert (one or two lines).
- The fake inventory caught a real bug while this example was written: MILK stayed reserved when PANEER was out of stock, until checkout released earlier reservations.

### Example 3: Production-grade

**Scenario:** PayLink is the payment provider. Hundreds of tests use an in-memory fake gateway instead of calling the provider. Requirements: the fake must behave like the real adapter for idempotency, declines, refund limits and timeouts; the same contract must run against the real adapter (the provider's sandbox, nightly in CI); and a quickly-written fake must be caught before it misleads anyone.

**VerifiedFakeDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Production: PayLink's payment gateway. Hundreds of tests use an in-memory FAKE instead of the real HTTP adapter.
 * A fake is only useful if it behaves like the real thing, so ONE contract suite runs against:
 *   - the real adapter (here talking to a simulated HTTP server; in CI, the provider's sandbox, nightly)
 *   - the fake used by every other test
 *   - a naive fake someone wrote quickly, which the contract catches
 * Contract: idempotency keys, declines for a magic card, no refund above the captured amount, error type for timeouts.
 */
public class VerifiedFakeDemo {

    sealed interface Result permits Approved, Declined { }
    record Approved(String paymentId, long paise) implements Result { }
    record Declined(String reason) implements Result { }
    static final class GatewayTimeout extends RuntimeException { GatewayTimeout() { super("gateway timeout"); } }

    interface PaymentGateway {
        Result charge(String idempotencyKey, String card, long paise);
        long refund(String paymentId, long paise);        // returns total refunded so far
    }

    // ---------- the real adapter, over a simulated HTTP API ----------
    static final class SimulatedPayLinkServer {                                                          // stands in for the provider's sandbox
        private final Map<String, String> byKey = new LinkedHashMap<>();
        private final Map<String, long[]> payments = new LinkedHashMap<>();   // id -> [captured, refunded]
        Map<String, String> post(String path, Map<String, String> body) {
            if (body.getOrDefault("card", "").equals("4000000000000119")) throw new GatewayTimeout();
            if (path.equals("/charges")) {
                String key = body.get("idempotency_key");
                if (byKey.containsKey(key)) return Map.of("status", "approved", "id", byKey.get(key), "amount", String.valueOf(payments.get(byKey.get(key))[0]));
                if (body.get("card").equals("4000000000000002")) return Map.of("status", "declined", "reason", "card_declined");
                String id = "pl_" + (payments.size() + 1);
                byKey.put(key, id); payments.put(id, new long[] {Long.parseLong(body.get("amount")), 0});
                return Map.of("status", "approved", "id", id, "amount", body.get("amount"));
            }
            long[] p = payments.get(body.get("payment_id"));
            long amount = Long.parseLong(body.get("amount"));
            if (p == null || p[1] + amount > p[0]) return Map.of("status", "error", "reason", "refund_exceeds_capture");
            p[1] += amount;
            return Map.of("status", "ok", "refunded", String.valueOf(p[1]));
        }
    }
    record PayLinkHttpAdapter(SimulatedPayLinkServer server) implements PaymentGateway {
        public Result charge(String key, String card, long paise) {
            Map<String, String> r = server.post("/charges", Map.of("idempotency_key", key, "card", card, "amount", String.valueOf(paise)));
            return r.get("status").equals("approved") ? new Approved(r.get("id"), Long.parseLong(r.get("amount"))) : new Declined(r.get("reason"));
        }
        public long refund(String id, long paise) {
            Map<String, String> r = server.post("/refunds", Map.of("payment_id", id, "amount", String.valueOf(paise)));
            if (r.get("status").equals("error")) throw new IllegalArgumentException(r.get("reason"));
            return Long.parseLong(r.get("refunded"));
        }
    }

    // ---------- the fake every other test uses ----------
    static final class FakePaymentGateway implements PaymentGateway {
        public static final String DECLINE_CARD = "4000000000000002", TIMEOUT_CARD = "4000000000000119";
        private final Map<String, Approved> byKey = new LinkedHashMap<>();
        private final Map<String, long[]> payments = new LinkedHashMap<>();
        public Result charge(String key, String card, long paise) {
            if (card.equals(TIMEOUT_CARD)) throw new GatewayTimeout();
            if (byKey.containsKey(key)) return byKey.get(key);
            if (card.equals(DECLINE_CARD)) return new Declined("card_declined");
            Approved a = new Approved("fake_" + (payments.size() + 1), paise);
            byKey.put(key, a); payments.put(a.paymentId(), new long[] {paise, 0});
            return a;
        }
        public long refund(String id, long paise) {
            long[] p = payments.get(id);
            if (p == null || p[1] + paise > p[0]) throw new IllegalArgumentException("refund_exceeds_capture");
            return p[1] += paise;
        }
    }

    // ---------- a quick fake that "works" for happy-path tests ----------
    static final class NaiveFake implements PaymentGateway {
        int n;
        public Result charge(String key, String card, long paise) { return new Approved("naive_" + (++n), paise); }
        public long refund(String id, long paise) { return paise; }
    }

    // ---------- the contract, written once ----------
    record Contract(Supplier<PaymentGateway> factory) {
        List<String> run() {
            List<String> failed = new ArrayList<>();
            check(failed, "approves a normal card", () -> factory.get().charge("k1", "4111111111111111", 64_900) instanceof Approved a && a.paise() == 64_900);
            check(failed, "same idempotency key returns the same payment", () -> {
                PaymentGateway g = factory.get();
                Result first = g.charge("k2", "4111111111111111", 64_900), retry = g.charge("k2", "4111111111111111", 64_900);
                return first.equals(retry);
            });
            check(failed, "declines the test decline card", () -> factory.get().charge("k3", "4000000000000002", 100) instanceof Declined d && d.reason().equals("card_declined"));
            check(failed, "refund above capture is rejected", () -> {
                PaymentGateway g = factory.get();
                Approved a = (Approved) g.charge("k4", "4111111111111111", 10_000);
                g.refund(a.paymentId(), 6_000);
                try { g.refund(a.paymentId(), 6_000); return false; } catch (IllegalArgumentException e) { return true; }
            });
            check(failed, "timeout card throws GatewayTimeout", () -> {
                try { factory.get().charge("k5", "4000000000000119", 100); return false; } catch (GatewayTimeout e) { return true; }
            });
            return failed;
        }
        static void check(List<String> failed, String name, Supplier<Boolean> t) {
            try { if (!t.get()) failed.add(name); } catch (RuntimeException e) { failed.add(name + " (threw " + e.getClass().getSimpleName() + ")"); }
        }
    }

    public static void main(String[] args) {
        Map<String, Supplier<PaymentGateway>> impls = new LinkedHashMap<>();
        impls.put("PayLinkHttpAdapter (sandbox)", () -> new PayLinkHttpAdapter(new SimulatedPayLinkServer()));
        impls.put("FakePaymentGateway", FakePaymentGateway::new);
        impls.put("NaiveFake", NaiveFake::new);
        impls.forEach((name, f) -> {
            List<String> failed = new Contract(f).run();
            System.out.println(name + ": " + (failed.isEmpty() ? "5/5 contract checks pass" : (5 - failed.size()) + "/5, fails " + failed));
        });
        System.out.println("=> only fakes that pass the same contract as the real adapter may be used in other tests");
    }
}
```

1. The real adapter maps HTTP responses to domain results; here the HTTP server is simulated.
2. The fake: in memory, but with the provider's real rules (idempotency, magic test cards, refund limits).
3. The contract, written once.
4. Idempotency: a retry with the same key must not charge twice. Fakes most often get this wrong.
5. The rule that makes fakes trustworthy.

**Run it:** `java VerifiedFakeDemo.java`

**Output:**

```text
PayLinkHttpAdapter (sandbox): 5/5 contract checks pass
FakePaymentGateway: 5/5 contract checks pass
NaiveFake: 1/5, fails [same idempotency key returns the same payment, declines the test decline card, refund above capture is rejected, timeout card throws GatewayTimeout]
=> only fakes that pass the same contract as the real adapter may be used in other tests
```

**What to notice:**

- The naive fake would make retry and refund tests pass while production double-charges or over-refunds.
- The fake uses the provider's documented test cards, so the same test data works against the sandbox.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Speed | Doubles make unit tests milliseconds | Integration gaps if nothing tests the real thing |
| Determinism | Fixed clocks and stubs remove flakiness | Fakes can drift from reality without contracts |
| Readability | Builders and AAA make intent clear | Test kits are code to maintain |
| Design feedback | Seams push towards loose coupling | Over-abstraction "just for tests" |
| Refactoring | State-based tests survive refactors | Interaction-heavy mock tests break on every change |

## 8. When to Use / When NOT to Use

**Use them when:**

- collaborators are slow, nondeterministic or have side effects (payments, SMS, clock, network);
- domain objects have many fields (builders);
- an interface has several implementations or a fake (contract tests).

**Don't use them when:**

- the collaborator is a simple value or pure function: use the real one;
- you could run the real dependency cheaply (Testcontainers for PostgreSQL) and the test is about the integration;
- a mock would only restate the implementation line by line.

## 9. Best Practices

1. **Prefer real objects, then fakes, then stubs, then mocks.** *(Opinion, widely shared; "classicist" school.)*
2. **Don't mock what you don't own;** wrap third-party APIs and fake your wrapper. *(Consensus, Freeman and Pryce.)*
3. **Inject `Clock`, randomness and IDs.** *(Consensus.)*
4. **One behaviour per test, named as a sentence, AAA layout.** *(Consensus.)*
5. **Builders with valid defaults** for test data. *(Consensus.)*
6. **Verify every fake with a contract test** shared with the real implementation. *(Consensus.)*
7. **Assert on outcomes before interactions.** *(Opinion, widely shared.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Unverified fake hides a double charge (production failure mode)**  
> **Symptom:** after a network blip, some customers were charged twice; every test was green.
> **Root cause:** the in-memory payment fake ignored idempotency keys and always returned a new payment, so the retry logic was "tested" against behaviour the real provider doesn't have, and a bug that sent a new key on retry was never caught.
> **Fix:** a contract suite run against the fake on every build and against the provider sandbox nightly; idempotency is the first contract test.

> 🚨 **Over-mocking**  
> **Symptom:** a harmless refactor (extracting a method) broke 40 tests.
> **Root cause:** tests verified every internal call with mocks, so they tested the implementation, not the behaviour.
> **Fix:** assert on results and state; reserve `verify` for meaningful outgoing effects (a payment was charged once).

> 🚨 **Flaky time-dependent tests**  
> **Symptom:** tests failed every night between 22:00 and 00:00 IST, and on 31 December.
> **Root cause:** production code called `LocalDate.now()`; tests assumed "tomorrow" was a fixed offset.
> **Fix:** inject `Clock`; test boundary times explicitly.

> 🚨 **Shared mutable fixtures**  
> **Symptom:** tests passed alone but failed when run together, in a different order each time.
> **Root cause:** a `static` test order was modified by one test and read by another.
> **Fix:** build fresh data per test with a builder; make shared fixtures immutable.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Mockito strict stubs (`MockitoExtension`) | Strict | Fails on unused stubs | Keep strict | Lenient hides dead setup |
| JUnit `junit.jupiter.execution.parallel.enabled` | false | Runs tests in parallel | Large suites with independent tests | Exposes shared-state bugs (good, but noisy) |
| JUnit `@TestMethodOrder` | Deterministic but unspecified | Order of tests | Never rely on order | Order-dependent tests |
| Testcontainers reuse | off | Reuses containers between runs | Local development speed | State leaking between runs |
| Contract suite against sandbox | n/a | Verifies fakes against reality | Nightly or on adapter changes | Rate limits, sandbox outages |

## 12. Security Considerations

- Never put real credentials, card numbers or customer data in test fixtures; use the provider's documented test cards and synthetic data.
- Make sure fakes and test-only endpoints can't be enabled in production (separate source sets, `@Profile("test")`).
- Sandbox credentials for contract tests are secrets; store them in the CI secret store, not in the repo.
- Test security behaviour with real filters (Spring Security test support), not by mocking the security layer away.

## 13. Performance & Scalability

Fakes and stubs keep unit tests in milliseconds; aim for a unit suite that runs in under a minute so people run it constantly. Mock frameworks add startup cost per class; hand-written fakes are often faster and clearer. Use Testcontainers for the smaller number of integration tests, and run slow contract tests against sandboxes on a schedule. Parallel test execution needs tests with no shared state, which builders and fresh fakes give you.

## 14. Testing It

The contract as real JUnit 5 tests: an abstract class with the tests, one `@Nested` subclass per implementation, AAA in each test:

**PaymentGatewayContractTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The contract as real JUnit tests: an abstract class with the tests, one nested subclass per implementation. */
class PaymentGatewayContractTest {

    abstract static class Contract {
        static final String OK_CARD = "4111111111111111";
        VerifiedFakeDemo.PaymentGateway gateway;

        abstract VerifiedFakeDemo.PaymentGateway create();

        @BeforeEach
        void setUp() { gateway = create(); }

        @Test
        @DisplayName("approves a normal card for the full amount")
        void approves() {
            // act
            var result = gateway.charge("k1", OK_CARD, 64_900);
            // assert
            var approved = assertInstanceOf(VerifiedFakeDemo.Approved.class, result);
            assertEquals(64_900, approved.paise());
        }

        @Test
        @DisplayName("a retry with the same idempotency key returns the same payment")
        void idempotent() {
            var first = gateway.charge("k2", OK_CARD, 10_000);
            var retry = gateway.charge("k2", OK_CARD, 10_000);
            assertEquals(first, retry);
        }

        @Test
        @DisplayName("declines the magic decline card")
        void declines() {
            var result = gateway.charge("k3", "4000000000000002", 100);
            assertEquals(new VerifiedFakeDemo.Declined("card_declined"), result);
        }

        @Test
        @DisplayName("refunds add up and can't exceed the captured amount")
        void refundLimit() {
            var paid = (VerifiedFakeDemo.Approved) gateway.charge("k4", OK_CARD, 10_000);
            assertEquals(6_000, gateway.refund(paid.paymentId(), 6_000));
            assertEquals(10_000, gateway.refund(paid.paymentId(), 4_000));
            assertThrows(IllegalArgumentException.class, () -> gateway.refund(paid.paymentId(), 1));
        }

        @Test
        @DisplayName("the timeout card raises GatewayTimeout")
        void timeout() {
            assertThrows(VerifiedFakeDemo.GatewayTimeout.class, () -> gateway.charge("k5", "4000000000000119", 100));
        }
    }

    @Nested
    @DisplayName("PayLink HTTP adapter against the sandbox")
    class HttpAdapter extends Contract {
        VerifiedFakeDemo.PaymentGateway create() { return new VerifiedFakeDemo.PayLinkHttpAdapter(new VerifiedFakeDemo.SimulatedPayLinkServer()); }
    }

    @Nested
    @DisplayName("in-memory fake used by other tests")
    class Fake extends Contract {
        VerifiedFakeDemo.PaymentGateway create() { return new VerifiedFakeDemo.FakePaymentGateway(); }
    }
}
```

Adding a new gateway (a second provider) means adding one `@Nested` class with a `create()` method; it immediately gets all five checks.

## 15. Observability & Operations

Track test-suite duration and flaky tests (tests that both pass and fail on the same commit) in CI; quarantine and fix flakes quickly. For contract tests against sandboxes, alert when the real adapter fails the contract: that usually means the provider changed behaviour, which you want to know before production does.

## 16. Version Notes

| Java / tool | Change relevant to testing patterns |
|-------------|-------------------------------------|
| Java 8 | `java.time.Clock` for injectable time; lambdas as one-line stubs |
| Java 16 | Records: value-based test data with `equals` for assertions |
| Java 17 | `RandomGenerator` interface for injectable randomness |
| JUnit 5 (2017) | `@Nested`, `@DisplayName`, `@ParameterizedTest`, extensions |
| Mockito 2–5 | Strict stubs; inline mock maker default in 5 (final classes mockable) |
| Testcontainers | Real databases and brokers in tests instead of fakes |

## 17. Expert Deep Dive

- **London vs Chicago (Detroit) schools.** "Mockist" TDD (Freeman and Pryce, *Growing Object-Oriented Software, Guided by Tests*) designs object roles through mocks; "classicist" TDD prefers real objects and fakes, asserting on state. Most teams mix them: mocks at architectural boundaries, real objects inside.
- **Hexagonal architecture makes doubles natural.** Ports (interfaces owned by the domain) are exactly where fakes plug in; adapters are tested with contract and integration tests.
- **Verified fakes at scale.** Google's testing guidance recommends that owners of a service also own its fake and its contract tests, so consumers don't each write their own drifting fakes.
- **Property-based testing** (jqwik) generates many inputs for contracts: "for any sequence of charges and refunds, refunded never exceeds captured".
- **Mutation testing** (PIT) changes production code and checks tests fail; it finds tests that execute code without really asserting anything.

> 📖 **War story: the fake that never timed out**  
> TextBlast's SMS service used a fake provider in all tests. The fake answered instantly and never failed. The real provider occasionally took 30 seconds, and the production client had no timeout, so during a provider incident every request thread blocked and order confirmation stopped across the platform. Tests had never exercised a slow or failing provider. **Fixes:** the fake gained configurable latency and failure modes (`slowFor(Duration)`, `failNext(n)`); a contract test ran against the provider sandbox nightly; the client got timeouts and a circuit breaker; and a test checked that checkout completes when SMS is down. **Lesson:** a fake must model the real system's failure modes, not just its happy path.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Name the five test doubles.** **Dummy, stub, fake, spy, mock.**
2. **Stub vs mock?** A stub **returns canned answers**; a mock **verifies expected calls**.
3. **What's Arrange-Act-Assert?** **Set up, act once, check the outcome.**
4. **How do you test code that uses the current time?** **Inject `Clock`**, use `Clock.fixed`.
5. **What's a Test Data Builder?** Defaults plus **fluent overrides** for test objects.

**▸ 🟡 L3**

1. **What's a fake, and why is it risky?** A **working in-memory implementation**; it can **drift from the real thing**.
2. **What is a seam?** A place to **change behaviour without editing there** (injection, strategy, decorator).
3. **Why "don't mock what you don't own"?** Your mock encodes **your assumptions** about their API; wrap and fake **your interface**.
4. **How do you write contract tests in JUnit 5?** **Abstract test class + subclass per implementation**, often `@Nested`.
5. **State vs interaction testing?** State survives **refactoring**; interactions check **outgoing effects**.

**▸ 🔴 L4–L5**

1. **Design the test strategy for a payment integration.** **Port interface, verified fake with contract tests, sandbox nightly, idempotency and failure modes, Testcontainers for DB.**
2. **How do you keep a large test suite fast and reliable?** **Pyramid**, fakes over mocks, no shared state, parallel execution, flake tracking.
3. **London vs Chicago TDD?** **Mocks to design roles** vs **real objects and state**; mix at boundaries.
4. **How do you know your tests are good?** **Mutation testing**, coverage of failure paths, contracts on fakes.
5. **Consumer-driven contracts vs provider contract tests?** **Pact**: consumers publish expectations the provider verifies; provider suites verify implementations of one interface.

**▸ 💻 Hands-on**

**Task:** Add a "slow" mode to `FakePaymentGateway` and a contract test that a timeout is reported as `GatewayTimeout`, not as a decline.

> ✅ **Solution**  
> ```java
> // in FakePaymentGateway
> private boolean timeOutNext;
> public FakePaymentGateway timeOutNextCall() { timeOutNext = true; return this; }
>
> public Result charge(String key, String card, long paise) {
>     if (timeOutNext || card.equals(TIMEOUT_CARD)) { timeOutNext = false; throw new GatewayTimeout(); }
>     ...
> }
>
> // contract (works for the sandbox too, via the magic timeout card)
> @Test
> void timeoutIsNotADecline() {
>     assertThrows(VerifiedFakeDemo.GatewayTimeout.class,
>             () -> gateway.charge("k6", "4000000000000119", 100));
> }
> ```

## 19. Practice

- [ ] Rewrite `TestDoublesDemo` with Mockito and compare which hand-written double each Mockito call replaces.
    > 💡 **Hint**  
    > `when(...).thenReturn(false)` is the stub; `verify(payments).charge("FC-2", 64900)` is the mock check.

- [ ] Add `withCustomer(String)` to the cart builder in `CheckoutTestKitDemo` and a test that charges go to the right customer.
    > 💡 **Hint**  
    > Assert on `kit.charges`.

- [ ] Make `ContractTestDemo` a real JUnit 5 contract with `@Nested` classes.
    > 💡 **Hint**  
    > Copy the structure of `PaymentGatewayContractTest`.

- [ ] Find a test in your codebase that uses `LocalDateTime.now()` indirectly and add a `Clock` seam.
    > 💡 **Hint**  
    > Search production code for `now()` without a `Clock` argument.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Doubles: dummy (unused), stub (canned), fake (works in memory), spy (records), mock (verifies).
> - Data: Object Mother for named cases, Builder for "default except...".
> - Shape: Arrange-Act-Assert, one behaviour, sentence names.
> - Seams: inject `Clock`, randomness, and your own interfaces for external systems.
> - Contracts: one suite per interface, run against every implementation, including fakes.
>
> ```java
> abstract static class Contract {
>     abstract PaymentGateway create();
>     @Test void idempotent() { var g = create(); assertEquals(g.charge("k", CARD, 100), g.charge("k", CARD, 100)); }
> }
> @Nested class Fake extends Contract { PaymentGateway create() { return new FakePaymentGateway(); } }
> ```

## 21. Further Reading

- Gerard Meszaros, *xUnit Test Patterns: Refactoring Test Code* (2007).
- Michael Feathers, *Working Effectively with Legacy Code* (2004), chapter 4, "The Seam Model".
- Steve Freeman and Nat Pryce, *Growing Object-Oriented Software, Guided by Tests* (2009).
- Martin Fowler, ["Mocks Aren't Stubs"](https://martinfowler.com/articles/mocksArentStubs.html).
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) (nested tests, test interfaces).

---

[← Previous: Specification](specification.md) · [Next: L3 Professional →](../l3-professional/index.md)
