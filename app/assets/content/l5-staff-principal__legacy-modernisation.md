# Legacy Modernisation Patterns

L5 🔴 Staff / Principal · ⏱ 35 min read · Prerequisites: [Microservice Patterns](../l4-senior/microservice-patterns.md), [DDD Strategic Patterns](../l4-senior/ddd-strategic.md)

> 📌 **TL;DR**  
> Big-bang rewrites of working systems usually run late and often fail; incremental modernisation keeps delivering while the old system shrinks. The **strangler fig** moves one capability at a time and is only finished when the new side owns the data and the legacy path is deleted. **Branch by abstraction** replaces a component on trunk behind an interface, with no long-lived branch. A **parallel run** executes old and new on real inputs, serves the old result and classifies differences until the new one is trusted. An **anti-corruption layer** keeps legacy formats and quirks out of new code. **Feature toggles** control rollout per segment and give an instant way back. Combine them with idempotent data migration (copy absolute values, backfill, reconcile) and automatic rollback.

## 1. What It Is

**Plain English.** FreshCart still runs parts of its business on a 2012 monolith and a billing mainframe. These patterns replace them piece by piece while customers keep ordering: route some traffic to new code, compare results, translate old data formats, and keep a switch to go back.

**Definition.**

| Pattern | Purpose |
|---------|---------|
| Strangler fig | Replace a system capability by capability behind a router |
| Branch by abstraction | Swap an internal component on trunk behind an abstraction |
| Parallel run | Run old and new side by side on real inputs and compare |
| Anti-corruption layer | Translate legacy models and formats at the boundary |
| Feature toggles | Control who gets new behaviour, without a deploy |

## 2. Why It Exists

Legacy systems hold years of business rules nobody fully remembers, and they keep earning money. A rewrite has to rediscover all of those rules while the business keeps changing, so it chases a moving target and delivers nothing until the end. Incremental approaches (Fowler's Strangler Fig, 2004; Paul Hammant's Branch by Abstraction, 2007; parallel runs from banking and mainframe migrations) deliver value early, reduce risk per step, and allow stopping halfway with a better system than before.

## 3. Mental Model / Analogy

**Analogy.** Renovating a hotel while it stays open. Floors are closed and rebuilt one at a time (strangler fig). Plumbing is swapped behind the wall panels without guests noticing (branch by abstraction). For the first week, the new kitchen cooks every dish alongside the old one, and only the old kitchen's food is served until the chefs agree they match (parallel run). The new booking system reads the old paper ledger through a translator (ACL). The manager can reopen the old wing with one call if something goes wrong (toggle).

**Technical model.** A routing layer and toggles decide which implementation handles each request; comparison and reconciliation provide evidence; data ownership moves explicitly; legacy code is deleted as the last step of each slice.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Strangler fig in practice

**Description.** In practice, a strangler migration is a portfolio of capabilities at different stages: not started, canary, reads migrated, data ownership moved, legacy deleted. Track it by traffic share **and** by deleted legacy code; the classic failure is a migration that stalls at "mostly on the new system" with both systems running forever.

**Advantages / features.** Visible progress; risk limited to one capability; the business can reprioritise between slices.

**StranglerInPracticeDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strangler fig in practice: migration is tracked per capability, by traffic share and by who owns the data.
 * A capability is only "strangled" when the new service owns writes and the legacy code path is deleted.
 */
public class StranglerInPracticeDemo {

    enum DataOwner { LEGACY, NEW_SERVICE }
    record Capability(String name, int newTrafficPercent, DataOwner writes, boolean legacyCodeDeleted, int dailyRequests) {
        String stage() {
            if (legacyCodeDeleted) return "done";
            if (writes == DataOwner.NEW_SERVICE) return "new service owns data; delete legacy path";
            if (newTrafficPercent == 100) return "reads migrated; legacy still owns writes (CDC syncs to new)";
            if (newTrafficPercent > 0) return "canary at " + newTrafficPercent + "%";
            return "not started";
        }
    }

    public static void main(String[] args) {
        Map<String, Capability> plan = new LinkedHashMap<>();
        plan.put("catalog browse", new Capability("catalog browse", 100, DataOwner.NEW_SERVICE, true, 900_000));
        plan.put("delivery slots", new Capability("delivery slots", 100, DataOwner.NEW_SERVICE, false, 300_000));
        plan.put("order history", new Capability("order history", 100, DataOwner.LEGACY, false, 120_000));
        plan.put("checkout", new Capability("checkout", 5, DataOwner.LEGACY, false, 80_000));
        plan.put("invoicing", new Capability("invoicing", 0, DataOwner.LEGACY, false, 20_000));
        plan.values().forEach(c -> System.out.printf("%-15s %s%n", c.name(), c.stage()));

        long total = plan.values().stream().mapToLong(Capability::dailyRequests).sum();
        long onNew = plan.values().stream().mapToLong(c -> c.dailyRequests() * c.newTrafficPercent() / 100).sum();
        long deleted = plan.values().stream().filter(Capability::legacyCodeDeleted).count();
        System.out.printf("traffic on new services: %d%%%n", onNew * 100 / total);
        System.out.println("capabilities fully strangled: " + deleted + " of " + plan.size());
    }
}
```

1. Each capability's stage follows from traffic share, data ownership and deletion.
2. Traffic share looks great early, because the busiest capabilities are often the easiest.
3. The honest metric: capabilities whose legacy code is gone.

**Run it:** `java StranglerInPracticeDemo.java`

```text
catalog browse  done
delivery slots  new service owns data; delete legacy path
order history   reads migrated; legacy still owns writes (CDC syncs to new)
checkout        canary at 5%
invoicing       not started
traffic on new services: 93%
capabilities fully strangled: 1 of 5
```

| Stage | Exit criterion |
|-------|----------------|
| Canary | Error rate and latency match legacy at small percentage |
| Reads migrated | 100% reads; data synced from legacy (CDC) |
| Data ownership moved | New service is the writer; legacy reads via sync back or stops |
| Legacy deleted | Code, tables and jobs removed; route removed |

**Use** for any system too large or risky to replace at once. **Avoid** starting with the most entangled core; start at the edges.
**Real-world Java:** Spring Cloud Gateway or Nginx routing, Debezium for data sync, feature flags for canaries.
**Best practice:** budget the deletion step in every slice's plan. **Gotcha:** two writers for the same data during migration need a clear owner per field, or you get lost updates.

### Branch by abstraction

**Description.** To replace a component inside a codebase: introduce an abstraction in front of the old implementation, move all callers to the abstraction, build the new implementation behind it (merged to trunk continuously, switched off), switch over with a toggle, then delete the old implementation (and the abstraction if it isn't needed any more).

**Advantages / features.** No long-lived branches or painful merges; the system is releasable at every step; switch-over is reversible.

**BranchByAbstractionDemo.java**

```java
import java.util.List;

/**
 * Branch by abstraction: replace a component on trunk, in small safe steps, without a long-lived branch.
 * 1) put an abstraction in front of the old implementation, 2) move callers onto it, 3) build the new
 * implementation behind it, 4) switch with a toggle, 5) delete the old implementation and maybe the abstraction.
 */
public class BranchByAbstractionDemo {

    interface TaxCalculator { long gstPaise(String hsnCode, long pricePaise); }

    /** The legacy code, untouched: one GST rate for everything except a hard-coded list. */
    static final class LegacyTaxTable implements TaxCalculator {
        public long gstPaise(String hsn, long price) { return hsn.equals("0401") ? 0 : price * 18 / 100; }
    }

    /** The new implementation: rate slabs per HSN chapter, built behind the same abstraction. */
    static final class GstSlabCalculator implements TaxCalculator {
        public long gstPaise(String hsn, long price) {
            int rate = switch (hsn.substring(0, 2)) { case "04" -> hsn.equals("0401") ? 0 : 5; case "19" -> 18; default -> 12; };
            return price * rate / 100;
        }
    }

    static boolean useNewTax = false;
    static TaxCalculator tax() { return useNewTax ? new GstSlabCalculator() : new LegacyTaxTable(); }

    static String invoiceLine(String item, String hsn, long price) { return item + " GST Rs " + tax().gstPaise(hsn, price) / 100.0; }

    public static void main(String[] args) {
        List<String[]> items = List.of(new String[] {"Milk 1L", "0401", "6800"}, new String[] {"Paneer 200g", "0406", "9500"},
                new String[] {"Biscuits", "1905", "3000"});
        for (boolean flag : new boolean[] {false, true}) {
            useNewTax = flag;
            System.out.println("toggle " + (flag ? "on " : "off") + ": " + items.stream().map(i -> invoiceLine(i[0], i[1], Long.parseLong(i[2]))).toList());
        }
        System.out.println("both implementations lived on trunk; step 5 deletes LegacyTaxTable and the toggle");
    }
}
```

1. The abstraction is introduced first, with the legacy implementation behind it.
2. A toggle chooses the implementation at runtime.
3. Callers only know the abstraction, so they don't change when the implementation does.

**Run it:** `java BranchByAbstractionDemo.java`

```text
toggle off: [Milk 1L GST Rs 0.0, Paneer 200g GST Rs 17.1, Biscuits GST Rs 5.4]
toggle on : [Milk 1L GST Rs 0.0, Paneer 200g GST Rs 4.75, Biscuits GST Rs 5.4]
both implementations lived on trunk; step 5 deletes LegacyTaxTable and the toggle
```

| Step | Releasable? |
|------|-------------|
| 1. Add abstraction over old code | Yes, no behaviour change |
| 2. Move callers to abstraction | Yes, one caller at a time |
| 3. Build new implementation, toggled off | Yes, dark code |
| 4. Toggle on (gradually) | Yes, reversible |
| 5. Delete old implementation and toggle | Yes |

**Use** for replacing frameworks, ORMs, tax engines or clients inside one codebase. **Avoid** leaving the toggle and the old implementation in place after the switch.
**Real-world Java:** replacing Hibernate 5 queries with jOOQ behind repository interfaces; swapping an HTTP client behind a port.
**Best practice:** run the new implementation in parallel (shadow) before toggling on. **Gotcha:** a "temporary" abstraction with two implementations for years doubles maintenance.

### Parallel run

**Description.** Feed the same real inputs to old and new implementations, serve the old result, and compare. Classify every difference: match, known and accepted (the new code fixes a legacy bug, or rounding differs within tolerance), or unexplained (investigate). Promote when unexplained differences reach zero over enough samples.

**Advantages / features.** Evidence from production traffic instead of hand-written test cases; legacy behaviour becomes a test oracle; customers are unaffected while differences are found.

**ParallelRunDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Parallel run: run legacy and new implementations on the same real inputs, serve the legacy result, and compare.
 * Differences are classified: known and accepted (e.g. the new code fixes a legacy rounding bug) vs unexplained.
 */
public class ParallelRunDemo {

    record Order(String id, long subtotalPaise, boolean subscriber) {}

    static final Function<Order, Long> LEGACY_FEE = o -> o.subtotalPaise() >= 19_900 ? 0 : (long) Math.floor(2500 * (o.subscriber() ? 0.65 : 1.0) / 100) * 100;
    static final Function<Order, Long> NEW_FEE = o -> o.subtotalPaise() > 19_900 ? 0 : Math.round(2500 * (o.subscriber() ? 0.65 : 1.0));

    public static void main(String[] args) {
        List<Order> traffic = List.of(new Order("FC-1", 15_000, false), new Order("FC-2", 25_000, false),
                new Order("FC-3", 12_000, true), new Order("FC-4", 19_900, false), new Order("FC-5", 8_000, false));
        Map<String, Integer> categories = new TreeMap<>();
        List<String> unexplained = new ArrayList<>();
        for (Order o : traffic) {
            long served = LEGACY_FEE.apply(o);
            long candidate = NEW_FEE.apply(o);
            String category;
            if (served == candidate) category = "match";
            else if (o.subscriber() && Math.abs(served - candidate) < 100) category = "accepted: legacy truncated to whole rupees";
            else { category = "UNEXPLAINED"; unexplained.add(o.id() + " legacy=" + served + " new=" + candidate); }
            categories.merge(category, 1, Integer::sum);
        }
        System.out.println("comparison: " + categories);
        unexplained.forEach(u -> System.out.println("  investigate " + u));
    }
}
```

1. The legacy result is served; the new one is only compared.
2. Known differences are classified explicitly, so they don't hide new ones.
3. Unexplained differences are what the team investigates.

**Run it:** `java ParallelRunDemo.java`

```text
comparison: {UNEXPLAINED=1, accepted: legacy truncated to whole rupees=1, match=3}
  investigate FC-4 legacy=0 new=2500
```

| Concern | Approach |
|---------|----------|
| Side effects | New path must not write, send or charge (dry-run mode) |
| Cost | Sample traffic; run asynchronously off the request path |
| Non-determinism | Normalise timestamps, IDs, ordering before comparing |
| Volume of diffs | Group by category and field |
| Exit criteria | N samples, zero unexplained, all segments covered |

**Use** when correctness matters more than speed (billing, pricing, tax). **Avoid** comparing raw outputs with timestamps or random IDs without normalising.
**Real-world Java:** GitHub's Scientist pattern (ports exist for Java), async comparison via `CompletableFuture` off the request thread.
**Best practice:** store diffs with inputs so engineers can replay them. **Gotcha:** an off-by-one at a boundary (≥ vs >) shows up only for exact boundary values; make sure traffic covers them.

### Anti-corruption layers for legacy

**Description.** Legacy systems speak in fixed-width records, cryptic codes, implied decimals, two-digit years and overloaded fields. An ACL at the boundary translates them into clean domain types, validates them, and quarantines what it can't interpret. New code never sees legacy formats.

**Advantages / features.** New models stay clean; legacy quirks are documented in one place; replacing the legacy system later changes only the ACL.

**LegacyAclDemo.java**

```java
import java.time.LocalDate;
import java.util.List;

/**
 * Anti-corruption layer for a legacy system: the old billing mainframe exports fixed-width records with padded
 * fields, implied decimals, two-digit years and Y/N flags. The ACL turns them into clean domain objects and rejects
 * records it can't interpret, instead of letting legacy quirks leak into new code.
 */
public class LegacyAclDemo {

    record Customer(String id, String name, long balancePaise, boolean codAllowed, LocalDate since) {}

    static Customer translate(String record) {
        if (record.length() != 36) throw new IllegalArgumentException("expected 36 chars, got " + record.length());
        String id = record.substring(0, 8).trim();
        String name = record.substring(8, 22).trim();
        long balance = Long.parseLong(record.substring(22, 29));            // implied 2 decimals: 0013600 = Rs 136.00
        String cod = record.substring(29, 30);
        if (!cod.equals("Y") && !cod.equals("N")) throw new IllegalArgumentException("unknown COD flag '" + cod + "'");
        int yy = Integer.parseInt(record.substring(30, 32));
        int year = yy >= 70 ? 1900 + yy : 2000 + yy;
        LocalDate since = LocalDate.of(year, Integer.parseInt(record.substring(32, 34)), Integer.parseInt(record.substring(34, 36)));
        return new Customer(id.replaceFirst("^CUST0*", "C-"), name, balance, cod.equals("Y"), since);
    }

    public static void main(String[] args) {
        for (String r : List.of("CUST0042Asha Rao      0013600Y980315", "CUST1207Vikram S      0000000N240102", "CUST0099Old record    0000500X991231")) {
            try { System.out.println(translate(r)); }
            catch (IllegalArgumentException e) { System.out.println("quarantined: " + e.getMessage()); }
        }
    }
}
```

1. Fixed-width fields are cut by position and trimmed.
2. Two-digit years need a pivot rule; this one is an explicit decision, not an accident.
3. Legacy identifiers are mapped to the new format.

**Run it:** `java LegacyAclDemo.java`

```text
Customer[id=C-42, name=Asha Rao, balancePaise=13600, codAllowed=true, since=1998-03-15]
Customer[id=C-1207, name=Vikram S, balancePaise=0, codAllowed=false, since=2024-01-02]
quarantined: unknown COD flag 'X'
```

| Legacy quirk | ACL handling |
|--------------|--------------|
| Implied decimals | Convert to explicit units (paise) |
| Two-digit years | Pivot year, documented |
| Codes (Y/N, 01/02) | Map to enums; reject unknowns |
| Padded and overloaded fields | Trim, split, validate |
| EBCDIC / legacy encodings | Decode at the edge |

**Use** wherever new code consumes legacy data or APIs. **Avoid** "temporary" direct reads of legacy tables from new services.
**Real-world Java:** Spring Batch `FixedLengthTokenizer`, BeanIO, custom mappers with property-based tests.
**Best practice:** build the ACL's test suite from real legacy samples, including the weird ones. **Gotcha:** legacy systems often encode business rules in data quirks (a special customer number range); find them before translating them away.

### Feature toggles

**Description.** Toggles switch behaviour at runtime. Types: **release** (hide unfinished work; short-lived), **ops** (kill switches; long-lived), **experiment** (A/B tests), **permission** (by user group). Percentage rollouts hash a stable key so each customer consistently gets one variant. Each toggle has an owner and an expiry.

**Advantages / features.** Deploy and release are decoupled; instant rollback; gradual exposure.

**FeatureTogglesDemo.java**

```java
import java.time.LocalDate;
import java.util.List;

/**
 * Feature toggles: release toggles hide unfinished work, ops toggles are kill switches, experiment toggles split
 * traffic, permission toggles target groups. Percentage rollouts use a stable hash so a customer doesn't flip
 * between old and new. Every toggle has an owner and an expiry, because old toggles become debt.
 */
public class FeatureTogglesDemo {

    enum Kind { RELEASE, OPS, EXPERIMENT, PERMISSION }
    record Toggle(String name, Kind kind, int rolloutPercent, boolean killed, String owner, LocalDate expires) {
        boolean enabledFor(String customerId) {
            if (killed) return false;
            int bucket = Math.floorMod((name + ":" + customerId).hashCode(), 100);
            return bucket < rolloutPercent;
        }
    }

    public static void main(String[] args) {
        Toggle newCheckout = new Toggle("new-checkout", Kind.RELEASE, 20, false, "checkout-team", LocalDate.of(2027, 1, 31));
        List<String> customers = List.of("C-1", "C-2", "C-3", "C-4", "C-5", "C-6", "C-7", "C-8", "C-9", "C-10");
        int on = 0;
        for (int i = 0; i < 10_000; i++) if (newCheckout.enabledFor("C-" + i)) on++;
        System.out.println("new-checkout at 20%: " + on + " of 10000 customers");
        System.out.println("same customers on every request: " + customers.stream().allMatch(c -> newCheckout.enabledFor(c) == newCheckout.enabledFor(c)));
        Toggle killed = new Toggle("new-checkout", Kind.RELEASE, 20, true, "checkout-team", LocalDate.of(2027, 1, 31));
        System.out.println("after kill switch: " + customers.stream().filter(killed::enabledFor).count() + " customers on new checkout");

        LocalDate today = LocalDate.of(2026, 11, 1);
        List<Toggle> all = List.of(newCheckout,
                new Toggle("paylink-fallback", Kind.OPS, 100, false, "payments-team", LocalDate.of(2099, 12, 31)),
                new Toggle("diwali-banner", Kind.EXPERIMENT, 50, false, "growth-team", LocalDate.of(2025, 11, 15)));
        all.stream().filter(t -> t.expires().isBefore(today))
                .forEach(t -> System.out.println("expired toggle: " + t.name() + " (" + t.kind() + ", owner " + t.owner() + ") since " + t.expires()));
    }
}
```

1. A kill switch overrides everything.
2. Stable hashing of toggle name and customer puts each customer in a fixed bucket.
3. Expired toggles are reported, because forgotten toggles become hidden branches.

**Run it:** `java FeatureTogglesDemo.java`

```text
new-checkout at 20%: 1993 of 10000 customers
same customers on every request: true
after kill switch: 0 customers on new checkout
expired toggle: diwali-banner (EXPERIMENT, owner growth-team) since 2025-11-15
```

| Toggle type | Lifetime | Typical owner |
|-------------|----------|---------------|
| Release | Days to weeks | Feature team |
| Ops / kill switch | Long | Service owners / SRE |
| Experiment | Weeks | Product / growth |
| Permission | Long | Product |

**Use** toggles for risky releases and migrations. **Avoid** nesting toggles or using them as permanent configuration without types and owners.
**Real-world Java:** Unleash, LaunchDarkly, FF4j, Togglz, OpenFeature Java SDK.
**Best practice:** remove release toggles within a sprint or two of full rollout. **Gotcha:** combinations of toggles create untested paths; test the states you actually ship.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** a 2009 class calls the old stock database through a static method, so nothing around it can be unit-tested.

**LegacySeamDemo.java**

```java
import java.util.function.ToIntFunction;

/**
 * Minimal: legacy code calls a static method that talks to the old stock database, so it can't be tested. A seam
 * (a parameter with the legacy call as default) makes it testable without changing behaviour for production.
 */
public class LegacySeamDemo {

    static final class LegacyStock {                      // imagine a 2009 class that opens a DB connection
        static int unitsFor(String sku) { return 7; }
    }

    static final class ReorderAdvisor {
        private final ToIntFunction<String> stock;
        ReorderAdvisor() { this(LegacyStock::unitsFor); }
        ReorderAdvisor(ToIntFunction<String> stock) { this.stock = stock; }
        String advise(String sku) { return stock.applyAsInt(sku) < 10 ? "reorder " + sku : "ok " + sku; }
    }

    public static void main(String[] args) {
        System.out.println("production: " + new ReorderAdvisor().advise("PANEER-200G"));
        System.out.println("test with stub stock=25: " + new ReorderAdvisor(sku -> 25).advise("PANEER-200G"));
    }
}
```

1. Production keeps calling the legacy static method by default.
2. Tests pass a stub through the new constructor: a seam.

**Run it:** `java LegacySeamDemo.java`

**Output:**

```text
production: reorder PANEER-200G
test with stub stock=25: ok PANEER-200G
```

**What to notice:**

- This is the first step of branch by abstraction: the seam is where a new implementation will later plug in.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** loyalty points move from the legacy database to the new Loyalty service while customers keep earning points.

**LoyaltyMigrationDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: moving loyalty points from the legacy database to the new Loyalty service without downtime.
 * Legacy stays the source of truth. Every write copies the customer's absolute balance (not the delta) to the new
 * store, backfill copies balances in batches, failed copies are retried by customer ID, and a reconciliation
 * compares both stores before reads switch over. Copying absolute values makes every step idempotent.
 */
public class LoyaltyMigrationDemo {

    static final Map<String, Integer> legacy = new TreeMap<>(), loyaltyService = new TreeMap<>();
    static boolean newServiceDown = false;
    static final List<String> retryQueue = new ArrayList<>();

    static void award(String customer, int points) {
        legacy.merge(customer, points, Integer::sum);
        if (newServiceDown) { retryQueue.add(customer); return; }
        loyaltyService.put(customer, legacy.get(customer));
    }

    static void backfill(int batchSize) {
        int copied = 0;
        for (var e : legacy.entrySet()) {
            if (copied == batchSize) break;
            if (!e.getValue().equals(loyaltyService.get(e.getKey()))) { loyaltyService.put(e.getKey(), e.getValue()); copied++; }
        }
        System.out.println("backfill batch copied " + copied + " balance(s)");
    }

    static List<String> reconcile() {
        List<String> mismatches = new ArrayList<>();
        legacy.forEach((c, p) -> { Integer q = loyaltyService.get(c); if (!p.equals(q)) mismatches.add(c + " legacy=" + p + " new=" + q); });
        return mismatches;
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 5; i++) legacy.put("C-" + i, i * 100);                 // existing balances before dual write
        award("C-2", 20);                                                            // dual write starts
        backfill(3);
        backfill(3);
        newServiceDown = true;
        award("C-6", 50);                                                            // a new customer; the copy fails
        newServiceDown = false;
        award("C-2", 5);                                                             // writes keep arriving during the migration
        System.out.println("mismatches: " + reconcile());
        retryQueue.forEach(c -> loyaltyService.put(c, legacy.get(c)));
        retryQueue.clear();
        System.out.println("after replaying the retry queue: " + reconcile() + " -> safe to switch reads");
    }
}
```

1. Writes update legacy (the source of truth) and copy the absolute balance to the new store; failures are queued by customer.
2. Backfill copies balances that differ, in batches, and can be rerun safely.
3. Reconciliation compares both stores before reads switch.

**Run it:** `java LoyaltyMigrationDemo.java`

**Output:**

```text
backfill batch copied 3 balance(s)
backfill batch copied 1 balance(s)
mismatches: [C-6 legacy=50 new=null]
after replaying the retry queue: [] -> safe to switch reads
```

**What to notice:**

- Copying absolute values made the dual write, the backfill and the retry all idempotent; copying deltas (`+20`) would have double-counted when steps overlapped.
- Reconciliation caught the missed copy for C-6 before anyone read from the new store.

### Example 3: Production-grade

**Scenario:** invoice generation moves off the billing mainframe one segment at a time. B2C goes first; B2B has different GST rules.

**InvoiceModernisationDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Production-grade: FreshCart moves invoice generation off the legacy billing system one customer segment at a
 * time. Each segment is LEGACY, SHADOW (parallel run, legacy served) or NEW. Legacy output passes through an
 * anti-corruption layer; promotion needs enough clean comparisons; in NEW mode, failures fall back to legacy and
 * too many failures roll the segment back to SHADOW automatically.
 */
public class InvoiceModernisationDemo {

    record Order(String id, String segment, long netPaise) {}
    record Invoice(String orderId, long netPaise, long gstPaise) {}
    enum Mode { LEGACY, SHADOW, NEW }

    /** Legacy output: fixed-width "INV" + order id (8) + net (9, paise) + gst (7, paise). */
    static Invoice fromLegacy(String record) {
        return new Invoice(record.substring(3, 11).trim(), Long.parseLong(record.substring(11, 20)), Long.parseLong(record.substring(20, 27)));
    }

    static final class Migrator {
        final Map<String, Mode> modes = new TreeMap<>();
        final Map<String, int[]> shadowStats = new TreeMap<>();     // [compared, unexplained]
        final Map<String, int[]> newStats = new TreeMap<>();        // [served, failures]
        final List<String> log = new ArrayList<>();
        private final Function<Order, String> legacy; private final Function<Order, Invoice> modern;
        Migrator(Function<Order, String> legacy, Function<Order, Invoice> modern) { this.legacy = legacy; this.modern = modern; }

        Invoice invoice(Order o) {
            Mode mode = modes.getOrDefault(o.segment(), Mode.LEGACY);
            if (mode == Mode.LEGACY) return fromLegacy(legacy.apply(o));
            if (mode == Mode.SHADOW) {
                Invoice served = fromLegacy(legacy.apply(o));
                int[] s = shadowStats.computeIfAbsent(o.segment(), k -> new int[2]);
                s[0]++;
                try {
                    Invoice candidate = modern.apply(o);
                    if (!candidate.equals(served) && Math.abs(candidate.gstPaise() - served.gstPaise()) > 1) {
                        s[1]++; log.add("diff " + o.id() + ": legacy " + served + " new " + candidate);
                    }
                } catch (RuntimeException e) { s[1]++; log.add("new failed in shadow for " + o.id() + ": " + e.getMessage()); }
                return served;
            }
            int[] n = newStats.computeIfAbsent(o.segment(), k -> new int[2]);
            n[0]++;
            try { return modern.apply(o); }
            catch (RuntimeException e) {
                n[1]++;
                log.add("new failed for " + o.id() + ", served legacy: " + e.getMessage());
                if (n[0] >= 4 && n[1] * 10 > n[0]) { modes.put(o.segment(), Mode.SHADOW); log.add("auto-rollback " + o.segment() + " to SHADOW"); }
                return fromLegacy(legacy.apply(o));
            }
        }

        String promote(String segment, int minSamples) {
            int[] s = shadowStats.getOrDefault(segment, new int[2]);
            if (s[0] < minSamples) return segment + " stays SHADOW: only " + s[0] + " comparisons";
            if (s[1] > 0) return segment + " stays SHADOW: " + s[1] + " unexplained difference(s)";
            modes.put(segment, Mode.NEW);
            newStats.remove(segment);
            return segment + " promoted to NEW after " + s[0] + " clean comparisons";
        }
    }

    public static void main(String[] args) {
        Function<Order, String> legacy = o -> String.format("INV%-8s%09d%07d", o.id(), o.netPaise(), o.netPaise() * 5 / 100);
        boolean[] b2bBug = {true};
        boolean[] newOutage = {false};
        Function<Order, Invoice> modern = o -> {
            if (newOutage[0]) throw new IllegalStateException("invoice-service 503");
            long gst = Math.round(o.netPaise() * 0.05);
            if (o.segment().equals("B2B") && b2bBug[0]) gst = o.netPaise() * 18 / 100;      // wrong slab for business customers
            return new Invoice(o.id(), o.netPaise(), gst);
        };
        Migrator m = new Migrator(legacy, modern);
        m.modes.put("B2C", Mode.SHADOW);
        m.modes.put("B2B", Mode.SHADOW);
        for (int i = 1; i <= 6; i++) { m.invoice(new Order("C" + i, "B2C", 10_000 + i * 1_001)); m.invoice(new Order("B" + i, "B2B", 50_000)); }
        System.out.println(m.promote("B2C", 5));
        System.out.println(m.promote("B2B", 5));
        newOutage[0] = true;
        for (int i = 7; i <= 10; i++) m.invoice(new Order("C" + i, "B2C", 12_000));
        System.out.println("modes: " + m.modes);
        m.log.stream().limit(2).forEach(l -> System.out.println("  " + l));
        System.out.println("  ... " + (m.log.size() - 2) + " more log lines; last: " + m.log.get(m.log.size() - 1));
    }
}
```

1. The ACL turns legacy fixed-width output into the same `Invoice` type the new service produces.
2. Each segment has its own mode, controlled like a toggle.
3. In shadow mode, differences beyond one paisa of rounding count as unexplained.
4. Promotion requires enough samples and zero unexplained differences.
5. In new mode, failures fall back to legacy, and too many failures roll the segment back automatically.

**Run it:** `java InvoiceModernisationDemo.java`

**Output:**

```text
B2C promoted to NEW after 6 clean comparisons
B2B stays SHADOW: 6 unexplained difference(s)
modes: {B2B=SHADOW, B2C=SHADOW}
  diff B1: legacy Invoice[orderId=B1, netPaise=50000, gstPaise=2500] new Invoice[orderId=B1, netPaise=50000, gstPaise=9000]
  diff B2: legacy Invoice[orderId=B2, netPaise=50000, gstPaise=2500] new Invoice[orderId=B2, netPaise=50000, gstPaise=9000]
  ... 9 more log lines; last: auto-rollback B2C to SHADOW
```

**What to notice:**

- B2B was blocked from promotion by a wrong GST slab that no customer ever saw.
- When the new service failed after B2C's promotion, customers still got legacy invoices, and the segment went back to shadow without anyone being paged first.
- The legacy truncation vs new rounding difference (at most one paisa) was accepted explicitly, not silently.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Strangler fig | Incremental value, reversible steps | Two systems for a long time |
| Branch by abstraction | Trunk-based, always releasable | Temporary duplication |
| Parallel run | Production evidence, no customer impact | Extra compute; diff triage effort |
| Legacy ACL | Clean new models | Translation code to maintain |
| Feature toggles | Instant rollback, gradual rollout | Toggle debt; combinatorial states |

## 8. When to Use / When NOT to Use

**Use them when:**

- the legacy system works and earns money but blocks change;
- correctness must be demonstrated (billing, tax, payments);
- the business can't pause feature work for a rewrite.

**Consider a rewrite only when:**

- the system is small, well understood and its behaviour is fully specified;
- the old platform can't be run or routed around at all;
- the business genuinely needs different behaviour, not a copy.

## 9. Best Practices

1. **Migrate capability by capability; plan the deletion step.** *(Consensus.)*
2. **Prove new behaviour with parallel runs on real traffic.** *(Consensus.)*
3. **Keep legacy formats behind an ACL.** *(Consensus.)*
4. **Make data migration idempotent: absolute values, backfill, reconcile.** *(Consensus.)*
5. **Toggles have types, owners and expiry dates.** *(Consensus.)*
6. **Automate rollback criteria before promoting.** *(Widely used.)*
7. **Characterise legacy behaviour with tests before changing it.** *(Consensus, Feathers.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **The stalled strangler (production failure mode)**  
> **Symptom:** after three years, 90% of traffic was on new services, but the monolith still ran checkout writes, nightly jobs and reports; both platforms needed on-call, and incidents involved both.
> **Root cause:** slices were declared done at traffic cut-over; deletion and data ownership were never scheduled.
> **Fix:** track "capabilities with legacy deleted" as the primary metric; every slice ends with deletion.

> 🚨 **Big-bang rewrite**  
> **Symptom:** an 18-month rewrite launched with 40 missing business rules found by customers.
> **Root cause:** rules hidden in legacy code were never discovered because nothing compared old and new.
> **Fix:** parallel runs per capability; legacy as test oracle.

> 🚨 **Delta-based dual writes**  
> **Symptom:** loyalty balances in the new service were higher than legacy for thousands of customers.
> **Root cause:** dual writes copied increments while backfill copied totals; overlapping steps double-counted.
> **Fix:** copy absolute values from the source of truth; reconcile before switching reads.

> 🚨 **Toggle graveyard**  
> **Symptom:** 300 toggles, nobody knows which are safe to remove; a stale toggle re-enabled old code in an incident.
> **Root cause:** no owners or expiry; release toggles never cleaned up.
> **Fix:** toggle registry with type, owner and expiry; CI warns on expired toggles.

## 11. Configuration & Tuning

| Setting | Typical value | What it does | When to change it | Risk of changing |
|---------|---------------|--------------|-------------------|------------------|
| Canary percentage steps | 1, 5, 25, 50, 100% | Gradual exposure | Higher-risk changes: smaller steps | Slower rollout |
| Parallel-run sample rate | 1–100% | Comparison volume | Lower for expensive calls | Rare cases missed |
| Promotion minimum samples | Thousands, per segment | Evidence threshold | Rare segments need longer windows | False confidence |
| Rollback failure threshold | e.g. >10% over 5 min | Automatic rollback trigger | Critical paths: lower | Flapping |
| Toggle expiry (release) | 2–6 weeks | Forces cleanup | Long migrations: per-phase toggles | Toggle debt |

## 12. Security Considerations

- Parallel runs duplicate sensitive data flows; the new path needs the same access controls, masking and audit as legacy.
- ACLs must validate legacy data; old systems often lack input validation, and injection payloads can be stored in them.
- Toggle services are powerful: protect them with authentication, audit logs and change approvals for ops toggles.
- Decommissioned legacy systems must have data retained or deleted according to policy, and credentials revoked.

## 13. Performance & Scalability

Parallel runs roughly double the compute for compared requests; run the new path asynchronously and sample. Routing layers add a hop; keep them simple. Backfills should be throttled to protect legacy databases (batch sizes, off-peak windows). CDC-based sync keeps read models fresh without loading legacy with polling.

## 14. Testing It

Test migration machinery like production code: translation, comparison tolerance, promotion gates and rollback:

**InvoiceModernisationTest.java**

```java
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceModernisationTest {

    static final Function<InvoiceModernisationDemo.Order, String> LEGACY =
            o -> String.format("INV%-8s%09d%07d", o.id(), o.netPaise(), o.netPaise() * 5 / 100);
    static final Function<InvoiceModernisationDemo.Order, InvoiceModernisationDemo.Invoice> CORRECT =
            o -> new InvoiceModernisationDemo.Invoice(o.id(), o.netPaise(), Math.round(o.netPaise() * 0.05));

    static InvoiceModernisationDemo.Order order(String id, String segment) { return new InvoiceModernisationDemo.Order(id, segment, 10_010); }

    @Test
    void aclParsesLegacyFixedWidthRecords() {
        assertEquals(new InvoiceModernisationDemo.Invoice("FC-9", 123456, 6172), InvoiceModernisationDemo.fromLegacy("INVFC-9    0001234560006172"));
    }

    @Test
    void segmentsDefaultToLegacy() {
        int[] modernCalls = {0};
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, o -> { modernCalls[0]++; return CORRECT.apply(o); });
        m.invoice(order("A", "B2C"));
        assertEquals(0, modernCalls[0]);
    }

    @Test
    void shadowServesLegacyAndToleratesOnePaiseRounding() {
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, CORRECT);
        m.modes.put("B2C", InvoiceModernisationDemo.Mode.SHADOW);
        var served = m.invoice(order("A", "B2C"));
        assertEquals(500, served.gstPaise());                 // legacy truncates 500.5 to 500
        assertEquals(0, m.shadowStats.get("B2C")[1]);
    }

    @Test
    void promotionNeedsEnoughCleanSamples() {
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, CORRECT);
        m.modes.put("B2C", InvoiceModernisationDemo.Mode.SHADOW);
        for (int i = 0; i < 3; i++) m.invoice(order("A" + i, "B2C"));
        assertTrue(m.promote("B2C", 5).contains("only 3"));
        for (int i = 3; i < 5; i++) m.invoice(order("A" + i, "B2C"));
        assertTrue(m.promote("B2C", 5).contains("promoted"));
        assertEquals(InvoiceModernisationDemo.Mode.NEW, m.modes.get("B2C"));
    }

    @Test
    void failuresInNewModeFallBackAndRollBack() {
        boolean[] down = {false};
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, o -> { if (down[0]) throw new IllegalStateException("503"); return CORRECT.apply(o); });
        m.modes.put("B2C", InvoiceModernisationDemo.Mode.NEW);
        down[0] = true;
        for (int i = 0; i < 3; i++) assertEquals(500, m.invoice(order("A" + i, "B2C")).gstPaise());   // legacy served
        assertEquals(InvoiceModernisationDemo.Mode.NEW, m.modes.get("B2C"));                          // not enough samples yet
        m.invoice(order("A3", "B2C"));
        assertEquals(InvoiceModernisationDemo.Mode.SHADOW, m.modes.get("B2C"));
    }
}
```

Add characterisation (golden master) tests around legacy code before changing it.

## 15. Observability & Operations

Dashboards per capability: traffic split, mode per segment, comparison results by category, unexplained diff rate, fallback and rollback events, reconciliation mismatches, and backfill progress. Alert on unexplained diffs and automatic rollbacks. Keep a migration runbook per slice: how to roll back, who decides, and how to verify.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2004 | Fowler, "Strangler Fig Application" (renamed from Strangler Application in 2019) |
| 2004 | Michael Feathers, *Working Effectively with Legacy Code* (seams, characterisation tests) |
| 2007 | Paul Hammant names Branch by Abstraction |
| 2010 | Fowler's "FeatureToggle" note; toggles spread with continuous delivery |
| 2016 | GitHub Scientist for parallel experiments |
| 2023 | OpenFeature (vendor-neutral toggle API) moves to CNCF incubating |

## 17. Expert Deep Dive

- **Event interception.** Capture legacy events (CDC, message taps) to feed new services before any traffic moves.
- **Transitional architecture.** Temporary code (routers, syncs, comparers) is a legitimate investment; plan its removal like any other slice.
- **Legacy as oracle.** Golden-master and parallel-run approaches treat existing behaviour as the specification until the business decides otherwise.
- **Dark launching.** New code runs on production traffic without user-visible effects, the same idea as shadow mode.
- **Socio-technical migration.** Assign each slice to the team that will own the new capability; migrations stall when the legacy team owns everything.

> 📖 **War story: three years at ninety percent**  
> FreshCart's monolith migration reported 90% of traffic on new services after 18 months, and the board considered it nearly done. Three years in, the monolith still handled checkout writes, invoicing and 40 nightly jobs; both stacks had on-call rotations, and the worst incident of the year involved a sync job between them. **Fixes:** the migration dashboard switched its headline metric to "capabilities with legacy code deleted" (2 of 14 at the time), invoicing moved with a segment-by-segment parallel run like Example 3, nightly jobs got owners and were migrated or deleted, and each slice's definition of done included deleting the legacy path. The monolith was switched off 14 months later. **Lesson:** a migration isn't done when traffic moves; it's done when the old code is gone.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Why avoid big-bang rewrites?** **High risk, late value, missed rules.**
2. **What's the strangler fig?** **Replace a system piece by piece** behind a router.
3. **What's a feature toggle?** A **runtime switch** for behaviour.
4. **What's a seam?** A place to **change behaviour without editing the code there**.
5. **What's a parallel run?** **Old and new on the same inputs**, compared.

**▸ 🟡 L3**

1. **Branch by abstraction steps?** **Abstraction, move callers, new impl, switch, delete.**
2. **Why serve the legacy result in a parallel run?** **It's trusted; the new one isn't yet.**
3. **What does a legacy ACL handle?** **Formats, codes, implied decimals, dates, validation.**
4. **Toggle types?** **Release, ops, experiment, permission.**
5. **Why stable hashing for rollouts?** **Each customer stays in one variant.**

**▸ 🔴 L4–L5**

1. **How do you migrate data with no downtime?** **Source of truth, idempotent copy, backfill, reconcile, switch reads, switch writes.**
2. **When is a strangler slice done?** **New side owns data and the legacy path is deleted.**
3. **How do you decide promotion?** **Sample size, zero unexplained diffs, segment coverage, rollback criteria.**
4. **How do you prevent toggle debt?** **Types, owners, expiry, CI checks.**
5. **What's transitional architecture?** **Temporary routing and sync code** with planned removal.

**▸ 💻 Hands-on**

**Task:** In `ParallelRunDemo`, normalise before comparing: treat any difference of at most 100 paise on subscriber orders as accepted, and report the maximum accepted difference.

> ✅ **Solution**  
> ```java
> long maxAccepted = 0;
> long diff = Math.abs(served - candidate);
> if (o.subscriber() && diff <= 100) { category = "accepted"; maxAccepted = Math.max(maxAccepted, diff); }
> System.out.println("largest accepted difference: " + maxAccepted + " paise");
> ```

## 19. Practice

- [ ] Draw your legacy system's capabilities and mark each with a strangler stage.
    > 💡 **Hint**  
    > Include data ownership, not just traffic.

- [ ] Add a seam to one untestable legacy class and write its first unit test.
    > 💡 **Hint**  
    > Constructor injection with the legacy call as the default.

- [ ] Build a parallel-run comparator for one endpoint with normalisation of IDs and timestamps.
    > 💡 **Hint**  
    > Compare parsed objects, not raw strings.

- [ ] Audit your feature toggles: type, owner, expiry.
    > 💡 **Hint**  
    > Delete any release toggle at 100% for over a month.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Strangle by capability; done means data owned and legacy deleted.
> - Branch by abstraction: abstraction, callers, new impl, toggle, delete.
> - Parallel run: serve old, compare, classify, promote on evidence.
> - ACL keeps legacy formats out; quarantine what you can't read.
> - Toggles: typed, owned, expiring; stable hashing; kill switches.
> - Data: absolute values, backfill, reconcile, then switch.
>
> ```java
> if (compared >= minSamples && unexplained == 0) modes.put(segment, Mode.NEW);
> ```

## 21. Further Reading

- Martin Fowler, [StranglerFigApplication](https://martinfowler.com/bliki/StranglerFigApplication.html) and [BranchByAbstraction](https://martinfowler.com/bliki/BranchByAbstraction.html).
- Michael Feathers, *Working Effectively with Legacy Code* (2004).
- Sam Newman, *Monolith to Microservices* (2019).
- Pete Hodgson, [Feature Toggles (aka Feature Flags)](https://martinfowler.com/articles/feature-toggles.html).

---

[← Previous: Evolutionary Architecture](evolutionary-architecture.md) · [Next: JVM Internals Behind Patterns →](jvm-internals-behind-patterns.md)
