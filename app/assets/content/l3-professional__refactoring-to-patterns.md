# Refactoring to Patterns

L3 🟡 Professional · ⏱ 40 min read · Prerequisites: [Anti-Patterns and Code Smells](anti-patterns-and-code-smells.md), [Strategy](../l1-foundations/strategy.md)

> 📌 **TL;DR**  
> Patterns are best **discovered** in working code, not imposed up front. Joshua Kerievsky's *Refactoring to Patterns* (2004) shows how to move code **toward** a pattern when a smell justifies it, and **away** from one when it no longer pays. The workflow is always the same: pin current behaviour with tests (a golden master if needed), then change structure in small, behaviour-preserving steps, running the tests after each. Key moves: **Replace Conditional with Polymorphism**, **Replace Constructors with Creation Methods**, **Extract Strategy**, and **Inline** a pattern that adds only indirection. For risky production changes, use **Parallel Change** (expand, migrate, contract) with shadow comparison and gradual rollout.

## 1. What It Is

**Plain English.** Refactoring means changing how code is organised without changing what it does. Refactoring *to patterns* means using those small, safe changes to arrive at a design pattern (Strategy, State, Factory) when the code has grown to need it, or to remove one that has become dead weight.

**Definition.**

| Move | From | To | Triggered by |
|------|------|----|--------------|
| Replace Conditional with Polymorphism | `switch`/`if` on a type or status | Subclasses, sealed types, enum methods | The same switch in several places |
| Replace Constructors with Creation Methods | Overloaded constructors | Named static factories | Constructors whose meaning depends on argument types |
| Extract Strategy | Algorithm variants inside a method | Strategy interface + implementations | Variants selected by flags or config |
| Inline Pattern | Observer/Strategy/Factory with one fixed use | Direct calls | Abstraction with no variation |
| Parallel Change | Old implementation | New implementation | Risky replacements in production |

## 2. Why It Exists

Designing every pattern up front leads to speculative generality (the Golden Hammer); never refactoring leads to God Objects and spaghetti. Refactoring to patterns sits between: start simple, and let real duplication and real variation pull the design toward the pattern that removes them. Doing it in small steps under tests makes structural change routine instead of risky, which is what keeps a code base changeable for years.

## 3. Mental Model / Analogy

**Analogy.** Renovating a shop while it stays open. You don't knock down every wall at once. You put up a temporary partition (tests), move one shelf at a time, check customers can still find everything (run tests), and only remove the old shelving when nobody uses it. For a big change (new tills), you run old and new side by side, compare receipts, and switch over one counter at a time (parallel change).

**Technical model.** Every step keeps the code compiling and the tests green. A step is small enough that if tests fail, you revert instead of debugging.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Refactoring workflow with tests

**Description.** Before refactoring, make sure behaviour is pinned. If good unit tests exist, use them. If not, write **characterization tests** (also called a **golden master** or approval tests): run the current code over many inputs, save the outputs, and assert later versions produce exactly the same. Then refactor in tiny steps (extract method, rename, introduce guard clauses), running the check after each.

**Advantages / features.** Safe changes to code you don't fully understand; easy reverts; reviewers see each step's intent.

**RefactoringWorkflowDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The refactoring loop: pin current behaviour with a golden master, then change structure in small steps, re-checking each one. */
public class RefactoringWorkflowDemo {

    record Order(int items, long subtotal, String city, boolean express) {}

    // Step 0: legacy code, untouched.
    static String v0(Order o) {
        String s = "";
        if (o.city().equals("BLR") || o.city().equals("PUN")) { if (o.express()) s = "30 min"; else s = "2 h"; }
        else { if (o.express()) { if (o.items() > 20) s = "not available"; else s = "45 min"; } else s = "next day"; }
        if (o.subtotal() > 100_000 && !s.equals("not available")) s = s + " (priority)";
        return s;
    }

    // Step 1: extract method for the city check.
    static boolean metro(Order o) { return o.city().equals("BLR") || o.city().equals("PUN"); }
    static String v1(Order o) {
        String s = "";
        if (metro(o)) { if (o.express()) s = "30 min"; else s = "2 h"; }
        else { if (o.express()) { if (o.items() > 20) s = "not available"; else s = "45 min"; } else s = "next day"; }
        if (o.subtotal() > 100_000 && !s.equals("not available")) s = s + " (priority)";
        return s;
    }

    // Step 2: extract the base promise into its own method with guard clauses.
    static String basePromise(Order o) {
        if (metro(o)) return o.express() ? "30 min" : "2 h";
        if (!o.express()) return "next day";
        return o.items() > 20 ? "not available" : "45 min";
    }
    static String v2(Order o) {
        String s = basePromise(o);
        if (o.subtotal() > 100_000 && !s.equals("not available")) s = s + " (priority)";
        return s;
    }

    // Step 3: make "not available" explicit instead of comparing strings.
    static String v3(Order o) {
        if (!metro(o) && o.express() && o.items() > 20) return "not available";
        String base = basePromise(o);
        return o.subtotal() > 100_000 ? base + " (priority)" : base;
    }

    static List<Order> sampleInputs() {
        List<Order> inputs = new ArrayList<>();
        for (int items : new int[] {1, 20, 21})
            for (long subtotal : new long[] {5_000, 100_000, 100_001})
                for (String city : new String[] {"BLR", "PUN", "MYS"})
                    for (boolean express : new boolean[] {true, false})
                        inputs.add(new Order(items, subtotal, city, express));
        return inputs;
    }

    public static void main(String[] args) {
        List<Order> inputs = sampleInputs();
        List<String> golden = inputs.stream().map(RefactoringWorkflowDemo::v0).toList();
        List<Function<Order, String>> steps = List.of(RefactoringWorkflowDemo::v1, RefactoringWorkflowDemo::v2, RefactoringWorkflowDemo::v3);
        for (int i = 0; i < steps.size(); i++) {
            List<String> now = inputs.stream().map(steps.get(i)).toList();
            System.out.println("step " + (i + 1) + ": " + (now.equals(golden) ? "green" : "RED, revert") + " on " + inputs.size() + " inputs");
        }
    }
}
```

1. A systematic set of inputs, including the boundaries (20 vs 21 items, 100,000 vs 100,001 paise).
2. The golden master: today's outputs, whatever they are, become the expectation.
3. Each refactoring step is checked against it; a red step is reverted, not debugged.

**Run it:** `java RefactoringWorkflowDemo.java`

```text
step 1: green on 54 inputs
step 2: green on 54 inputs
step 3: green on 54 inputs
```

| Technique | Use when |
|-----------|----------|
| Existing unit tests | Code is already well tested |
| Characterization tests | Legacy code, unclear behaviour |
| Golden master over generated inputs | Pure functions with many combinations |
| Recorded production traffic | Services with complex real inputs |
| Approval tests (ApprovalTests library) | Large outputs (reports, JSON, HTML) |

**Use** characterization tests whenever behaviour isn't fully understood. **Avoid** "fixing" bugs during refactoring; note them, finish the refactoring, then fix them in a separate commit with a test.
**Real-world Java:** IntelliJ's automated refactorings (Extract Method, Introduce Parameter Object, Replace Constructor with Factory Method) are behaviour-preserving by construction.
**Best practice:** one refactoring per commit, with a message naming it. **Gotcha:** a golden master captures bugs too; that's intentional, since changing behaviour is a separate decision.

### Replace conditional with polymorphism

**Description.** When the same `switch` or `if` chain on a type code or status appears in several methods, move each branch into a type that represents that case. With Java 21, the types can be a sealed interface with records, so data specific to a case (rider and ETA only when out for delivery) lives only there.

**Advantages / features.** One place per case; new cases can't be forgotten; case-specific data has a home.

**ReplaceConditionalWithPolymorphismDemo.java**

```java
import java.util.List;

/** Replace a status switch duplicated across methods with types that carry their own behaviour. */
public class ReplaceConditionalWithPolymorphismDemo {

    // ---- Before ----
    static String messageBefore(String status, String rider, int eta) {
        return switch (status) {
            case "PLACED" -> "We've got your order";
            case "PACKED" -> "Packed and waiting for a rider";
            case "OUT" -> rider + " is on the way, " + eta + " min";
            case "DELIVERED" -> "Delivered. Enjoy!";
            default -> throw new IllegalArgumentException(status);
        };
    }
    static boolean canCancelBefore(String status) {
        return switch (status) { case "PLACED", "PACKED" -> true; default -> false; };
    }

    // ---- After ----
    sealed interface OrderStatus permits Placed, Packed, OutForDelivery, Delivered {
        String customerMessage();
        default boolean canCancel() { return false; }
    }
    record Placed() implements OrderStatus {
        public String customerMessage() { return "We've got your order"; }
        public boolean canCancel() { return true; }
    }
    record Packed() implements OrderStatus {
        public String customerMessage() { return "Packed and waiting for a rider"; }
        public boolean canCancel() { return true; }
    }
    record OutForDelivery(String rider, int etaMinutes) implements OrderStatus {
        public String customerMessage() { return rider + " is on the way, " + etaMinutes + " min"; }
    }
    record Delivered() implements OrderStatus {
        public String customerMessage() { return "Delivered. Enjoy!"; }
    }

    public static void main(String[] args) {
        List<OrderStatus> statuses = List.of(new Placed(), new Packed(), new OutForDelivery("Bina", 9), new Delivered());
        List<String> codes = List.of("PLACED", "PACKED", "OUT", "DELIVERED");
        for (int i = 0; i < statuses.size(); i++) {
            OrderStatus s = statuses.get(i);
            boolean same = s.customerMessage().equals(messageBefore(codes.get(i), "Bina", 9)) && s.canCancel() == canCancelBefore(codes.get(i));
            System.out.println(String.format("%-10s %-36s cancel=%-5s same=%s", codes.get(i), s.customerMessage(), s.canCancel(), same));
        }
    }
}
```

1. Before: string codes switched in two methods, with rider and ETA passed even when meaningless.
2. A sealed interface lists every status; each one implements its behaviour.
3. Only the out-for-delivery status carries a rider and ETA.

**Run it:** `java ReplaceConditionalWithPolymorphismDemo.java`

```text
PLACED     We've got your order                 cancel=true  same=true
PACKED     Packed and waiting for a rider       cancel=true  same=true
OUT        Bina is on the way, 9 min            cancel=false same=true
DELIVERED  Delivered. Enjoy!                    cancel=false same=true
```

| Steps (Fowler) | |
|----------------|-|
| 1 | Create the type hierarchy (or sealed interface) for the cases |
| 2 | Replace creation of codes with creation of types (factory) |
| 3 | Move one branch at a time into an overriding method; run tests |
| 4 | When all branches are moved, delete the switch |

**Use** it when cases are stable and operations grow. **Avoid** it when operations change more often than cases; then keep a sealed hierarchy with exhaustive `switch` expressions (Visitor-like), so new operations don't touch every type.
**Real-world Java:** `java.util.concurrent.TimeUnit` (enum methods), Spring's `HttpStatus.Series`.
**Best practice:** let the compiler help: sealed types + exhaustive switches fail compilation when a case is missing. **Gotcha:** don't move persistence or UI into the status types; keep them about domain behaviour.

### Replace constructors with creation methods

**Description.** When a class has several constructors whose meaning depends on argument types or order (`new Coupon("X", 100L, 500L)`), replace them with **static creation methods** named after intent: `Coupon.percentOff`, `Coupon.flatOff`, `Coupon.freeDelivery`. Each can validate its own rules and use clearer units.

**Advantages / features.** Readable call sites; per-variant validation; freedom to return cached or subclass instances later.

**CreationMethodsDemo.java**

```java
/** Replace overloaded constructors whose meaning depends on argument order with named creation methods. */
public class CreationMethodsDemo {

    // ---- Before: which constructor makes a "percent off" coupon? What does the boolean mean? ----
    static final class CouponBefore {
        final String code; final int percent; final long flatPaise; final boolean freeDelivery; final long minOrder;
        CouponBefore(String code, int percent) { this(code, percent, 0, false, 0); }
        CouponBefore(String code, long flatPaise, long minOrder) { this(code, 0, flatPaise, false, minOrder); }
        CouponBefore(String code, boolean freeDelivery) { this(code, 0, 0, freeDelivery, 0); }
        CouponBefore(String code, int percent, long flatPaise, boolean freeDelivery, long minOrder) {
            this.code = code; this.percent = percent; this.flatPaise = flatPaise; this.freeDelivery = freeDelivery; this.minOrder = minOrder;
        }
    }

    // ---- After: intention-revealing names, validation per kind, one private constructor. ----
    record Coupon(String code, int percent, long flatPaise, boolean freeDelivery, long minOrderPaise) {
        static Coupon percentOff(String code, int percent) {
            if (percent < 1 || percent > 50) throw new IllegalArgumentException("percent must be 1..50");
            return new Coupon(code, percent, 0, false, 0);
        }
        static Coupon flatOff(String code, long rupees, long minOrderRupees) {
            if (rupees * 2 > minOrderRupees) throw new IllegalArgumentException("flat discount must be at most half the minimum order");
            return new Coupon(code, 0, rupees * 100, false, minOrderRupees * 100);
        }
        static Coupon freeDelivery(String code) { return new Coupon(code, 0, 0, true, 0); }

        long discountOn(long subtotal) {
            if (subtotal < minOrderPaise) return 0;
            return percent > 0 ? subtotal * percent / 100 : flatPaise;
        }
    }

    public static void main(String[] args) {
        CouponBefore confusing = new CouponBefore("SAVE", 100L, 500L);   // 100 paise flat? 100 percent? min order 5 rupees?
        System.out.println("before: flat=" + confusing.flatPaise + " paise, min=" + confusing.minOrder + " (units unclear)");

        Coupon fresh10 = Coupon.percentOff("FRESH10", 10);
        Coupon save100 = Coupon.flatOff("SAVE100", 100, 500);
        Coupon ship = Coupon.freeDelivery("SHIPFREE");
        for (Coupon c : new Coupon[] {fresh10, save100, ship})
            System.out.println(c.code() + ": discount on Rs 600 = Rs " + c.discountOn(60_000) / 100 + ", free delivery=" + c.freeDelivery());
        try {
            Coupon.percentOff("OOPS", 90);
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
```

1. Before: overloaded constructors; `100L, 500L` could mean anything.
2. After: named creation methods with intent-specific validation and units (rupees in, paise inside).
3. Behaviour depends on the fields set by each creation method.

**Run it:** `java CreationMethodsDemo.java`

```text
before: flat=100 paise, min=500 (units unclear)
FRESH10: discount on Rs 600 = Rs 60, free delivery=false
SAVE100: discount on Rs 600 = Rs 100, free delivery=false
SHIPFREE: discount on Rs 600 = Rs 0, free delivery=true
rejected: percent must be 1..50
```

| Option | When |
|--------|------|
| Creation methods (`of`, `from`, named) | A few distinct ways to create |
| Builder | Many optional parameters |
| Factory class | Creation needs dependencies or chooses subclasses |
| Plain constructor | One obvious way to create |

**Use** creation methods when there are distinct kinds of an object. **Avoid** them when there's only one way to construct; `new` is clearer.
**Real-world Java:** `List.of`, `Duration.ofMinutes`, `BigDecimal.valueOf`, `Optional.empty`, `LocalDate.of`.
**Best practice:** follow JDK naming: `of`, `from`, `valueOf`, `parse`, `newInstance`, or intent names. **Gotcha:** record canonical constructors stay public; validate in the compact constructor too, or callers can bypass the creation methods.

### Extract strategy

**Description.** When a method contains several versions of an algorithm chosen by flags or config (`if city == "BLR" ... else if city == "PUN"`), extract each version into a class (or lambda) implementing a common interface, and choose one by configuration. The method shrinks to "pick strategy, call it".

**Advantages / features.** New variants without editing existing code; each variant tested alone; selection becomes data.

**ExtractStrategyDemo.java**

```java
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Extract Strategy: pull a varying algorithm out of a service into an interface with one class per variant. */
public class ExtractStrategyDemo {

    record Rider(String name, double km, int activeOrders, double rating) {}

    // ---- Before: the algorithm and the city switch are buried in the service. ----
    static String assignBefore(String city, List<Rider> riders) {
        Rider best = null;
        for (Rider r : riders) {
            if (city.equals("BLR")) {
                if (best == null || r.km() < best.km()) best = r;
            } else if (city.equals("PUN")) {
                if (r.activeOrders() < 2 && (best == null || r.rating() > best.rating())) best = r;
            } else {
                if (best == null || r.activeOrders() < best.activeOrders()) best = r;
            }
        }
        return best == null ? "none" : best.name();
    }

    // ---- After ----
    interface AssignmentStrategy { String pick(List<Rider> riders); }

    static final AssignmentStrategy NEAREST = rs -> rs.stream().min(Comparator.comparingDouble(Rider::km)).map(Rider::name).orElse("none");
    static final AssignmentStrategy BEST_RATED_NOT_BUSY = rs -> rs.stream().filter(r -> r.activeOrders() < 2)
            .max(Comparator.comparingDouble(Rider::rating)).map(Rider::name).orElse("none");
    static final AssignmentStrategy LEAST_BUSY = rs -> rs.stream().min(Comparator.comparingInt(Rider::activeOrders)).map(Rider::name).orElse("none");

    record Dispatcher(Map<String, AssignmentStrategy> byCity, AssignmentStrategy fallback) {
        String assign(String city, List<Rider> riders) { return byCity.getOrDefault(city, fallback).pick(riders); }
    }

    public static void main(String[] args) {
        List<Rider> riders = List.of(new Rider("Arjun", 1.2, 2, 4.9), new Rider("Bina", 2.5, 0, 4.7), new Rider("Chetan", 0.8, 3, 4.2));
        Dispatcher dispatcher = new Dispatcher(Map.of("BLR", NEAREST, "PUN", BEST_RATED_NOT_BUSY), LEAST_BUSY);
        for (String city : List.of("BLR", "PUN", "MYS"))
            System.out.println(city + ": before " + assignBefore(city, riders) + ", after " + dispatcher.assign(city, riders));
    }
}
```

1. Before: three algorithms interleaved in one loop, selected by city.
2. A strategy interface; each variant is a small, readable lambda.
3. The dispatcher maps cities to strategies with a fallback; outputs match the original.

**Run it:** `java ExtractStrategyDemo.java`

```text
BLR: before Chetan, after Chetan
PUN: before Bina, after Bina
MYS: before Bina, after Bina
```

| Step | Action |
|------|--------|
| 1 | Extract each branch's logic into its own method |
| 2 | Introduce the interface; make each method an implementation |
| 3 | Replace the conditional with a lookup (map, config, DI) |
| 4 | Move selection to configuration; delete the conditional |

**Use** Extract Strategy when variants differ by city, customer type, experiment or version. **Avoid** it for two variants that will never grow; an `if` is fine.
**Real-world Java:** Spring injects `Map<String, Strategy>` of all beans implementing an interface; `Comparator` strategies.
**Best practice:** name strategies by the business rule (`NEAREST`, `BEST_RATED_NOT_BUSY`), not by city. **Gotcha:** subtle shared state in the original loop (like the `best` variable reused across branches) can make variants not quite independent; check with a golden master.

### Inline a pattern that doesn't pay

**Description.** Patterns cost indirection. When an Observer has exactly one listener that will always be there, a Strategy has one implementation, or a Factory always creates the same class, inline it: call the collaborator directly. Kerievsky calls these refactorings "away from" patterns. The dependency becomes visible and navigation becomes trivial.

**Advantages / features.** Less code; "find usages" works; fewer runtime surprises (registration order, missing listeners).

**InlinePatternDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Refactoring away from a pattern: an Observer with exactly one listener, forever, is just an indirect method call. */
public class InlinePatternDemo {

    static final List<String> audit = new ArrayList<>();

    // ---- Before: event bus, listener interface, registration, for one fixed listener. ----
    interface PriceChangedListener { void onPriceChanged(String sku, long oldP, long newP); }
    static final class PriceEvents {
        private final List<PriceChangedListener> listeners = new ArrayList<>();
        void register(PriceChangedListener l) { listeners.add(l); }
        void publish(String sku, long o, long n) { listeners.forEach(l -> l.onPriceChanged(sku, o, n)); }
    }
    static final class AuditListener implements PriceChangedListener {
        public void onPriceChanged(String sku, long o, long n) { audit.add(sku + " " + o + "->" + n); }
    }
    static final class CatalogBefore {
        private final PriceEvents events;
        CatalogBefore(PriceEvents events) { this.events = events; }
        void changePrice(String sku, long o, long n) { events.publish(sku, o, n); }   // where does this go? search the code base...
    }

    // ---- After: the one collaborator is called directly; the dependency is visible. ----
    record PriceAudit(List<String> log) { void record(String sku, long o, long n) { log.add(sku + " " + o + "->" + n); } }
    record Catalog(PriceAudit audit) {
        void changePrice(String sku, long o, long n) { audit.record(sku, o, n); }
    }

    public static void main(String[] args) {
        PriceEvents events = new PriceEvents();
        events.register(new AuditListener());
        new CatalogBefore(events).changePrice("GHEE-1L", 65_000, 62_000);
        System.out.println("before: " + audit);

        List<String> log = new ArrayList<>();
        new Catalog(new PriceAudit(log)).changePrice("GHEE-1L", 65_000, 62_000);
        System.out.println("after:  " + log + ", same=" + log.equals(audit));
    }
}
```

1. Before: listener interface, event registry and registration code for a single audit listener.
2. After: a plainly named collaborator.
3. The catalogue calls it directly; same result, and the dependency is in the constructor.

**Run it:** `java InlinePatternDemo.java`

```text
before: [GHEE-1L 65000->62000]
after:  [GHEE-1L 65000->62000], same=true
```

| Pattern | Inline when |
|---------|-------------|
| Observer / events | One listener, same module, needs to run synchronously and in a transaction |
| Strategy | One implementation for a year, no plans for more |
| Factory | Always creates the same concrete class, no configuration |
| Decorator chain | One decorator, always applied |
| Interface + Impl | One implementation, not a module boundary, not mocked |

**Use** inlining when variation never arrived. **Avoid** inlining real boundaries: module APIs, ports to infrastructure, extension points for plugins.
**Real-world Java:** many `FooService` + `FooServiceImpl` pairs where the interface exists only by habit.
**Best practice:** inline with the IDE (Inline Class / Inline Method) to keep it behaviour-preserving. **Gotcha:** events are sometimes used for decoupling across modules or for after-commit side effects; check for those reasons before inlining.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** TextBlast's SMS builder is one dense method. Extract named steps without changing the messages.

**ExtractMethodDemo.java**

```java
/** TextBlast: Extract Method turns a dense block into named steps, the first move in almost every refactoring. */
public class ExtractMethodDemo {

    // ---- Before ----
    static String smsBefore(String name, String orderId, long paise, int eta) {
        String n = name == null || name.isBlank() ? "there" : name.strip().split(" ")[0];
        String amt = "Rs " + paise / 100 + (paise % 100 == 0 ? "" : "." + String.format("%02d", paise % 100));
        String msg = "Hi " + n + ", order " + orderId + " (" + amt + ") arrives in " + eta + " min.";
        return msg.length() > 160 ? msg.substring(0, 157) + "..." : msg;
    }

    // ---- After ----
    static String sms(String name, String orderId, long paise, int eta) {
        return fitToSms("Hi " + firstName(name) + ", order " + orderId + " (" + rupees(paise) + ") arrives in " + eta + " min.");
    }
    static String firstName(String name) { return name == null || name.isBlank() ? "there" : name.strip().split(" ")[0]; }
    static String rupees(long paise) { return "Rs " + paise / 100 + (paise % 100 == 0 ? "" : "." + String.format("%02d", paise % 100)); }
    static String fitToSms(String text) { return text.length() > 160 ? text.substring(0, 157) + "..." : text; }

    public static void main(String[] args) {
        System.out.println(sms("Asha Rao", "FC-12", 51_850, 18));
        System.out.println(sms(" ", "FC-13", 20_000, 25));
        System.out.println("same as before: " + (sms("Asha Rao", "FC-12", 51_850, 18).equals(smsBefore("Asha Rao", "FC-12", 51_850, 18))
                && sms(null, "X", 1, 1).equals(smsBefore(null, "X", 1, 1))));
    }
}
```

1. The top-level method now reads like the requirement.
2. Each extracted method has a name that says what it does and can be tested alone.

**Run it:** `java ExtractMethodDemo.java`

**Output:**

```text
Hi Asha, order FC-12 (Rs 518.50) arrives in 18 min.
Hi there, order FC-13 (Rs 200) arrives in 25 min.
same as before: true
```

**What to notice:**

- Extract Method is the first step of almost every larger refactoring, including the pattern moves on this page.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the discount method grows a new `if` every sale. Marketing wants each discount shown on the receipt. Refactor the conditional chain into rule objects, proven equivalent on 20,000 random carts, and get the receipt lines for free.

**DiscountRulesRefactorDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FreshCart discounts: a growing if-chain refactored, step by step under a golden master, into a list of rule
 * objects (Strategy + Chain of Responsibility). New promotions become new classes, not new branches.
 */
public class DiscountRulesRefactorDemo {

    record Cart(long subtotal, boolean member, boolean firstOrder, int dairyItems, String coupon) {}

    // ---- Before ----
    static long discountBefore(Cart c) {
        long d = 0;
        if (c.firstOrder()) d += Math.min(c.subtotal() / 5, 10_000);
        if (c.member() && c.subtotal() >= 30_000) d += c.subtotal() / 20;
        if (c.dairyItems() >= 3) d += 2_000;
        if ("FRESH10".equals(c.coupon()) && !c.firstOrder()) d += c.subtotal() / 10;
        return Math.min(d, c.subtotal() / 2);
    }

    // ---- After ----
    interface DiscountRule {
        String name();
        long discount(Cart cart);
    }
    record FirstOrder() implements DiscountRule {
        public String name() { return "first order 20% (max Rs 100)"; }
        public long discount(Cart c) { return c.firstOrder() ? Math.min(c.subtotal() / 5, 10_000) : 0; }
    }
    record MemberFivePercent() implements DiscountRule {
        public String name() { return "member 5% over Rs 300"; }
        public long discount(Cart c) { return c.member() && c.subtotal() >= 30_000 ? c.subtotal() / 20 : 0; }
    }
    record DairyBundle() implements DiscountRule {
        public String name() { return "3+ dairy items Rs 20"; }
        public long discount(Cart c) { return c.dairyItems() >= 3 ? 2_000 : 0; }
    }
    record Fresh10Coupon() implements DiscountRule {
        public String name() { return "FRESH10 coupon"; }
        public long discount(Cart c) { return "FRESH10".equals(c.coupon()) && !c.firstOrder() ? c.subtotal() / 10 : 0; }
    }

    record Breakdown(List<String> applied, long total) {}

    record DiscountEngine(List<DiscountRule> rules, int capPercent) {
        Breakdown apply(Cart c) {
            List<String> applied = new ArrayList<>();
            long sum = 0;
            for (DiscountRule r : rules) {
                long d = r.discount(c);
                if (d > 0) { applied.add(r.name() + " -" + d); sum += d; }
            }
            return new Breakdown(applied, Math.min(sum, c.subtotal() * capPercent / 100));
        }
    }

    public static void main(String[] args) {
        var engine = new DiscountEngine(List.of(new FirstOrder(), new MemberFivePercent(), new DairyBundle(), new Fresh10Coupon()), 50);
        Random random = new Random(11);
        int mismatches = 0;
        for (int i = 0; i < 20_000; i++) {
            Cart c = new Cart(random.nextInt(200_000), random.nextBoolean(), random.nextInt(5) == 0, random.nextInt(6),
                    random.nextBoolean() ? "FRESH10" : null);
            if (engine.apply(c).total() != discountBefore(c)) mismatches++;
        }
        System.out.println("golden master: 20000 random carts, mismatches: " + mismatches);
        Breakdown b = engine.apply(new Cart(60_000, true, false, 4, "FRESH10"));
        System.out.println("receipt lines: " + b.applied() + ", total " + b.total());
    }
}
```

1. Each discount is a rule with a name and a calculation (Strategy).
2. The engine applies every rule in order and caps the total (a simple chain).
3. A golden master over random carts compares the engine with the original method.

**Run it:** `java DiscountRulesRefactorDemo.java`

**Output:**

```text
golden master: 20000 random carts, mismatches: 0
receipt lines: [member 5% over Rs 300 -3000, 3+ dairy items Rs 20 -2000, FRESH10 coupon -6000], total 11000
```

**What to notice:**

- Zero mismatches gives confidence; the boundary cases are also checked explicitly in the test.
- The receipt breakdown was hard with the `if` chain and trivial with named rules: refactoring made the feature easy.

### Example 3: Production-grade

**Scenario:** PayLink is moving FreshCart to a new fee calculation service. Fees affect every order, so the switch must be provably safe. Use **Parallel Change**: put old and new behind one interface; run the new one in shadow mode, comparing results without affecting customers; fix mismatches; roll out by percentage with stable per-order bucketing; keep a kill switch; finally delete the old code.

**ParallelChangeDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * PayLink to PayLink v2 migration with Parallel Change (expand, migrate, contract): both implementations sit behind
 * one interface; a deterministic percentage rollout routes traffic; the old path runs as control and the new one is
 * compared in shadow mode (a "Scientist"-style experiment); a kill switch reverts instantly.
 */
public class ParallelChangeDemo {

    record Quote(long feePaise, String currency) {}

    interface FeeCalculator { Quote fee(String orderId, long amountPaise); }

    /** Old implementation: flat 2% with a Rs 3 minimum. */
    static final class LegacyFees implements FeeCalculator {
        public Quote fee(String orderId, long amount) { return new Quote(Math.max(amount * 2 / 100, 300), "INR"); }
    }
    /** New implementation: should match, but rounds half up where the legacy code truncates. */
    static final class V2Fees implements FeeCalculator {
        public Quote fee(String orderId, long amount) {
            long fee = Math.round(amount * 0.02);
            return new Quote(Math.max(fee, 300), "INR");
        }
    }

    /** Runs control, optionally the candidate, compares, and never lets the candidate hurt the caller. */
    static final class Experiment<T> {
        int compared, mismatched, candidateErrors;
        final List<String> mismatches = new ArrayList<>();
        T run(String key, Supplier<T> control, Supplier<T> candidate) {
            T expected = control.get();
            try {
                T actual = candidate.get();
                compared++;
                if (!Objects.equals(expected, actual)) {
                    mismatched++;
                    if (mismatches.size() < 3) mismatches.add(key + ": " + expected + " vs " + actual);
                }
            } catch (RuntimeException e) {
                candidateErrors++;
            }
            return expected;
        }
    }

    record Rollout(int percent, boolean killSwitch) {
        Rollout { if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent must be 0..100"); }
        boolean useNew(String orderId) {
            return !killSwitch && Math.floorMod(orderId.hashCode(), 100) < percent;
        }
    }

    static final class RoutingFees implements FeeCalculator {
        final FeeCalculator legacy, v2; final Experiment<Quote> shadow = new Experiment<>();
        Rollout rollout; boolean shadowCompare;
        int servedByNew;
        RoutingFees(FeeCalculator legacy, FeeCalculator v2, Rollout rollout, boolean shadowCompare) {
            this.legacy = legacy; this.v2 = v2; this.rollout = rollout; this.shadowCompare = shadowCompare;
        }
        public Quote fee(String orderId, long amount) {
            if (rollout.useNew(orderId)) { servedByNew++; return v2.fee(orderId, amount); }
            if (shadowCompare) return shadow.run(orderId, () -> legacy.fee(orderId, amount), () -> v2.fee(orderId, amount));
            return legacy.fee(orderId, amount);
        }
    }

    static long amountFor(int i) { return 10_000 + (i * 7_919L) % 200_000; }

    public static void main(String[] args) {
        // Phase 1 (expand): v2 exists, 0% traffic, shadow comparison on.
        RoutingFees fees = new RoutingFees(new LegacyFees(), new V2Fees(), new Rollout(0, false), true);
        for (int i = 0; i < 1_000; i++) fees.fee("FC-" + i, amountFor(i));
        System.out.println("shadow: compared " + fees.shadow.compared + ", mismatched " + fees.shadow.mismatched + ", served by v2 " + fees.servedByNew);
        fees.shadow.mismatches.forEach(m -> System.out.println("  " + m));

        // Fix the bug, re-run shadow until clean, then migrate gradually.
        RoutingFees fixed = new RoutingFees(new LegacyFees(),
                (id, amount) -> new Quote(Math.max(amount * 2 / 100, 300), "INR"), new Rollout(0, false), true);
        for (int i = 0; i < 1_000; i++) fixed.fee("FC-" + i, amountFor(i));
        System.out.println("after fix: compared " + fixed.shadow.compared + ", mismatched " + fixed.shadow.mismatched);

        fixed.rollout = new Rollout(25, false);
        for (int i = 0; i < 1_000; i++) fixed.fee("FC-" + i, amountFor(i));
        System.out.println("25% rollout: served by v2 " + fixed.servedByNew + " of 1000");
        System.out.println("same order, same path: " + (fixed.rollout.useNew("FC-42") == fixed.rollout.useNew("FC-42")));

        fixed.rollout = new Rollout(25, true);
        int before = fixed.servedByNew;
        for (int i = 0; i < 1_000; i++) fixed.fee("FC-" + i, amountFor(i));
        System.out.println("kill switch on: served by v2 " + (fixed.servedByNew - before));
        // Phase 3 (contract): at 100% with no mismatches for a while, delete LegacyFees, the router and the flag.
    }
}
```

1. One interface for both implementations: the seam that makes parallel change possible.
2. The experiment always returns the control result; candidate errors and mismatches are only recorded.
3. Bucketing by order ID makes routing deterministic: the same order always takes the same path.
4. The router combines rollout percentage, kill switch and shadow mode.
5. After the rounding bug is fixed, the shadow run shows zero mismatches.

**Run it:** `java ParallelChangeDemo.java`

**Output:**

```text
shadow: compared 1000, mismatched 487, served by v2 0
  FC-2: Quote[feePaise=516, currency=INR] vs Quote[feePaise=517, currency=INR]
  FC-4: Quote[feePaise=833, currency=INR] vs Quote[feePaise=834, currency=INR]
  FC-5: Quote[feePaise=991, currency=INR] vs Quote[feePaise=992, currency=INR]
after fix: compared 1000, mismatched 0
25% rollout: served by v2 264 of 1000
same order, same path: true
kill switch on: served by v2 0
```

**What to notice:**

- Shadow mode found a one-paisa rounding difference in almost half of orders before any customer saw it.
- At 25%, about a quarter of orders used v2; raising the percentage keeps earlier orders on v2 (tested).
- The kill switch sent all traffic back to the old path instantly, with no deploy.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Small steps under tests | Safe, reviewable, revertible | Slower than a big edit (in the short term) |
| Golden master | Works on legacy code with no tests | Freezes bugs; brittle for non-deterministic output |
| Toward patterns | Removes duplication, enables variation | Adds indirection |
| Away from patterns | Less code, clearer flow | May need re-adding if variation appears |
| Parallel change | Zero-downtime, measurable migrations | Temporary duplication, flags to clean up |

## 8. When to Use / When NOT to Use

**Use them when:**

- you're about to change code and the current structure makes the change hard ("make the change easy, then make the easy change");
- the same conditional or duplication appears for the third time;
- replacing a critical component in production.

**Don't use them when:**

- the code is stable and nobody needs to change it;
- you can't pin behaviour and the risk is high (write tests first);
- the "pattern" would serve hypothetical future variation.

## 9. Best Practices

1. **Make the change easy, then make the easy change.** *(Consensus; Kent Beck.)*
2. **Separate refactoring commits from behaviour changes.** *(Consensus.)*
3. **Pin behaviour first; revert red steps instead of debugging.** *(Consensus.)*
4. **Use IDE automated refactorings where available.** *(Consensus.)*
5. **Refactor toward patterns on the rule of three.** *(Consensus.)*
6. **For production replacements: expand, shadow, migrate, contract.** *(Consensus.)*
7. **Remove flags and old paths promptly after migration.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Big-bang migration without shadowing (production failure mode)**  
> **Symptom:** after switching to a new fee service, finance found a Rs 4 lakh discrepancy at month end.
> **Root cause:** the new service rounded half-up while the old one truncated; no comparison was run before cutover.
> **Fix:** shadow comparison on real traffic until mismatches are zero (or explained), then gradual rollout with a kill switch.

> 🚨 **Refactoring and feature in one PR**  
> **Symptom:** a 2,000-line PR "adds express delivery" and nobody can review it; a regression in standard delivery ships.
> **Root cause:** structural changes mixed with behaviour changes.
> **Fix:** one PR (or commit series) that refactors with no behaviour change, then a small PR for the feature.

> 🚨 **Non-deterministic golden master**  
> **Symptom:** characterization tests fail randomly.
> **Root cause:** outputs include timestamps, random IDs or map iteration order.
> **Fix:** inject clocks and ID generators, sort collections, or scrub volatile fields before comparing.

> 🚨 **Flags that never die**  
> **Symptom:** three years later, the code has both fee paths and nobody knows which runs.
> **Root cause:** the contract phase never happened.
> **Fix:** a ticket for removal created with the flag; an expiry date the build checks.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Rollout percentage | 0 | Share of traffic on the new path | Step up: 1, 5, 25, 50, 100 | Larger blast radius |
| Shadow mode | off | Runs candidate for comparison | During expand phase | Double load on dependencies |
| Shadow sample rate | 100% | Fraction compared | High-volume paths | Missing rare mismatches |
| Kill switch | off | Forces the old path | Incidents | None (that's the point) |
| Mismatch alert threshold | — | When to page | Before increasing rollout | Noise vs missed issues |
| Flag expiry | — | Date to remove flag | Always set | Flag debt |

## 12. Security Considerations

- Shadow mode must not cause side effects twice: never shadow non-idempotent calls (payments, emails) for real; compare computations or use test doubles.
- Logs of mismatches may contain personal or financial data; log IDs and diffs, not full payloads.
- Refactoring can silently drop security checks (an authorisation `if` deleted as "dead code"); keep security tests in the golden suite.
- Feature flag systems are control planes; restrict who can change production flags and audit changes.

## 13. Performance & Scalability

Refactoring to patterns usually costs little at runtime: the JIT inlines small methods and monomorphic interface calls (see [Performance Cost of Patterns](performance-of-patterns.md)). Shadow mode doubles the work on the shadowed path; sample it on hot paths and run candidates asynchronously if latency matters. Golden-master test suites with large input sets should run in CI in parallel, or on a sample per commit and fully nightly.

## 14. Testing It

Pin behaviour, then test the migration mechanics themselves (control result always returned, candidate failures contained, rollout deterministic and monotonic, kill switch works):

**ParallelChangeTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ParallelChangeTest {

    @Test
    void shadowModeAlwaysReturnsTheControlResult() {
        var fees = new ParallelChangeDemo.RoutingFees(new ParallelChangeDemo.LegacyFees(), new ParallelChangeDemo.V2Fees(),
                new ParallelChangeDemo.Rollout(0, false), true);
        for (int i = 0; i < 200; i++) {
            long amount = ParallelChangeDemo.amountFor(i);
            assertEquals(new ParallelChangeDemo.LegacyFees().fee("x", amount), fees.fee("FC-" + i, amount));
        }
        assertTrue(fees.shadow.mismatched > 0);
    }

    @Test
    void candidateFailuresNeverReachTheCaller() {
        var exp = new ParallelChangeDemo.Experiment<String>();
        assertEquals("ok", exp.run("k", () -> "ok", () -> { throw new IllegalStateException("boom"); }));
        assertEquals(1, exp.candidateErrors);
    }

    @Test
    void rolloutIsDeterministicMonotonicAndKillable() {
        List<String> ids = IntStream.range(0, 5_000).mapToObj(i -> "FC-" + i).toList();
        var r10 = new ParallelChangeDemo.Rollout(10, false);
        var r50 = new ParallelChangeDemo.Rollout(50, false);
        for (String id : ids) {
            assertEquals(r10.useNew(id), r10.useNew(id));
            if (r10.useNew(id)) assertTrue(r50.useNew(id), "raising the percentage must keep earlier users on v2");
        }
        long share = ids.stream().filter(r50::useNew).count();
        assertTrue(share > 2_250 && share < 2_750, "about half: " + share);
        assertFalse(ids.stream().anyMatch(new ParallelChangeDemo.Rollout(100, true)::useNew));
        assertThrows(IllegalArgumentException.class, () -> new ParallelChangeDemo.Rollout(101, false));
    }

    @Test
    void refactoredDiscountsMatchTheOriginalOnEdgeCases() {
        var engine = new DiscountRulesRefactorDemo.DiscountEngine(List.of(new DiscountRulesRefactorDemo.FirstOrder(),
                new DiscountRulesRefactorDemo.MemberFivePercent(), new DiscountRulesRefactorDemo.DairyBundle(),
                new DiscountRulesRefactorDemo.Fresh10Coupon()), 50);
        for (long subtotal : new long[] {0, 1, 29_999, 30_000, 50_000, 1_000_000})
            for (boolean first : new boolean[] {true, false}) {
                var cart = new DiscountRulesRefactorDemo.Cart(subtotal, true, first, 3, "FRESH10");
                assertEquals(DiscountRulesRefactorDemo.discountBefore(cart), engine.apply(cart).total());
            }
    }

    @Test
    void everyRefactoringStepMatchesTheGoldenMaster() {
        var inputs = RefactoringWorkflowDemo.sampleInputs();
        for (var o : inputs) {
            String golden = RefactoringWorkflowDemo.v0(o);
            assertEquals(golden, RefactoringWorkflowDemo.v1(o));
            assertEquals(golden, RefactoringWorkflowDemo.v2(o));
            assertEquals(golden, RefactoringWorkflowDemo.v3(o));
        }
    }
}
```

## 15. Observability & Operations

During a parallel change, dashboard: share of traffic per path, mismatch rate (with examples), candidate error rate, and latency of both paths. Promote to the next rollout step only when mismatch and error rates are zero or explained over a full business cycle (including peaks). After contract, remove the dashboard and the flag so nobody relies on stale signals.

## 16. Version Notes

| Year / version | Change |
|----------------|--------|
| 1999 | Fowler's *Refactoring* (Java examples) |
| 2001+ | IntelliJ IDEA and Eclipse automate refactorings |
| 2004 | Kerievsky's *Refactoring to Patterns*; Feathers' characterization tests |
| 2014 | Danilo Sato's "Parallel Change"; GitHub's Scientist library (Ruby, later ported to Java) |
| 2018 | *Refactoring* 2nd edition (JavaScript examples, same catalogue) |
| Java 17–21 | Sealed types and pattern matching add a "sealed + switch" alternative to polymorphism |

## 17. Expert Deep Dive

- **Branch by abstraction.** Parallel change in code: introduce an abstraction over the old component, switch callers to it, build the new implementation behind it, switch over, remove the old one, all on trunk without long-lived branches.
- **Expression problem.** Polymorphism makes adding types easy; sealed switches make adding operations easy. Choose based on which axis changes more; refactoring between them is mechanical.
- **Mikado method.** For large refactorings, try the goal, note what breaks, revert, fix prerequisites first; build a dependency graph of small steps.
- **Refactoring at the service level.** Strangler fig, parallel run and shadow traffic apply the same ideas to services and databases (dual writes with reconciliation).
- **Automated refactoring at scale.** OpenRewrite recipes apply refactorings (framework upgrades, API migrations) across hundreds of repositories reproducibly.

> 📖 **War story: the one-paisa migration**  
> PayLink's fee engine was rewritten as a separate service. A shadow comparison was added "just in case" and ran for a week. It reported mismatches on 48% of orders, all by one paisa: the new code used `Math.round(amount * 0.02)` while the old truncated with integer arithmetic. Individually trivial, it would have meant roughly Rs 4 lakh per month of fee differences and a reconciliation nightmare with the payment partner. The fix was a one-line change; shadow mode then showed zero mismatches for two weeks, through a sale weekend. The rollout went 1%, 10%, 50%, 100% over ten days, and the old path, router and flag were deleted in the following sprint. **Lesson:** compare before you switch; trivial differences multiplied by millions of orders are not trivial.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's refactoring?** **Changing structure without changing behaviour.**
2. **Why tests before refactoring?** To **detect accidental behaviour changes**.
3. **What's Extract Method?** Moving a block into a **named method**.
4. **What's a creation method?** A **named static factory** like `List.of`.
5. **Why small steps?** Easy to **verify and revert**.

**▸ 🟡 L3**

1. **When do you replace a conditional with polymorphism?** When the **same switch appears in several places**.
2. **What's a golden master?** **Recorded current outputs** used as expected results.
3. **How do you extract a strategy?** **Extract each branch, introduce the interface, replace the conditional with a lookup.**
4. **When would you remove a pattern?** When it has **one implementation and no variation**.
5. **Why separate refactoring from feature commits?** **Reviewability and safe reverts.**

**▸ 🔴 L4–L5**

1. **How do you replace a critical component in production?** **Parallel change: expand, shadow, migrate by percentage, contract; kill switch.**
2. **Polymorphism vs sealed + switch?** **Types change often → polymorphism; operations change often → sealed + switch.**
3. **How do you refactor a 5,000-line class nobody understands?** **Characterization tests from real inputs, hotspot first, extract by responsibility, Mikado.**
4. **What can go wrong with shadow traffic?** **Duplicate side effects, extra load, data exposure in logs.**
5. **How do you refactor across 200 repositories?** **Automated recipes (OpenRewrite), staged rollout, CI verification.**

**▸ 💻 Hands-on**

**Task:** Add a "Diwali 15% on sweets" rule to `DiscountRulesRefactorDemo` without editing any existing rule.

> ✅ **Solution**  
> ```java
> record FestiveSweets(int sweetsPaise) implements DiscountRule {
>     public String name() { return "Diwali 15% on sweets"; }
>     public long discount(Cart c) { return sweetsPaise * 15L / 100; }
> }
> // Cart would carry the sweets subtotal; in practice the rule reads cart lines by category.
> var engine = new DiscountEngine(List.of(new FirstOrder(), new MemberFivePercent(),
>         new DairyBundle(), new Fresh10Coupon(), new FestiveSweets(40_000)), 50);
> // existing rules and the engine are unchanged: Open/Closed
> ```

## 19. Practice

- [ ] Take the `feeBefore` method from the Anti-Patterns page and refactor it in four commits, each checked against a golden master.
    > 💡 **Hint**  
    > Delete the dead branch first; then guard clauses.

- [ ] Replace `ExtractStrategyDemo`'s map with Spring's `Map<String, AssignmentStrategy>` injection.
    > 💡 **Hint**  
    > Bean names become keys: `@Component("BLR")`.

- [ ] Find an interface with exactly one implementation in a project and decide whether to inline it.
    > 💡 **Hint**  
    > Is it a module boundary, mocked in tests, or an extension point? If none, inline.

- [ ] Add latency measurement for both paths to `ParallelChangeDemo`'s experiment.
    > 💡 **Hint**  
    > `System.nanoTime()` around each supplier; report P50/P99.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Pin behaviour (tests, golden master), then tiny steps; revert red steps.
> - Repeated switch → polymorphism or sealed types.
> - Confusing constructors → named creation methods.
> - Algorithm variants → Extract Strategy, selection as data.
> - Pattern with no variation → inline it.
> - Risky replacement → parallel change: expand, shadow, migrate, contract.
>
> ```java
> T run(Supplier<T> control, Supplier<T> candidate) {
>     T expected = control.get();
>     try { if (!expected.equals(candidate.get())) mismatches++; } catch (RuntimeException e) { errors++; }
>     return expected;   // customers always get the old, proven answer
> }
> ```

## 21. Further Reading

- Joshua Kerievsky, *Refactoring to Patterns* (2004).
- Martin Fowler, *Refactoring* (2nd ed., 2018), and the [online catalog](https://refactoring.com/catalog/).
- Michael Feathers, *Working Effectively with Legacy Code* (2004).
- Danilo Sato, ["ParallelChange"](https://martinfowler.com/bliki/ParallelChange.html); Martin Fowler, ["BranchByAbstraction"](https://martinfowler.com/bliki/BranchByAbstraction.html).
- [OpenRewrite](https://docs.openrewrite.org/) for automated large-scale refactoring.

---

[← Previous: Anti-Patterns and Code Smells](anti-patterns-and-code-smells.md) · [Next: Performance Cost of Patterns →](performance-of-patterns.md)
