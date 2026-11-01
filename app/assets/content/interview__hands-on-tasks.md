# Hands-on Tasks

> 📌 **TL;DR**  
> Five take-home and live-coding tasks in the FreshCart domain, from L2 to L4, each with requirements, hints and a complete, runnable solution with its real output. Set a timer, write your own version and tests first, then compare.

**How interviewers grade these.** Working code matters, but so do: the names you choose, whether the variation point is easy to extend, tests for edge cases, and what you say about trade-offs. A short "what I'd do with more time" note at the end is always welcome.

All solutions live in `examples/interview/hands-on-tasks/` and run with `java <File>.java`; `examples/run-all.sh` checks their output.

## Task 1: Coupon engine (L2, 45 minutes)

FreshCart has three coupons. Given a cart, return the single best coupon and how much it saves.

| Code | Eligible when | Discount |
|------|---------------|----------|
| WELCOME100 | First order and basket at least Rs 300 | Rs 100 off (never more than the basket) |
| DAIRY15 | Cart has dairy items and basket at least Rs 200 | 15% off dairy items |
| BIG5 | Basket at least Rs 1000 | 5% off everything |

**Requirements:** adding a coupon must not change the selection code; eligibility rules must be combinable and testable on their own; ties go to the coupon listed first.

> 💡 **Hints**  
> Eligibility is a `Predicate<Cart>` (Specification); the discount is a small Strategy. A coupon is a record of code, predicate and discount.

> ✅ **Solution**  
> **CouponEngineDemo.java**

```java
    import java.util.List;
    import java.util.function.Predicate;

    /** Task 1 solution: coupon rules as composable specifications, each coupon a discount strategy. */
    public class CouponEngineDemo {

        record Item(String sku, String category, long paise, int qty) { long total() { return paise * qty; } }
        record Cart(String customer, boolean firstOrder, List<Item> items) {
            long subtotal() { return items.stream().mapToLong(Item::total).sum(); }
        }

        interface Discount { long apply(Cart cart); }
        record Coupon(String code, Predicate<Cart> eligibility, Discount discount) {}

        static Predicate<Cart> minBasket(long paise) { return c -> c.subtotal() >= paise; }
        static Predicate<Cart> hasCategory(String cat) { return c -> c.items().stream().anyMatch(i -> i.category().equals(cat)); }
        static final Predicate<Cart> FIRST_ORDER = Cart::firstOrder;

        static Discount percentOff(int pct) { return c -> c.subtotal() * pct / 100; }
        static Discount percentOffCategory(String cat, int pct) {
            return c -> c.items().stream().filter(i -> i.category().equals(cat)).mapToLong(Item::total).sum() * pct / 100;
        }
        static Discount flat(long paise, long cap) { return c -> Math.min(paise, Math.min(cap, c.subtotal())); }

        static final List<Coupon> COUPONS = List.of(
                new Coupon("WELCOME100", FIRST_ORDER.and(minBasket(30000)), flat(10000, 10000)),
                new Coupon("DAIRY15", hasCategory("dairy").and(minBasket(20000)), percentOffCategory("dairy", 15)),
                new Coupon("BIG5", minBasket(100000), percentOff(5)));

        /** Picks the best eligible coupon; ties go to the coupon listed first. */
        static String best(Cart cart) {
            Coupon best = null;
            long bestValue = 0;
            for (Coupon c : COUPONS) {
                if (!c.eligibility().test(cart)) continue;
                long value = c.discount().apply(cart);
                if (value > bestValue) { best = c; bestValue = value; }
            }
            return best == null ? "no coupon applies" : best.code() + " saves Rs " + bestValue / 100.0;
        }

        public static void main(String[] args) {
            Cart small = new Cart("asha", false, List.of(new Item("MILK-1L", "dairy", 6800, 2)));
            Cart firstOrder = new Cart("dev", true, List.of(new Item("MILK-1L", "dairy", 6800, 3), new Item("RICE-5KG", "staples", 64000, 1)));
            Cart dairyHeavy = new Cart("chen", false, List.of(new Item("PANEER-200G", "dairy", 9000, 4), new Item("CURD-500G", "dairy", 4500, 2)));
            Cart big = new Cart("bilal", false, List.of(new Item("OIL-5L", "staples", 90000, 1), new Item("MILK-1L", "dairy", 6800, 5)));
            for (Cart c : List.of(small, firstOrder, dairyHeavy, big)) System.out.println(c.customer() + " (Rs " + c.subtotal() / 100.0 + "): " + best(c));
        }
    }
    ```

    **Output:**

    ```text
    asha (Rs 136.0): no coupon applies
    dev (Rs 844.0): WELCOME100 saves Rs 100.0
    chen (Rs 450.0): DAIRY15 saves Rs 67.5
    bilal (Rs 1240.0): BIG5 saves Rs 62.0
    ```

    **Discussion points:** money in paise as `long`, coupons as data so they could come from configuration, and why "best coupon" logic belongs in one place. Patterns: [Specification](../l2-practitioner/specification.md), [Strategy](../l1-foundations/strategy.md).

## Task 2: Undoable cart (L2, 45 minutes)

The mobile app needs undo and redo for cart edits: set a quantity (0 removes the line) and clear the cart.

**Requirements:** undo restores exactly the previous state, including after "clear cart"; redo works until a new edit is made; the cart class must not know about undo.

> 💡 **Hints**  
> Each edit is a Command that records what it needs to reverse itself when executed. Two stacks: undo and redo. A new command clears the redo stack.

> ✅ **Solution**  
> **CartUndoDemo.java**

```java
    import java.util.ArrayDeque;
    import java.util.Deque;
    import java.util.Map;
    import java.util.TreeMap;

    /** Task 2 solution: cart edits as commands with undo and redo; each command remembers what it needs to reverse itself. */
    public class CartUndoDemo {

        static final class Cart {
            final Map<String, Integer> lines = new TreeMap<>();
            int qty(String sku) { return lines.getOrDefault(sku, 0); }
            void set(String sku, int qty) { if (qty == 0) lines.remove(sku); else lines.put(sku, qty); }
            @Override public String toString() { return lines.toString(); }
        }

        interface Command { void execute(Cart cart); void undo(Cart cart); String label(); }

        record SetQuantity(String sku, int qty, int[] previous) implements Command {
            SetQuantity(String sku, int qty) { this(sku, qty, new int[1]); }
            public void execute(Cart cart) { previous[0] = cart.qty(sku); cart.set(sku, qty); }
            public void undo(Cart cart) { cart.set(sku, previous[0]); }
            public String label() { return "set " + sku + " to " + qty; }
        }

        record ClearCart(Map<String, Integer> snapshot) implements Command {
            ClearCart() { this(new TreeMap<>()); }
            public void execute(Cart cart) { snapshot.clear(); snapshot.putAll(cart.lines); cart.lines.clear(); }
            public void undo(Cart cart) { cart.lines.putAll(snapshot); }
            public String label() { return "clear cart"; }
        }

        static final class Editor {
            final Cart cart = new Cart();
            final Deque<Command> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
            void run(Command c) { c.execute(cart); undo.push(c); redo.clear(); System.out.println(c.label() + " -> " + cart); }
            void undo() { if (undo.isEmpty()) return; Command c = undo.pop(); c.undo(cart); redo.push(c); System.out.println("undo " + c.label() + " -> " + cart); }
            void redo() { if (redo.isEmpty()) { System.out.println("nothing to redo (a new edit cleared the redo stack)"); return; } Command c = redo.pop(); c.execute(cart); undo.push(c); System.out.println("redo " + c.label() + " -> " + cart); }
        }

        public static void main(String[] args) {
            Editor e = new Editor();
            e.run(new SetQuantity("MILK-1L", 2));
            e.run(new SetQuantity("CURD-500G", 1));
            e.run(new SetQuantity("MILK-1L", 5));
            e.run(new ClearCart());
            e.undo();
            e.undo();
            e.redo();
            e.run(new SetQuantity("BREAD", 1));
            e.redo();
        }
    }
    ```

    **Output:**

    ```text
    set MILK-1L to 2 -> {MILK-1L=2}
    set CURD-500G to 1 -> {CURD-500G=1, MILK-1L=2}
    set MILK-1L to 5 -> {CURD-500G=1, MILK-1L=5}
    clear cart -> {}
    undo clear cart -> {CURD-500G=1, MILK-1L=5}
    undo set MILK-1L to 5 -> {CURD-500G=1, MILK-1L=2}
    redo set MILK-1L to 5 -> {CURD-500G=1, MILK-1L=5}
    set BREAD to 1 -> {BREAD=1, CURD-500G=1, MILK-1L=5}
    nothing to redo (a new edit cleared the redo stack)
    ```

    **Discussion points:** commands store only the previous value (cheap) except "clear", which stores a snapshot (Memento); limits on history size; how this maps to a ViewModel on mobile. Patterns: [Command](../l2-practitioner/command.md), [Memento](../l2-practitioner/memento.md).

## Task 3: Refactor the fee calculator (L3, live, 40 minutes)

You are given this method and asked to make it easy to add new customer types and cities, without changing behaviour:

```java
static long legacyFee(String customerType, String city, long basketPaise) {
    switch (customerType) {
        case "MEMBER":
            return 0;
        case "REGULAR":
            if (city.equals("Pune")) return basketPaise >= 60000 ? 0 : 3900;
            return basketPaise >= 50000 ? 0 : 2900;
        case "CORPORATE":
            if (city.equals("Pune")) return 1500;
            return 1500;
        default:
            throw new IllegalArgumentException(customerType);
    }
}
```

**Requirements:** prove the new code matches the old for boundary values before deleting it; remove the duplicated corporate branch; make invalid customer types impossible to pass.

> 💡 **Hints**  
> Start with a characterisation test over customer types × cities × boundary baskets. Then introduce an enum for customer types and a small set of fee rules (free, free above a threshold, flat).

> ✅ **Solution**  
> **FeeRefactorDemo.java**

```java
    import java.util.List;

    /**
     * Task 3 solution: the legacy fee calculator (a switch on strings with duplicated rules) refactored to a sealed set
     * of fee rules chosen by a factory. A characterisation check proves both versions agree on every sample before the
     * legacy code is deleted.
     */
    public class FeeRefactorDemo {

        // ---- before: the code given in the task ----
        static long legacyFee(String customerType, String city, long basketPaise) {
            switch (customerType) {
                case "MEMBER":
                    return 0;
                case "REGULAR":
                    if (city.equals("Pune")) return basketPaise >= 60000 ? 0 : 3900;
                    return basketPaise >= 50000 ? 0 : 2900;
                case "CORPORATE":
                    if (city.equals("Pune")) return 1500;
                    return 1500;
                default:
                    throw new IllegalArgumentException(customerType);
            }
        }

        // ---- after ----
        sealed interface FeeRule permits Free, FreeAbove, Flat {
            long feePaise(long basketPaise);
        }
        record Free() implements FeeRule { public long feePaise(long b) { return 0; } }
        record FreeAbove(long thresholdPaise, long feePaise) implements FeeRule {
            public long feePaise(long b) { return b >= thresholdPaise ? 0 : feePaise; }
        }
        record Flat(long feePaise) implements FeeRule { public long feePaise(long b) { return feePaise; } }

        enum CustomerType { MEMBER, REGULAR, CORPORATE }

        static FeeRule ruleFor(CustomerType type, String city) {
            return switch (type) {
                case MEMBER -> new Free();
                case REGULAR -> city.equals("Pune") ? new FreeAbove(60000, 3900) : new FreeAbove(50000, 2900);
                case CORPORATE -> new Flat(1500);
            };
        }

        public static void main(String[] args) {
            int checked = 0, mismatches = 0;
            for (CustomerType type : CustomerType.values()) {
                for (String city : List.of("Bengaluru", "Pune")) {
                    for (long basket : new long[] {0, 49999, 50000, 59999, 60000, 150000}) {
                        long before = legacyFee(type.name(), city, basket);
                        long after = ruleFor(type, city).feePaise(basket);
                        checked++;
                        if (before != after) { mismatches++; System.out.println("MISMATCH " + type + " " + city + " " + basket); }
                    }
                }
            }
            System.out.println("characterisation: " + checked + " cases, " + mismatches + " mismatches");
            System.out.println("REGULAR in Pune, Rs 550 basket: rule " + ruleFor(CustomerType.REGULAR, "Pune") + ", fee " + ruleFor(CustomerType.REGULAR, "Pune").feePaise(55000) + " paise");
            System.out.println("CORPORATE anywhere: " + ruleFor(CustomerType.CORPORATE, "Bengaluru"));
        }
    }
    ```

    **Output:**

    ```text
    characterisation: 36 cases, 0 mismatches
    REGULAR in Pune, Rs 550 basket: rule FreeAbove[thresholdPaise=60000, feePaise=3900], fee 3900 paise
    CORPORATE anywhere: Flat[feePaise=1500]
    ```

    **Discussion points:** characterisation before refactoring, sealed types so a new rule kind is a compile-time decision, and why a rule table in configuration could be the next step. Pages: [Refactoring to Patterns](../l3-professional/refactoring-to-patterns.md), [Patterns in Modern Java](../l3-professional/patterns-in-modern-java.md).

## Task 4: Retrying partner client (L4, take-home, 90 minutes)

Write a client for QuickDrop's booking API. The API sometimes returns 503 under load; 400 means the request is wrong.

**Requirements:** retry only transient failures, at most 4 attempts, with exponential backoff and full jitter capped at 2 seconds; send the same idempotency key on every attempt so the partner never books twice; tests must not sleep.

> 💡 **Hints**  
> Use a virtual clock and an injectable random source. Full jitter means sleeping a random time between 0 and the current backoff ceiling.

> ✅ **Solution**  
> **RetryingPartnerClientDemo.java**

```java
    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;

    /**
     * Task 4 solution: a delivery-booking client that retries transient failures with exponential backoff and full
     * jitter, never retries client errors, and sends the same idempotency key on every attempt so the partner books once.
     * A virtual clock and a seeded jitter source keep the output deterministic.
     */
    public class RetryingPartnerClientDemo {

        static final class TransientException extends RuntimeException { TransientException(String m) { super(m); } }
        static final class ClientErrorException extends RuntimeException { ClientErrorException(String m) { super(m); } }

        /** Fake partner: fails the first N calls per key with 503, books at most once per idempotency key. */
        static final class FakeQuickDrop {
            final Map<String, String> bookings = new HashMap<>();
            final Map<String, Integer> calls = new HashMap<>();
            final int failuresBeforeSuccess;
            FakeQuickDrop(int failuresBeforeSuccess) { this.failuresBeforeSuccess = failuresBeforeSuccess; }

            String book(String idempotencyKey, String orderId) {
                int n = calls.merge(idempotencyKey, 1, Integer::sum);
                if (orderId.isBlank()) throw new ClientErrorException("400 missing order id");
                if (n <= failuresBeforeSuccess) throw new TransientException("503 busy");
                return bookings.computeIfAbsent(idempotencyKey, k -> "QD-" + orderId);
            }
        }

        static final class RetryingClient {
            final FakeQuickDrop partner;
            final int maxAttempts = 4;
            final long baseMillis = 100, capMillis = 2000;
            long virtualNow = 0;
            long seed = 42;
            final List<String> log = new ArrayList<>();

            RetryingClient(FakeQuickDrop partner) { this.partner = partner; }

            long jitter(long bound) {                       // small deterministic LCG instead of a real random source
                seed = (seed * 6364136223846793005L + 1442695040888963407L);
                return Math.floorMod(seed >>> 33, bound + 1);
            }

            String book(String orderId) {
                String key = "book-" + orderId;              // stable across attempts
                for (int attempt = 1; ; attempt++) {
                    try {
                        String id = partner.book(key, orderId);
                        log.add("t=" + virtualNow + "ms attempt " + attempt + " ok " + id);
                        return id;
                    } catch (TransientException e) {
                        if (attempt == maxAttempts) { log.add("t=" + virtualNow + "ms attempt " + attempt + " " + e.getMessage() + ", giving up"); throw e; }
                        long ceiling = Math.min(capMillis, baseMillis << (attempt - 1));
                        long sleep = jitter(ceiling);
                        log.add("t=" + virtualNow + "ms attempt " + attempt + " " + e.getMessage() + ", retry in " + sleep + "ms (max " + ceiling + ")");
                        virtualNow += sleep;
                    } catch (ClientErrorException e) {
                        log.add("t=" + virtualNow + "ms attempt " + attempt + " " + e.getMessage() + ", not retried");
                        throw e;
                    }
                }
            }
        }

        public static void main(String[] args) {
            for (int failures : new int[] {2, 5}) {
                RetryingClient client = new RetryingClient(new FakeQuickDrop(failures));
                System.out.println("partner fails " + failures + " times:");
                try { client.book("FC-1001"); } catch (RuntimeException ignored) { }
                client.log.forEach(l -> System.out.println("  " + l));
                System.out.println("  bookings at partner: " + client.partner.bookings.size());
            }
            RetryingClient client = new RetryingClient(new FakeQuickDrop(0));
            try { client.book(" "); } catch (RuntimeException ignored) { }
            System.out.println("bad request:");
            client.log.forEach(l -> System.out.println("  " + l));
        }
    }
    ```

    **Output:**

    ```text
    partner fails 2 times:
      t=0ms attempt 1 503 busy, retry in 100ms (max 100)
      t=100ms attempt 2 503 busy, retry in 176ms (max 200)
      t=276ms attempt 3 ok QD-FC-1001
      bookings at partner: 1
    partner fails 5 times:
      t=0ms attempt 1 503 busy, retry in 100ms (max 100)
      t=100ms attempt 2 503 busy, retry in 176ms (max 200)
      t=276ms attempt 3 503 busy, retry in 257ms (max 400)
      t=533ms attempt 4 503 busy, giving up
      bookings at partner: 0
    bad request:
      t=0ms attempt 1 400 missing order id, not retried
    ```

    **Discussion points:** why jitter prevents synchronised retry waves, why the key is derived from the order and not generated per attempt, retry budgets across a fleet, and where Resilience4j would replace this. Pages: [Resilience Patterns](../l4-senior/resilience-patterns.md), [API Design Patterns](../l4-senior/api-design-patterns.md).

## Task 5: Single-flight price cache (L3 to L4, live, 45 minutes)

Hot product prices are requested by hundreds of threads at once. When a price is missing from the cache, only one load may hit the pricing database; the others wait for its result. A failed load must not be cached.

**Requirements:** thread-safe without a global lock; no load inside a map lock (loads can be slow); demonstrate with 50 concurrent requests.

> 💡 **Hints**  
> Store a `CompletableFuture` per key with `putIfAbsent`. Whoever inserted it loads; everyone else joins. On failure, remove that exact future.

> ✅ **Solution**  
> **SingleFlightCacheDemo.java**

```java
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Set;
    import java.util.concurrent.CompletableFuture;
    import java.util.concurrent.ConcurrentHashMap;
    import java.util.concurrent.CountDownLatch;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.Future;
    import java.util.concurrent.atomic.AtomicInteger;
    import java.util.function.Function;

    /**
     * Task 5 solution: a thread-safe price cache with single-flight loading. Concurrent misses for the same key share one
     * in-flight load (a CompletableFuture stored with computeIfAbsent); failed loads are removed so the next caller retries.
     */
    public class SingleFlightCacheDemo {

        static final class SingleFlightCache<K, V> {
            private final ConcurrentHashMap<K, CompletableFuture<V>> entries = new ConcurrentHashMap<>();
            private final Function<K, V> loader;
            SingleFlightCache(Function<K, V> loader) { this.loader = loader; }

            V get(K key) {
                CompletableFuture<V> created = new CompletableFuture<>();
                CompletableFuture<V> existing = entries.putIfAbsent(key, created);
                if (existing != null) return existing.join();          // someone else is loading or has loaded
                try {
                    V value = loader.apply(key);                         // load outside any map lock
                    created.complete(value);
                    return value;
                } catch (RuntimeException e) {
                    entries.remove(key, created);                        // let the next caller retry
                    created.completeExceptionally(e);
                    throw e;
                }
            }
        }

        public static void main(String[] args) throws Exception {
            AtomicInteger loads = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            SingleFlightCache<String, Long> cache = new SingleFlightCache<>(sku -> {
                loads.incrementAndGet();
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return 6800L;
            });

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Long>> results = new ArrayList<>();
                for (int i = 0; i < 50; i++) results.add(pool.submit(() -> { start.await(); return cache.get("MILK-1L"); }));
                start.countDown();
                Set<Long> values = new java.util.TreeSet<>();
                for (Future<Long> f : results) values.add(f.get());
                System.out.println("50 concurrent requests for MILK-1L: loader called " + loads.get() + " time(s), values " + values);
            }

            AtomicInteger attempts = new AtomicInteger();
            SingleFlightCache<String, Long> flaky = new SingleFlightCache<>(sku -> {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("pricing DB timeout");
                return 4500L;
            });
            try { flaky.get("CURD-500G"); } catch (IllegalStateException e) { System.out.println("first load failed: " + e.getMessage()); }
            System.out.println("second call retries and gets " + flaky.get("CURD-500G") + " paise (loader attempts " + attempts.get() + ")");
        }
    }
    ```

    **Output:**

    ```text
    50 concurrent requests for MILK-1L: loader called 1 time(s), values [6800]
    first load failed: pricing DB timeout
    second call retries and gets 4500 paise (loader attempts 2)
    ```

    **Discussion points:** why `computeIfAbsent` with a slow loader blocks other keys in the same bin, adding expiry and size bounds (or using Caffeine's `AsyncLoadingCache`, which does this for you), and virtual threads for the demo. Pages: [Caching Patterns](../l4-senior/caching-patterns.md), [Concurrency Patterns](../l3-professional/concurrency-patterns.md).

## More practice

- The **Hands-on** tab in every topic page's Interview Corner has a smaller task with a solution.
- The [FreshCart capstone](../projects/capstone.md) milestones make good 2 to 4 hour take-homes.
