# Null Object

L2 🔵 Practitioner · ⏱ 30 min read · Prerequisites: [Strategy](../l1-foundations/strategy.md), [Polymorphism](../l1-foundations/oop-refresher.md)

> 📌 **TL;DR**  
> A Null Object is an implementation of an interface that does nothing, used instead of `null` when "nothing" has one obvious meaning: no discount, no notification, no metrics. Callers stop writing `if (x != null)`, and `NullPointerException`s from forgotten checks disappear. Its bigger sibling, the Special Case, gives specific, sensible answers for situations like "guest customer" or "invalid coupon". Use `Optional` instead when the caller must decide what absence means, and never use a Null Object for something that must happen, such as a payment.

## 1. What It Is

**Plain English.** FreshCart's checkout asks a `Discount` to adjust the total. Most carts have no discount. Instead of passing `null` and checking for it everywhere, pass `NoDiscount`, a discount that returns the total unchanged. The checkout code treats every cart the same way.

**Definition (Woolf, *Pattern Languages of Program Design 3*, 1997).** "A Null Object provides a surrogate for another object that shares the same interface but does nothing." Parts:

- **Abstract object:** the interface clients use (`Discount`, `Notifier`).
- **Real object:** an implementation with behaviour (`PercentOff`, `SmsNotifier`).
- **Null object:** an implementation whose methods do nothing or return neutral values (`0`, empty list, the input unchanged). Usually a singleton.
- **Client:** calls the interface without checking for `null`.

## 2. Why It Exists

Tony Hoare called `null` his "billion-dollar mistake": every reference may be missing, and the compiler doesn't make you check. Code fills with defensive `if (x != null)`, and the one place someone forgets crashes in production. When absence has a single, harmless meaning, encoding that meaning in an object removes both the checks and the crashes, and keeps the decision ("what does no discount mean?") in one place.

## 3. Mental Model / Analogy

**Analogy.** A shop's "no discount" option on the till is a real button that applies a 0% discount. The cashier presses one button for every customer instead of first asking "is there a discount at all?".

**Technical model.** Polymorphism replaces a conditional: the `if (discount == null)` branch moves into a class whose methods are the "else" behaviour. The decision is made once, when the object is chosen (often at wiring time), not at every call.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Null Object structure

**Description.** Implement the interface with methods that do nothing or return a neutral value: the identity for the operation (subtotal unchanged, `0` points, empty list), and a harmless description (`"none"`). Because it has no state, make it a singleton; an `enum` with one constant is the simplest thread-safe way.

**Advantages / features.** No `null` checks in callers; no NPEs from forgotten checks; the meaning of "nothing" is defined and tested in one place.

**Before (null and checks) and after (Null Object):**

**NullObjectStructureDemo.java**

```java
import java.util.List;

/** Null Object: instead of passing null for "no discount", pass an object that implements the interface and does nothing. */
public class NullObjectStructureDemo {

    record Line(String sku, long paise) { }

    // ---------- before: every caller must remember the null check ----------
    interface Discount { long apply(long subtotal); String label(); }
    record PercentOff(int percent) implements Discount {
        public long apply(long s) { return s - s * percent / 100; }
        public String label() { return percent + "% off"; }
    }

    static long totalBefore(List<Line> lines, Discount discount) {
        long subtotal = lines.stream().mapToLong(Line::paise).sum();
        return discount == null ? subtotal : discount.apply(subtotal);
    }

    static String receiptBefore(Discount discount) {
        return "discount: " + discount.label();                                                       // forgot the check here
    }

    // ---------- after: a do-nothing implementation ----------
    enum NoDiscount implements Discount {
        INSTANCE;
        public long apply(long subtotal) { return subtotal; }
        public String label() { return "none"; }
    }

    static long total(List<Line> lines, Discount discount) {
        return discount.apply(lines.stream().mapToLong(Line::paise).sum());
    }

    public static void main(String[] args) {
        List<Line> cart = List.of(new Line("MILK-1L", 6_400), new Line("BREAD", 4_500));
        System.out.println("before, 10% off: " + totalBefore(cart, new PercentOff(10)));
        System.out.println("before, no discount: " + totalBefore(cart, null));
        try { receiptBefore(null); } catch (NullPointerException e) { System.out.println("before, receipt: NullPointerException"); }

        System.out.println("after, 10% off: " + total(cart, new PercentOff(10)) + ", label " + new PercentOff(10).label());
        System.out.println("after, no discount: " + total(cart, NoDiscount.INSTANCE) + ", label " + NoDiscount.INSTANCE.label());
    }
}
```

1. Before: every method that touches the discount needs this check, and `receiptBefore` forgot it.
2. The Null Object: same interface, neutral behaviour, a single shared instance.
3. After: no branches; "no discount" flows through the same code as any other discount.

**Run it:** `java NullObjectStructureDemo.java`

```text
before, 10% off: 9810
before, no discount: 10900
before, receipt: NullPointerException
after, 10% off: 9810, label 10% off
after, no discount: 10900, label none
```

| Method kind | Neutral value | Example |
|-------------|---------------|---------|
| Transform a value | Return the input | `apply(subtotal) -> subtotal` |
| Compute an amount | `0` | `loyaltyPoints() -> 0` |
| Return a collection | Empty, immutable | `List.of()` |
| Side effect | Do nothing | `send(...) {}` |
| Description | A clear word | `"none"` |
| Boolean query | The safe answer | `canUseCashOnDelivery() -> false` |

**Use** a Null Object when an optional collaborator's absence means "skip it". **Avoid** it when callers need to know the value was missing.
**Real-world Java:** `Collections.emptyList()`, `OutputStream.nullOutputStream()` and `Writer.nullWriter()` (Java 11), SLF4J's `NOPLogger`, Micrometer's `NoopCounter`, OpenTelemetry's no-op `Tracer`.
**Best practice:** return empty collections, never `null` collections (Effective Java Item 54). **Gotcha:** a Null Object's "neutral" value must be neutral for **every** caller; `0` is neutral for a sum but not for a minimum.

### Null Object vs. Optional

**Description.** `Optional<T>` (Java 8) is a return type that says "there may be no value; you decide what to do". A Null Object says "absence has one behaviour, already decided". Lookups (`findByPhone`) return `Optional`, because different callers react differently (greet as new, ask to sign up, return 404). Collaborators (`Notifier`) use Null Objects, because "no notifier" always means "don't notify".

**Advantages / features.** Each tool fits a different question; using the right one makes intent visible in the type.

**NullObjectVsOptionalDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Optional: "there may be no value, and YOU decide what that means" (return types of lookups).
 * Null Object: "absence has one obvious behaviour: do nothing" (collaborators such as notifiers or metrics).
 */
public class NullObjectVsOptionalDemo {

    record Customer(String phone, String name) { }

    static final Map<String, Customer> CUSTOMERS = new LinkedHashMap<>();
    static { CUSTOMERS.put("98450", new Customer("98450", "Asha")); }

    static Optional<Customer> findByPhone(String phone) {
        return Optional.ofNullable(CUSTOMERS.get(phone));
    }

    interface Notifier { void send(String phone, String text); }
    record SmsNotifier() implements Notifier { public void send(String p, String t) { System.out.println("  SMS to " + p + ": " + t); } }
    enum NoNotifier implements Notifier { INSTANCE; public void send(String p, String t) { } }

    static void placeOrder(String phone, Notifier notifier) {
        String greeting = findByPhone(phone).map(c -> "Hi " + c.name()).orElse("Welcome, new customer");
        System.out.println(phone + ": " + greeting);
        notifier.send(phone, "order confirmed");
    }

    public static void main(String[] args) {
        placeOrder("98450", new SmsNotifier());
        placeOrder("70000", NoNotifier.INSTANCE);
        System.out.println("existing customer at checkout? " + findByPhone("70000").isPresent() + " -> ask them to sign up");
    }
}
```

1. A lookup may find nothing, and the meaning depends on the caller, so it returns `Optional`.
2. "No notifier" has one meaning everywhere: do nothing. That's a Null Object.
3. Here the caller decides what a missing customer means at this point in the flow.

**Run it:** `java NullObjectVsOptionalDemo.java`

```text
98450: Hi Asha
  SMS to 98450: order confirmed
70000: Welcome, new customer
existing customer at checkout? false -> ask them to sign up
```

| Question | Use | Example |
|----------|-----|---------|
| Lookup may find nothing; callers react differently | `Optional<T>` return | `findCustomer(phone)` |
| Optional collaborator; absent means skip | Null Object | `NoNotifier`, `NoMetrics` |
| Absent or odd case with specific behaviour | Special Case | `Guest`, `InvalidCoupon` |
| Must exist | Fail fast (`requireNonNull`) | `PaymentGateway` |
| Field or parameter | Neither `Optional` nor `null`: overloads, defaults or a Null Object | `Order(Discount d)` |

**Use** `Optional` for return values only. **Avoid** `Optional` fields, parameters and collections of `Optional`; the JDK designers intended it as a return type.
**Real-world Java:** `Stream.findFirst()`, `Map.entry` lookups via `Optional.ofNullable(map.get(k))`, Spring Data `findById` returning `Optional`.
**Best practice:** `orElseThrow()` when absence is a bug, `map(...).orElse(...)` when there is a default. **Gotcha:** `Optional.get()` without checking just moves the NPE to a `NoSuchElementException`.

### Special Case pattern

**Description.** Martin Fowler's *Special Case* (*Patterns of Enterprise Application Architecture*, 2002) generalises Null Object: a subclass for a particular situation that gives specific, sensible answers. A missing customer becomes `Guest` (greeting "Sign in to earn points", no cash on delivery); a blocked one becomes `Blocked`. Null Object is the special case for absence.

**Advantages / features.** Business rules for unusual situations live in one class; callers stay simple; with a sealed interface, code that does need to tell cases apart can still `switch` on them.

**SpecialCaseDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Special Case (Fowler): a subclass for a particular situation that gives sensible, SPECIFIC answers,
 * not just "nothing". A Null Object is the special case for absence; here we also model a missing and a blocked customer.
 */
public class SpecialCaseDemo {

    sealed interface Customer permits Member, Guest, Blocked {
        String greeting();
        long loyaltyPoints();
        boolean canUseCashOnDelivery();
    }
    record Member(String name, long loyaltyPoints) implements Customer {
        public String greeting() { return "Welcome back, " + name; }
        public boolean canUseCashOnDelivery() { return true; }
    }
    enum Guest implements Customer {
        INSTANCE;
        public String greeting() { return "Hello! Sign in to earn points"; }
        public long loyaltyPoints() { return 0; }
        public boolean canUseCashOnDelivery() { return false; }
    }
    record Blocked(String name, String reason) implements Customer {
        public String greeting() { return "Hi " + name + ", please contact support"; }
        public long loyaltyPoints() { return 0; }
        public boolean canUseCashOnDelivery() { return false; }
    }

    static final Map<String, Customer> DB = new LinkedHashMap<>();
    static {
        DB.put("u1", new Member("Asha", 420));
        DB.put("u2", new Blocked("Ravi", "chargeback"));
    }

    static Customer lookup(String userId) {
        if (userId == null) return Guest.INSTANCE;
        return DB.getOrDefault(userId, Guest.INSTANCE);
    }

    public static void main(String[] args) {
        for (String id : java.util.Arrays.asList("u1", "u2", null)) {
            Customer c = lookup(id);
            System.out.printf("%-5s %-38s points=%-4d cod=%s%n", id, c.greeting(), c.loyaltyPoints(), c.canUseCashOnDelivery());
        }
        List<Customer> all = List.of(lookup("u1"), lookup("u2"), lookup(null));
        System.out.println("blocked customers: " + all.stream().filter(c -> c instanceof Blocked).count());
    }
}
```

1. `Guest` is more than "nothing": it has its own greeting and rules.
2. A second special case for a situation that isn't absence at all.
3. The lookup turns `null` and "not found" into a special case at the boundary, so nothing downstream sees `null`.

**Run it:** `java SpecialCaseDemo.java`

```text
u1    Welcome back, Asha                     points=420  cod=true
u2    Hi Ravi, please contact support        points=0    cod=false
null  Hello! Sign in to earn points          points=0    cod=false
blocked customers: 1
```

| | Null Object | Special Case |
|-|-------------|--------------|
| Represents | Absence | Any unusual situation (absent, unknown, blocked, unavailable) |
| Behaviour | Neutral / nothing | Specific to the situation |
| State | None (singleton) | May carry data (`code`, `reason`) |
| Typical number | One per interface | Several per interface |

**Use** Special Case when an unusual situation has its own rules. **Avoid** stuffing many special cases into one class with flags; that brings the conditionals back.
**Real-world Java:** `Collections.emptyList()` (a special case of `List`), `Duration.ZERO`, `BigDecimal.ZERO`, `Locale.ROOT`.
**Best practice:** convert `null` to a special case at the boundary (repository, HTTP client) and never let `null` past it. **Gotcha:** special cases must not hide errors; "unknown customer" is fine, "database down" usually is not.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** TextBlast sends an order SMS to customers who opted in. Checkout shouldn't care who opted in.

**NoOpNotifierDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Minimal: TextBlast order SMS. Customers who opted out get a notifier that does nothing, so checkout code has no if-statements. */
public class NoOpNotifierDemo {

    interface Notifier { void orderPlaced(String orderId); }

    record SmsNotifier(String phone, List<String> outbox) implements Notifier {
        public void orderPlaced(String orderId) { outbox.add("SMS " + phone + ": order " + orderId + " placed"); }
    }

    enum NoOpNotifier implements Notifier {
        INSTANCE;
        public void orderPlaced(String orderId) { }
    }

    static Notifier notifierFor(String phone, boolean optedIn, List<String> outbox) {
        return optedIn ? new SmsNotifier(phone, outbox) : NoOpNotifier.INSTANCE;
    }

    public static void main(String[] args) {
        List<String> outbox = new ArrayList<>();
        notifierFor("98450", true, outbox).orderPlaced("FC-1001");
        notifierFor("70000", false, outbox).orderPlaced("FC-1002");
        System.out.println("messages sent: " + outbox.size());
        outbox.forEach(System.out::println);
    }
}
```

1. The Null Object: accepts the call, does nothing.
2. The choice is made once, when the notifier is created.

**Run it:** `java NoOpNotifierDemo.java`

**Output:**

```text
messages sent: 1
SMS 98450: order FC-1001 placed
```

**What to notice:**

- Both orders called `orderPlaced`; only one SMS went out, and no code checked the preference.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** a promo code box at checkout. Customers leave it empty, type a valid code, mistype one, or use an expired one. Each case must produce the right total and the right message, and the pricing code must stay simple.

**CouponCheckoutDemo.java**

```java
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Realistic: FreshCart checkout with an optional promo code. Three "absent or bad" situations each get a special case:
 * no code typed (NoCoupon), unknown code (InvalidCoupon), expired code (ExpiredCoupon). Checkout code has no null checks.
 */
public class CouponCheckoutDemo {

    sealed interface Coupon permits PercentCoupon, NoCoupon, InvalidCoupon, ExpiredCoupon {
        long discount(long subtotalPaise);
        String message();
    }
    record PercentCoupon(String code, int percent, long capPaise) implements Coupon {
        public long discount(long s) { return Math.min(s * percent / 100, capPaise); }
        public String message() { return code + " applied: " + percent + "% off (max Rs " + capPaise / 100 + ")"; }
    }
    enum NoCoupon implements Coupon {
        INSTANCE;
        public long discount(long s) { return 0; }
        public String message() { return ""; }
    }
    record InvalidCoupon(String code) implements Coupon {
        public long discount(long s) { return 0; }
        public String message() { return "code " + code + " isn't valid"; }
    }
    record ExpiredCoupon(String code, LocalDate expiredOn) implements Coupon {
        public long discount(long s) { return 0; }
        public String message() { return "code " + code + " expired on " + expiredOn; }
    }

    record StoredCoupon(int percent, long capPaise, LocalDate validUntil) { }
    static final Map<String, StoredCoupon> COUPONS = new LinkedHashMap<>();
    static {
        COUPONS.put("FRESH10", new StoredCoupon(10, 10_000, LocalDate.of(2026, 12, 31)));
        COUPONS.put("DIWALI25", new StoredCoupon(25, 15_000, LocalDate.of(2026, 10, 25)));
    }

    static Coupon resolve(String typed, LocalDate today) {
        if (typed == null || typed.isBlank()) return NoCoupon.INSTANCE;
        String code = typed.trim().toUpperCase();
        StoredCoupon c = COUPONS.get(code);
        if (c == null) return new InvalidCoupon(code);
        if (today.isAfter(c.validUntil())) return new ExpiredCoupon(code, c.validUntil());
        return new PercentCoupon(code, c.percent(), c.capPaise());
    }

    static String checkout(long subtotalPaise, String typedCode, LocalDate today) {
        Coupon coupon = resolve(typedCode, today);
        long total = subtotalPaise - coupon.discount(subtotalPaise);
        String msg = coupon.message().isEmpty() ? "" : "  [" + coupon.message() + "]";
        return String.format("Rs %.2f -> Rs %.2f%s", subtotalPaise / 100.0, total / 100.0, msg);
    }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 31);
        System.out.println("no code:   " + checkout(64_900, null, today));
        System.out.println("FRESH10:   " + checkout(64_900, " fresh10 ", today));
        System.out.println("big cart:  " + checkout(250_000, "FRESH10", today));
        System.out.println("typo:      " + checkout(64_900, "FRSH10", today));
        System.out.println("expired:   " + checkout(64_900, "DIWALI25", today));
    }
}
```

1. The Null Object for "no code typed": no discount, no message.
2. Special cases for bad codes: no discount, but a helpful message with the code.
3. All the branching happens once, when the typed text is turned into a `Coupon`.

**Run it:** `java CouponCheckoutDemo.java`

**Output:**

```text
no code:   Rs 649.00 -> Rs 649.00
FRESH10:   Rs 649.00 -> Rs 584.10  [FRESH10 applied: 10% off (max Rs 100)]
big cart:  Rs 2500.00 -> Rs 2400.00  [FRESH10 applied: 10% off (max Rs 100)]
typo:      Rs 649.00 -> Rs 649.00  [code FRSH10 isn't valid]
expired:   Rs 649.00 -> Rs 649.00  [code DIWALI25 expired on 2026-10-25]
```

**What to notice:**

- `checkout` has no `if` about coupons; it only formats the message if there is one.
- The cap applied on the big cart (Rs 100, not Rs 250).
- `" fresh10 "` was normalised at the boundary.

### Example 3: Production-grade

**Scenario:** FreshCart's checkout has a required payment gateway and optional SMS, metrics and loyalty services that can be switched off per environment or during incidents. Requirements: no `null` checks in checkout; never start without payments; disabled features must be visible at startup and in counters; when loyalty is off, the receipt must say points are pending rather than silently showing 0.

**CheckoutWiringDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production: FreshCart checkout has REQUIRED collaborators (payment) and OPTIONAL ones (SMS, metrics, loyalty).
 * - optional features that are switched off get Null Objects, chosen once at startup from config
 * - required collaborators never get a Null Object: wiring fails fast
 * - disabled features are reported at startup, and suppressed calls are counted, so "doing nothing" stays visible
 * - a Special Case for a DOWN loyalty service returns a safe answer AND says it is degraded
 */
public class CheckoutWiringDemo {

    interface PaymentGateway { String charge(String orderId, long paise); }
    interface Notifier { void send(String orderId, String text); }
    interface Metrics { void increment(String name); }
    interface Loyalty { long pointsFor(String customerId, long paise); boolean degraded(); }

    /** A Null Object that counts what it swallowed, so silence is measurable. */
    static final class DisabledNotifier implements Notifier {
        final AtomicLong suppressed = new AtomicLong();
        public void send(String orderId, String text) { suppressed.incrementAndGet(); }
    }
    enum NoMetrics implements Metrics { INSTANCE; public void increment(String name) { } }

    /** Special case: the loyalty service is unreachable. Award nothing now, but say so. */
    static final class LoyaltyUnavailable implements Loyalty {
        final List<String> owed = new ArrayList<>();
        public long pointsFor(String customerId, long paise) { owed.add(customerId + ":" + paise); return 0; }
        public boolean degraded() { return true; }
    }

    record Collaborators(PaymentGateway payments, Notifier notifier, Metrics metrics, Loyalty loyalty, List<String> disabled) { }

    static Collaborators wire(Map<String, String> config, Map<String, Object> realBeans) {
        PaymentGateway payments = (PaymentGateway) realBeans.get("payments");
        Objects.requireNonNull(payments, "payments is required; refusing to start with a no-op payment gateway");
        List<String> disabled = new ArrayList<>();
        Notifier n = on(config, "sms") ? (Notifier) realBeans.get("sms") : off(disabled, "sms", new DisabledNotifier());
        Metrics m = on(config, "metrics") ? (Metrics) realBeans.get("metrics") : off(disabled, "metrics", NoMetrics.INSTANCE);
        Loyalty l = on(config, "loyalty") ? (Loyalty) realBeans.get("loyalty") : off(disabled, "loyalty", new LoyaltyUnavailable());
        return new Collaborators(payments, n, m, l, List.copyOf(disabled));
    }
    static boolean on(Map<String, String> c, String k) { return Boolean.parseBoolean(c.getOrDefault(k + ".enabled", "false")); }
    static <T> T off(List<String> disabled, String name, T nullObject) { disabled.add(name); return nullObject; }

    record Receipt(String orderId, String paymentRef, long points, boolean loyaltyDegraded) { }

    static Receipt checkout(Collaborators c, String orderId, String customerId, long paise) {
        String ref = c.payments().charge(orderId, paise);
        c.metrics().increment("checkout.success");
        c.notifier().send(orderId, "Order " + orderId + " confirmed");
        long points = c.loyalty().pointsFor(customerId, paise);
        return new Receipt(orderId, ref, points, c.loyalty().degraded());
    }

    public static void main(String[] args) {
        Map<String, Object> beans = new LinkedHashMap<>();
        beans.put("payments", (PaymentGateway) (id, p) -> "PAY-" + id);
        beans.put("sms", (Notifier) (id, t) -> System.out.println("  sms: " + t));

        Map<String, String> config = new LinkedHashMap<>();
        config.put("sms.enabled", "true");
        config.put("metrics.enabled", "false");
        config.put("loyalty.enabled", "false");

        Collaborators c = wire(config, beans);
        System.out.println("startup: disabled features " + c.disabled());
        System.out.println(checkout(c, "FC-2001", "u1", 64_900));

        config.put("sms.enabled", "false");
        Collaborators quiet = wire(config, beans);
        checkout(quiet, "FC-2002", "u2", 30_000);
        checkout(quiet, "FC-2003", "u3", 12_000);
        System.out.println("sms suppressed: " + ((DisabledNotifier) quiet.notifier()).suppressed.get());
        System.out.println("loyalty points owed later: " + ((LoyaltyUnavailable) quiet.loyalty()).owed);

        try { wire(config, Map.of()); }
        catch (NullPointerException e) { System.out.println("startup failed: " + e.getMessage()); }
    }
}
```

1. A Null Object that counts what it swallowed. "Doing nothing" is now measurable and can be exported as a metric.
2. A Special Case for an unavailable service: it gives a safe answer (0 points), reports `degraded()`, and records what's owed so it can be paid later.
3. Wiring picks real or null implementations once. The required collaborator is checked with `requireNonNull` and never replaced.
4. Checkout calls every collaborator unconditionally.
5. The startup log lists disabled features, so a missing SMS isn't a mystery.

**Run it:** `java CheckoutWiringDemo.java`

**Output:**

```text
startup: disabled features [metrics, loyalty]
  sms: Order FC-2001 confirmed
Receipt[orderId=FC-2001, paymentRef=PAY-FC-2001, points=0, loyaltyDegraded=true]
sms suppressed: 2
loyalty points owed later: [u2:30000, u3:12000]
startup failed: payments is required; refusing to start with a no-op payment gateway
```

**What to notice:**

- The receipt says `loyaltyDegraded=true`, so the app can show "points pending".
- Two SMS were suppressed and counted.
- The app refused to start without a payment gateway.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Safety | No NPEs from forgotten checks | Can hide real errors if misused |
| Readability | Callers have no branches | Behaviour of "nothing" is in another file |
| Cohesion | Meaning of absence defined once | One more class per interface |
| Performance | Singleton, no allocation | Calls still happen (negligible) |
| Debugging | Fewer crashes | "Why didn't the SMS go out?" is harder without logs and counters |

## 8. When to Use / When NOT to Use

**Use it when:**

- an optional collaborator's absence means "skip it" (logging, metrics, notifications, caching);
- a default has a clear neutral behaviour (no discount, empty list);
- unusual situations have their own rules (guest, invalid coupon): use Special Case.

**Don't use it when:**

- callers need to know the value was missing: return `Optional`;
- the operation must happen (payment, stock reservation, audit): fail fast;
- the "neutral" answer is wrong for some callers.

## 9. Best Practices

1. **Make Null Objects stateless singletons**, ideally an `enum`. *(Consensus.)*
2. **Return empty collections, never `null`.** *(Consensus, Effective Java Item 54.)*
3. **Convert `null` to a special case at the boundary** and keep it out of the domain. *(Consensus.)*
4. **Never use a Null Object for required behaviour;** use `Objects.requireNonNull` at construction. *(Consensus.)*
5. **Make "nothing" observable:** log disabled features at startup, count suppressed calls. *(Opinion, widely shared.)*
6. **Use `Optional` only as a return type.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **No-op for something that must happen (production failure mode)**  
> **Symptom:** orders were marked paid in a staging-like region, and goods were delivered without payment for six hours.
> **Root cause:** a config typo disabled the payment client, and the wiring code fell back to a `NoOpPaymentGateway` that returned success, "so the app still starts".
> **Fix:** required collaborators are never replaced by Null Objects; the app fails to start. Null Objects are for optional features only.

> 🚨 **Silent Null Object hides an outage**  
> **Symptom:** customers stopped receiving delivery SMS; nobody noticed for two days.
> **Root cause:** after a credentials rotation the SMS client failed to initialise and a no-op notifier was wired in without any log or metric.
> **Fix:** log disabled features at startup, count suppressed calls, and alert when a feature that should be on is off.

> 🚨 **Neutral value that isn't neutral**  
> **Symptom:** the "cheapest delivery slot" computation always returned Rs 0.
> **Root cause:** a Null Object `DeliveryFee` returned `0`, which is neutral for a sum but wins every minimum.
> **Fix:** choose the neutral value per operation, or use `Optional` when there's no safe value.

> 🚨 **Null Object with mutable state**  
> **Symptom:** a shared `EmptyCart` singleton started showing items for random users.
> **Root cause:** its `items()` returned a mutable `ArrayList`, which a caller added to.
> **Fix:** return immutable values (`List.of()`); keep Null Objects stateless.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `<feature>.enabled` flags | Feature-specific | Chooses real vs. Null Object at startup | Per environment, during incidents | Silent loss of a feature if not observable |
| Spring `@ConditionalOnMissingBean` | n/a | Supplies a no-op bean when no real one exists | Libraries offering optional integrations | Hides misconfiguration if the no-op is unintended |
| Micrometer without a registry | `SimpleMeterRegistry` or no-op | Metrics calls become cheap no-ops | Tests, local runs | No metrics in prod if the registry isn't configured |
| SLF4J without a binding | NOP logger (with a warning) | Log calls do nothing | Never intentionally in prod | All logs lost |

## 12. Security Considerations

- Never use a permissive Null Object for security decisions: a `NoOpAuthorizer` that allows everything, or an empty `PermissionSet` treated as "no restrictions". The neutral value for access is **deny**.
- An audit logger must not be optional; if auditing is required, fail when it's missing.
- Special cases for unknown users (`Guest`) must have the most restricted permissions.

## 13. Performance & Scalability

A singleton Null Object costs one virtual call that the JIT usually inlines to nothing, cheaper and more predictable than branching. Avoid building expensive arguments for no-op calls (`logger.debug("..." + bigObject)`); use lazy forms such as SLF4J's `{}` placeholders or `Supplier` parameters. Counting suppressed calls with `AtomicLong` (or `LongAdder` under heavy contention) is cheap.

## 14. Testing It

Test the wiring, the special cases and that required collaborators can't be replaced:

**CheckoutWiringTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckoutWiringTest {

    private final Map<String, Object> beans = Map.of(
            "payments", (CheckoutWiringDemo.PaymentGateway) (id, p) -> "PAY-" + id,
            "loyalty", new CheckoutWiringDemo.Loyalty() {
                public long pointsFor(String c, long p) { return p / 10_000; }
                public boolean degraded() { return false; }
            });

    @Test
    void disabledFeaturesGetNullObjectsAndAreReported() {
        var c = CheckoutWiringDemo.wire(Map.of("loyalty.enabled", "true"), beans);
        assertEquals(List.of("sms", "metrics"), c.disabled());
        assertSame(CheckoutWiringDemo.NoMetrics.INSTANCE, c.metrics());
        var receipt = CheckoutWiringDemo.checkout(c, "FC-1", "u1", 50_000);
        assertEquals(5, receipt.points());
        assertFalse(receipt.loyaltyDegraded());
        assertEquals(1, ((CheckoutWiringDemo.DisabledNotifier) c.notifier()).suppressed.get());
    }

    @Test
    void loyaltySpecialCaseIsSafeButVisible() {
        var c = CheckoutWiringDemo.wire(Map.of(), beans);
        var receipt = CheckoutWiringDemo.checkout(c, "FC-2", "u2", 50_000);
        assertEquals(0, receipt.points());
        assertTrue(receipt.loyaltyDegraded());
        assertEquals(List.of("u2:50000"), ((CheckoutWiringDemo.LoyaltyUnavailable) c.loyalty()).owed);
    }

    @Test
    void requiredCollaboratorNeverGetsANullObject() {
        assertThrows(NullPointerException.class, () -> CheckoutWiringDemo.wire(Map.of(), Map.of()));
    }

    @Test
    void couponSpecialCases() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        assertSame(CouponCheckoutDemo.NoCoupon.INSTANCE, CouponCheckoutDemo.resolve("  ", today));
        assertInstanceOf(CouponCheckoutDemo.InvalidCoupon.class, CouponCheckoutDemo.resolve("NOPE", today));
        assertInstanceOf(CouponCheckoutDemo.ExpiredCoupon.class, CouponCheckoutDemo.resolve("diwali25", today));
        assertEquals(10_000, CouponCheckoutDemo.resolve("FRESH10", today).discount(250_000));
    }

    @Test
    void noOpNotifierSendsNothing() {
        List<String> outbox = new ArrayList<>();
        NoOpNotifierDemo.notifierFor("1", false, outbox).orderPlaced("X");
        assertTrue(outbox.isEmpty());
    }
}
```

Null Objects are also useful **in** tests: `NoMetrics.INSTANCE` and `NoOpNotifier.INSTANCE` are simpler than mocks when the test doesn't care about those calls.

## 15. Observability & Operations

Log the list of disabled features at startup and expose it on a health or info endpoint. Count suppressed calls per Null Object and alert when a feature that should be on reports suppressions. For Special Cases like `LoyaltyUnavailable`, expose a "degraded" health status and the size of the backlog owed.

## 16. Version Notes

| Java | Change relevant to Null Object |
|------|--------------------------------|
| 1.2 | `Collections.EMPTY_LIST` |
| 5 | Generic `Collections.emptyList()`; enums for singleton Null Objects |
| 7 | `Objects.requireNonNull` |
| 8 | `Optional`; default methods let interfaces provide no-op defaults |
| 9 | `List.of()`, `Map.of()` immutable empties; `Objects.requireNonNullElse` |
| 11 | `OutputStream.nullOutputStream()`, `Writer.nullWriter()`, `Reader.nullReader()` |
| 14 | Helpful NullPointerException messages name the null variable |
| 17 / 21 | Sealed interfaces and pattern matching for distinguishing special cases |

## 17. Expert Deep Dive

- **Null Object is a degenerate Strategy, State or Proxy.** Any of them can have a "do nothing" variant; `NoDiscount` is the identity strategy.
- **Identity elements.** The best neutral values are algebraic identities: `0` for sums, `1` for products, the empty list for concatenation, `x -> x` for transforms. If the operation has no identity (min, max), a Null Object is the wrong tool.
- **Default methods.** An interface can provide no-op defaults (`default void onEvent(Event e) {}`), turning every implementation into a partial Null Object. Listener interfaces in Swing's `*Adapter` classes did this before Java 8.
- **Null-safety tooling.** JSpecify annotations (`@Nullable`, `@NullMarked`) with NullAway or the Checker Framework catch remaining nulls at compile time; Null Objects reduce how many you need to annotate.
- **Valhalla.** Project Valhalla's proposed null-restricted types (`Foo!`) would let the type system forbid `null` directly; Special Case remains the way to model meaningful "missing" situations.

> 📖 **War story: the coupon that always worked**  
> ParcelGo's merchant checkout looked up promo codes and, to avoid NPEs, returned a shared `NoCoupon` for unknown codes. Unknown codes therefore produced no error message, and merchants assumed a typo'd code had been applied; support tickets doubled during a sale. Worse, a later "optimisation" cached the coupon per cart and a developer added a `discountPercent` setter on the shared `NoCoupon` for testing, which leaked into production and gave 15% off to every cart without a code for an afternoon. **Fixes:** separate `NoCoupon` (nothing typed) from `InvalidCoupon(code)` and `ExpiredCoupon(code, date)` special cases with messages; make all of them immutable enums or records; and add a test that `NoCoupon.INSTANCE.discount(x) == 0` for random `x`. **Lesson:** "absent" and "wrong" are different situations, and a shared Null Object must never have state.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is a Null Object?** An implementation that **does nothing**, used **instead of `null`**.
2. **What does it remove from callers?** **`null` checks** and the **NPEs** from forgotten ones.
3. **Give a JDK example.** **`Collections.emptyList()`**, `OutputStream.nullOutputStream()`.
4. **Why make it a singleton?** It has **no state**, so one instance is enough.
5. **When should you not use it?** When the operation **must happen** (payment) or callers **need to know** it's missing.

**▸ 🟡 L3**

1. **Null Object vs Optional?** Optional: **caller decides** (return values). Null Object: **behaviour already decided** (collaborators).
2. **What is Special Case?** A subclass with **situation-specific behaviour** (Guest, InvalidCoupon); Null Object is the special case for absence.
3. **Where should `null` become a special case?** **At the boundary** (repository, client).
4. **How can a Null Object hide failures?** Silent no-ops after **misconfiguration**; fix with **startup logs and counters**.
5. **What's a good neutral value?** The operation's **identity element**; none exists for min/max.

**▸ 🔴 L4–L5**

1. **Design wiring for optional integrations.** **Feature flags choose real vs null at startup**, required beans fail fast, disabled list exposed on health, suppression metrics.
2. **How do you handle a degraded dependency at checkout?** A **Special Case** returning safe values, flagging **degraded**, queuing owed work.
3. **Null Objects and security?** Neutral for access is **deny**; never a permissive no-op authorizer.
4. **How would you eliminate NPEs in a large codebase?** **JSpecify + NullAway**, empties not nulls, boundary conversion, Optional returns.
5. **When does Null Object conflict with fail-fast?** When it **masks configuration errors**; decide per collaborator whether it's optional.

**▸ 💻 Hands-on**

**Task:** Add an `AuditLog` collaborator to `CheckoutWiringDemo` that is required in production but optional in tests.

> ✅ **Solution**  
> ```java
> interface AuditLog { void record(String event); }
> enum NoAudit implements AuditLog { INSTANCE; public void record(String e) { } }
>
> // in wire(): required unless explicitly allowed (tests only)
> AuditLog audit = (AuditLog) realBeans.get("audit");
> if (audit == null) {
>     if (!Boolean.parseBoolean(config.getOrDefault("audit.optional", "false")))
>         throw new IllegalStateException("audit log is required");
>     audit = off(disabled, "audit", NoAudit.INSTANCE);
> }
> ```

## 19. Practice

- [ ] Replace a method that returns `null` for "no items" with one returning `List.of()`, and remove the callers' checks.
    > 💡 **Hint**  
    > Search for `== null` right after the call.

- [ ] Add a `ClosedStore` special case to a `Store` interface: no slots, a message with the reopening time.
    > 💡 **Hint**  
    > A record with `reopensAt`, returning `List.of()` from `availableSlots()`.

- [ ] Export `DisabledNotifier.suppressed` as a metric and write an alert rule.
    > 💡 **Hint**  
    > Alert if suppressed > 0 while `sms.enabled` is expected to be true.

- [ ] Find a `NoOp` implementation in a library you use and check whether its neutral values are neutral for every caller.
    > 💡 **Hint**  
    > Look at Micrometer, OpenTelemetry or SLF4J.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Null Object: same interface, does nothing; replaces `null` and its checks.
> - Stateless singleton (`enum`), immutable return values.
> - Special Case: specific behaviour for unusual situations (Guest, InvalidCoupon).
> - `Optional` for return values when the caller decides.
> - Never for required behaviour; make "nothing" observable.
>
> ```java
> enum NoDiscount implements Discount {
>     INSTANCE;
>     public long apply(long subtotal) { return subtotal; }
>     public String label() { return "none"; }
> }
> ```

## 21. Further Reading

- Bobby Woolf, "Null Object", in *Pattern Languages of Program Design 3* (1997).
- Martin Fowler, *Patterns of Enterprise Application Architecture* (2002), "Special Case"; and *Refactoring* (2nd ed., 2018), "Introduce Special Case".
- Joshua Bloch, *Effective Java* (3rd ed., 2018), Item 54 "Return empty collections or arrays, not nulls" and Item 55 "Return optionals judiciously".
- [JSpecify](https://jspecify.dev/): standard nullness annotations for Java.
- Tony Hoare, "Null References: The Billion Dollar Mistake" (QCon 2009 talk).

---

[← Previous: Interpreter](interpreter.md) · [Next: Specification →](specification.md)
