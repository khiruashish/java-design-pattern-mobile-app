---
tags: [interview]
---
# Hands-on Tasks

!!! abstract "TL;DR"
    Five take-home and live-coding tasks in the FreshCart domain, from L2 to L4, each with requirements, hints and a complete, runnable solution with its real output. Set a timer, write your own version and tests first, then compare.

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

??? tip "Hints"
    Eligibility is a `Predicate<Cart>` (Specification); the discount is a small Strategy. A coupon is a record of code, predicate and discount.

??? success "Solution"
    ```java title="CouponEngineDemo.java"
    --8<-- "examples/interview/hands-on-tasks/CouponEngineDemo.java"
    ```

    **Output:**

    ```text
    --8<-- "examples/interview/hands-on-tasks/CouponEngineDemo.expected.txt"
    ```

    **Discussion points:** money in paise as `long`, coupons as data so they could come from configuration, and why "best coupon" logic belongs in one place. Patterns: [Specification](../l2-practitioner/specification.md), [Strategy](../l1-foundations/strategy.md).

## Task 2: Undoable cart (L2, 45 minutes)

The mobile app needs undo and redo for cart edits: set a quantity (0 removes the line) and clear the cart.

**Requirements:** undo restores exactly the previous state, including after "clear cart"; redo works until a new edit is made; the cart class must not know about undo.

??? tip "Hints"
    Each edit is a Command that records what it needs to reverse itself when executed. Two stacks: undo and redo. A new command clears the redo stack.

??? success "Solution"
    ```java title="CartUndoDemo.java"
    --8<-- "examples/interview/hands-on-tasks/CartUndoDemo.java"
    ```

    **Output:**

    ```text
    --8<-- "examples/interview/hands-on-tasks/CartUndoDemo.expected.txt"
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

??? tip "Hints"
    Start with a characterisation test over customer types × cities × boundary baskets. Then introduce an enum for customer types and a small set of fee rules (free, free above a threshold, flat).

??? success "Solution"
    ```java title="FeeRefactorDemo.java"
    --8<-- "examples/interview/hands-on-tasks/FeeRefactorDemo.java"
    ```

    **Output:**

    ```text
    --8<-- "examples/interview/hands-on-tasks/FeeRefactorDemo.expected.txt"
    ```

    **Discussion points:** characterisation before refactoring, sealed types so a new rule kind is a compile-time decision, and why a rule table in configuration could be the next step. Pages: [Refactoring to Patterns](../l3-professional/refactoring-to-patterns.md), [Patterns in Modern Java](../l3-professional/patterns-in-modern-java.md).

## Task 4: Retrying partner client (L4, take-home, 90 minutes)

Write a client for QuickDrop's booking API. The API sometimes returns 503 under load; 400 means the request is wrong.

**Requirements:** retry only transient failures, at most 4 attempts, with exponential backoff and full jitter capped at 2 seconds; send the same idempotency key on every attempt so the partner never books twice; tests must not sleep.

??? tip "Hints"
    Use a virtual clock and an injectable random source. Full jitter means sleeping a random time between 0 and the current backoff ceiling.

??? success "Solution"
    ```java title="RetryingPartnerClientDemo.java"
    --8<-- "examples/interview/hands-on-tasks/RetryingPartnerClientDemo.java"
    ```

    **Output:**

    ```text
    --8<-- "examples/interview/hands-on-tasks/RetryingPartnerClientDemo.expected.txt"
    ```

    **Discussion points:** why jitter prevents synchronised retry waves, why the key is derived from the order and not generated per attempt, retry budgets across a fleet, and where Resilience4j would replace this. Pages: [Resilience Patterns](../l4-senior/resilience-patterns.md), [API Design Patterns](../l4-senior/api-design-patterns.md).

## Task 5: Single-flight price cache (L3 to L4, live, 45 minutes)

Hot product prices are requested by hundreds of threads at once. When a price is missing from the cache, only one load may hit the pricing database; the others wait for its result. A failed load must not be cached.

**Requirements:** thread-safe without a global lock; no load inside a map lock (loads can be slow); demonstrate with 50 concurrent requests.

??? tip "Hints"
    Store a `CompletableFuture` per key with `putIfAbsent`. Whoever inserted it loads; everyone else joins. On failure, remove that exact future.

??? success "Solution"
    ```java title="SingleFlightCacheDemo.java"
    --8<-- "examples/interview/hands-on-tasks/SingleFlightCacheDemo.java"
    ```

    **Output:**

    ```text
    --8<-- "examples/interview/hands-on-tasks/SingleFlightCacheDemo.expected.txt"
    ```

    **Discussion points:** why `computeIfAbsent` with a slow loader blocks other keys in the same bin, adding expiry and size bounds (or using Caffeine's `AsyncLoadingCache`, which does this for you), and virtual threads for the demo. Pages: [Caching Patterns](../l4-senior/caching-patterns.md), [Concurrency Patterns](../l3-professional/concurrency-patterns.md).

## More practice

- The **Hands-on** tab in every topic page's Interview Corner has a smaller task with a solution.
- The [FreshCart capstone](../projects/capstone.md) milestones make good 2 to 4 hour take-homes.
