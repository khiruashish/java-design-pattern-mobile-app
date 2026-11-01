# Domain-Driven Design: Tactical Patterns

L4 🟠 Senior / Lead · ⏱ 45 min read · Prerequisites: [Architectural Patterns](architectural-patterns.md), [Enterprise Application Patterns](../l3-professional/enterprise-application-patterns.md)

> 📌 **TL;DR**  
> DDD's tactical patterns are the building blocks for modelling a complex domain in code. **Entities** have identity that persists as attributes change. **Value objects** are immutable, compared by value and validate themselves (`Money`, `PinCode`). **Aggregates** group objects that must stay consistent, with one **root** that enforces invariants; one transaction changes one aggregate. **Domain events** record meaningful things that happened. **Domain services** hold logic that belongs to no single object. **Factories** create valid aggregates, and **repositories** store and load whole aggregates by ID. In Java 21, records, sealed types and pattern matching make these patterns lighter than ever.

## 1. What It Is

**Plain English.** A grocery business has concepts with rules: an order can't exceed the cash-on-delivery limit, a subscription can't be skipped after 10 pm the night before, money is never negative. Tactical DDD puts those concepts and rules into code objects that protect themselves, instead of scattering checks through services.

**Definition.**

| Pattern | Identity | Mutability | Example |
|---------|----------|------------|---------|
| Entity | By ID | Mutable | `Customer`, `Rider` |
| Value object | By value | Immutable | `Money`, `PinCode`, `DeliveryWindow` |
| Aggregate (root) | By root ID | Mutable through root only | `Order` with lines, `Subscription` |
| Domain event | n/a | Immutable | `OrderPlaced`, `SubscriptionPaused` |
| Domain service | n/a | Stateless | `RefundPolicy`, `MonthlyBilling` |
| Factory | n/a | n/a | `SubscriptionFactory` |
| Repository | n/a | n/a | `SubscriptionRepository` |

## 2. Why It Exists

In many business applications, rules live in services that manipulate data classes ([anaemic domain model](../l3-professional/anti-patterns-and-code-smells.md)). As rules grow, they get duplicated, forgotten and contradicted, and invalid states reach the database. Eric Evans' *Domain-Driven Design* (2003) proposed modelling the domain in rich objects shaped by the language of domain experts, with explicit consistency boundaries. Tactical patterns are how that model is built; [strategic patterns](ddd-strategic.md) decide where models end.

## 3. Mental Model / Analogy

**Analogy.** A bank branch. A customer (entity) is the same person even after changing their phone number. A ₹500 note (value object) is interchangeable with any other ₹500 note. A locker with its contents (aggregate) is opened only by the locker key-holder (root), who checks the rules. "Locker opened" is written in the logbook (domain event). Currency exchange (domain service) involves two currencies and a rate; it belongs to neither. Account opening forms (factory) make sure new accounts are complete; the vault (repository) stores and retrieves whole lockers.

**Technical model.** Aggregates are consistency boundaries; within one, invariants hold after every transaction. Between aggregates, consistency is eventual, via events.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Entities

**Description.** An entity has a **stable identity** that persists across changes to its attributes and across storage, sessions and systems. Equality is by identity. Use a dedicated ID type (`CustomerId`) rather than a bare `String` or `Long`, so IDs of different entities can't be mixed up.

**Advantages / features.** Correct behaviour in sets and maps as attributes change; tracking over time; clear lifecycle.

**Before (equality by all fields) and after (equality by identity):**

**EntityDemo.java**

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** An Entity is defined by a stable identity, not by its attributes; its attributes can change over its life. */
public class EntityDemo {

    record CustomerId(String value) {
        CustomerId { if (!value.matches("C-\\d+")) throw new IllegalArgumentException("bad id " + value); }
    }

    static final class Customer {
        private final CustomerId id;
        private String name; private String phone;
        Customer(CustomerId id, String name, String phone) { this.id = id; this.name = name; this.phone = phone; }
        void changePhone(String newPhone) {
            if (!newPhone.matches("\\d{10}")) throw new IllegalArgumentException("phone must have 10 digits");
            phone = newPhone;
        }
        @Override public boolean equals(Object o) { return o instanceof Customer c && c.id.equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return id.value() + "(" + name + ", " + phone + ")"; }
    }

    /** Before: equality by all fields, as Lombok @Data or a record would generate. */
    record CustomerByValue(String id, String name, String phone) {}

    public static void main(String[] args) {
        Customer asha = new Customer(new CustomerId("C-17"), "Asha", "9876500001");
        Set<Customer> vip = new HashSet<>(Set.of(asha));
        asha.changePhone("9876500002");
        System.out.println("entity still in VIP set after phone change: " + vip.contains(asha));
        System.out.println("same customer loaded twice is equal: " + asha.equals(new Customer(new CustomerId("C-17"), "Asha R", "0000000000")));

        CustomerByValue before = new CustomerByValue("C-17", "Asha", "9876500001");
        CustomerByValue after = new CustomerByValue("C-17", "Asha", "9876500002");
        System.out.println("value-equality 'customer' equal after phone change: " + before.equals(after));
        System.out.println(Objects.toString(asha));
    }
}
```

1. A typed ID that validates its format.
2. Equality and hash code use only the ID.
3. Before: a record (or Lombok `@Data`) compares all fields, so the "same" customer stops being equal after a phone change.

**Run it:** `java EntityDemo.java`

```text
entity still in VIP set after phone change: true
same customer loaded twice is equal: true
value-equality 'customer' equal after phone change: false
C-17(Asha, 9876500002)
```

| Question | Entity | Value object |
|----------|--------|--------------|
| Do we track it over time? | Yes | No |
| Can two with equal attributes be different? | Yes | No |
| Mutable? | Usually | Never |
| Java shape | Class with ID-based `equals` | `record` |

**Use** entities for things the business tracks individually (customers, orders, riders). **Avoid** making everything an entity; most concepts are better as value objects.
**Real-world Java:** JPA `@Entity` with `@Id`; jMolecules `@Entity` and `Identifier`.
**Best practice:** generate IDs in the domain (UUIDs, or ULIDs for ordering) so new entities have identity before saving. **Gotcha:** JPA entities with database-generated IDs have no ID until persisted, breaking `equals`/`hashCode` in sets; use application-assigned IDs or compare carefully.

### Value objects

**Description.** A value object describes a characteristic with no identity of its own: money, quantities, addresses, date ranges. It's **immutable**, equal by value, and **validates itself** on construction, so an invalid one can't exist. Behaviour that belongs to the concept (adding money, checking window overlap) lives on it.

**Advantages / features.** Eliminates primitive obsession; thread-safe; no defensive copies; tiny, testable units of logic.

**ValueObjectDemo.java**

```java
import java.util.List;

/** Value objects: immutable, compared by value, self-validating, and they carry the behaviour of the concept. */
public class ValueObjectDemo {

    record Money(long paise, String currency) {
        Money {
            if (paise < 0) throw new IllegalArgumentException("money can't be negative");
            if (!currency.equals("INR")) throw new IllegalArgumentException("only INR supported");
        }
        static Money rupees(long r) { return new Money(r * 100, "INR"); }
        Money plus(Money o) { return new Money(paise + o.paise, currency); }
        Money times(int q) { return new Money(paise * q, currency); }
        Money percentOff(int p) { return new Money(paise - paise * p / 100, currency); }
        @Override public String toString() { return "Rs " + paise / 100 + "." + String.format("%02d", paise % 100); }
    }

    record Quantity(int value, String unit) {
        Quantity { if (value <= 0 || value > 50) throw new IllegalArgumentException("quantity 1..50, got " + value); }
    }

    record DeliveryWindow(int startHour, int endHour) {
        DeliveryWindow {
            if (startHour < 6 || endHour > 23 || endHour - startHour != 2) throw new IllegalArgumentException("windows are 2 hours between 06 and 23");
        }
        boolean overlaps(DeliveryWindow o) { return startHour < o.endHour && o.startHour < endHour; }
    }

    public static void main(String[] args) {
        Money price = Money.rupees(68);
        Money line = price.times(new Quantity(3, "pack").value());
        System.out.println("line: " + line + ", after 10% off: " + line.percentOff(10) + ", original unchanged: " + line);
        System.out.println("equal by value: " + Money.rupees(68).equals(new Money(6_800, "INR")));
        System.out.println("07-09 overlaps 08-10: " + new DeliveryWindow(7, 9).overlaps(new DeliveryWindow(8, 10)));
        for (Runnable bad : List.<Runnable>of(() -> new Quantity(0, "kg"), () -> new DeliveryWindow(22, 24), () -> new Money(100, "USD"))) {
            try { bad.run(); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
```

1. `Money` validates amount and currency and provides arithmetic returning new values.
2. `DeliveryWindow` encodes business rules (two-hour windows, operating hours) and behaviour (overlap).
3. Records give value equality for free.

**Run it:** `java ValueObjectDemo.java`

```text
line: Rs 204.00, after 10% off: Rs 183.60, original unchanged: Rs 204.00
equal by value: true
07-09 overlaps 08-10: true
rejected: quantity 1..50, got 0
rejected: windows are 2 hours between 06 and 23
rejected: only INR supported
```

| Primitive | Value object | Gains |
|-----------|--------------|-------|
| `long amountPaise` | `Money` | Unit safety, arithmetic, formatting |
| `String pin` | `PinCode` | Validation, region lookup |
| `int start, int end` | `DeliveryWindow` | Invariants, overlap logic |
| `String email` | `Email` | Normalisation, validation |

**Use** value objects generously; they're the cheapest, highest-return DDD pattern. **Avoid** value objects with setters or identity fields.
**Real-world Java:** `java.time` types, `BigDecimal`, `Currency`, JSR 354 `MonetaryAmount`.
**Best practice:** persist with JPA `@Embeddable` (records are supported as embeddables in Jakarta Persistence 3.2 / Hibernate 6.2+). **Gotcha:** `BigDecimal.equals` compares scale (`2.0` ≠ `2.00`); normalise in the value object or use `compareTo`.

### Aggregates and invariants

**Description.** An aggregate is a cluster of entities and value objects treated as one unit for changes. External code holds references only to the **aggregate root** and changes the aggregate only through its methods, which enforce **invariants**: rules that must always be true. One transaction should modify one aggregate; aggregates refer to each other by ID.

**Advantages / features.** Invariants enforced in one place; clear transaction and locking boundaries; safe concurrency.

**AggregateDemo.java**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Aggregate is a cluster of objects changed together through one root that enforces the invariants. Outsiders
 * hold a reference only to the root, and refer to other aggregates by ID.
 */
public class AggregateDemo {

    record OrderLine(String sku, int qty, long pricePaise) {}

    static final class Order {
        static final int MAX_ITEMS = 30;
        static final long MAX_COD_PAISE = 200_000;
        private final String id; private final String customerId;
        private final List<OrderLine> lines = new ArrayList<>();
        private boolean cashOnDelivery; private boolean placed;

        Order(String id, String customerId, boolean cod) { this.id = id; this.customerId = customerId; this.cashOnDelivery = cod; }

        void add(String sku, int qty, long price) {
            requireDraft();
            if (itemCount() + qty > MAX_ITEMS) throw new IllegalStateException("max " + MAX_ITEMS + " items per order");
            if (cashOnDelivery && total() + qty * price > MAX_COD_PAISE) throw new IllegalStateException("COD orders are limited to Rs 2000");
            lines.add(new OrderLine(sku, qty, price));
        }
        void place() { requireDraft(); if (lines.isEmpty()) throw new IllegalStateException("empty order"); placed = true; }

        List<OrderLine> lines() { return Collections.unmodifiableList(lines); }
        int itemCount() { return lines.stream().mapToInt(OrderLine::qty).sum(); }
        long total() { return lines.stream().mapToLong(l -> l.qty() * l.pricePaise()).sum(); }
        private void requireDraft() { if (placed) throw new IllegalStateException("order " + id + " already placed"); }
    }

    static void attempt(String label, Runnable r) {
        try { r.run(); System.out.println(label + ": ok"); }
        catch (RuntimeException e) { System.out.println(label + ": refused (" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")"); }
    }

    public static void main(String[] args) {
        Order order = new Order("FC-9", "C-17", true);
        attempt("add 2 ghee", () -> order.add("GHEE-1L", 2, 70_000));
        attempt("add 1 more ghee (COD limit)", () -> order.add("GHEE-1L", 1, 70_000));
        attempt("add 40 eggs (item limit)", () -> order.add("EGG", 40, 700));
        attempt("bypass via lines().add", () -> order.lines().add(new OrderLine("FREE", 99, 0)));
        attempt("place", order::place);
        attempt("add after placing", () -> order.add("MILK-1L", 1, 6_800));
        System.out.println("final: " + order.itemCount() + " items, total " + order.total());
    }
}
```

1. The root owns its lines and the rules about them: item limit, COD limit, frozen after placing.
2. Other aggregates (the customer) are referenced by ID, never by object.
3. The lines are exposed read-only, so the only way to add one is through the rule-checking method.

**Run it:** `java AggregateDemo.java`

```text
add 2 ghee: ok
add 1 more ghee (COD limit): refused (IllegalStateException: COD orders are limited to Rs 2000)
add 40 eggs (item limit): refused (IllegalStateException: max 30 items per order)
bypass via lines().add: refused (UnsupportedOperationException)
place: ok
add after placing: refused (IllegalStateException: order FC-9 already placed)
final: 2 items, total 140000
```

| Vaughn Vernon's aggregate rules | Why |
|---------------------------------|-----|
| Model true invariants in consistency boundaries | Only rules that must be immediately consistent belong together |
| Design small aggregates | Less contention, faster loads |
| Reference other aggregates by identity | No accidental cross-aggregate changes |
| Use eventual consistency outside the boundary | Domain events between aggregates |

**Use** aggregates for any cluster with rules spanning several objects. **Avoid** huge aggregates ("Customer with all orders"): every change locks everything and loads too much.
**Real-world Java:** JPA root entity with `@OneToMany(cascade = ALL, orphanRemoval = true)` children; Spring Data repositories per root only; Axon aggregates.
**Best practice:** if two users regularly edit the same aggregate concurrently, it's probably too big. **Gotcha:** returning a mutable internal collection (even a JPA-managed `List`) lets callers bypass invariants; return unmodifiable views or copies.

### Domain events

**Description.** A domain event records something that happened in the domain that domain experts care about: `OrderPlaced`, `SubscriptionPaused`. Aggregates **record** events when state changes; the application **dispatches** them after the aggregate is saved. Handlers update other aggregates, send notifications, or inform other bounded contexts.

**Advantages / features.** Side effects decoupled from the aggregate; an explicit record of business facts; the basis for eventual consistency between aggregates.

**DomainEventsDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Domain events record that something meaningful happened in an aggregate; they're dispatched after it's saved. */
public class DomainEventsDemo {

    sealed interface DomainEvent permits OrderPlaced, OrderCancelled {}
    record OrderPlaced(String orderId, String customerId, long totalPaise) implements DomainEvent {}
    record OrderCancelled(String orderId, String reason) implements DomainEvent {}

    static final class Order {
        private final String id; private final String customerId; private String status = "DRAFT"; private final long total;
        private final List<DomainEvent> events = new ArrayList<>();
        Order(String id, String customerId, long total) { this.id = id; this.customerId = customerId; this.total = total; }

        void place() {
            if (!status.equals("DRAFT")) throw new IllegalStateException(status);
            status = "PLACED";
            events.add(new OrderPlaced(id, customerId, total));
        }
        void cancel(String reason) {
            if (!status.equals("PLACED")) throw new IllegalStateException("only placed orders can be cancelled");
            status = "CANCELLED";
            events.add(new OrderCancelled(id, reason));
        }
        List<DomainEvent> pullEvents() { List<DomainEvent> out = List.copyOf(events); events.clear(); return out; }
    }

    static final List<String> handled = new ArrayList<>();
    static void dispatch(DomainEvent e) {
        switch (e) {
            case OrderPlaced p -> { handled.add("loyalty: +" + p.totalPaise() / 10_000 + " points for " + p.customerId()); handled.add("warehouse: pick " + p.orderId()); }
            case OrderCancelled c -> handled.add("payments: refund " + c.orderId() + " (" + c.reason() + ")");
        }
    }

    public static void main(String[] args) {
        Order order = new Order("FC-31", "C-17", 51_800);
        order.place();
        order.cancel("customer changed mind");
        System.out.println("saved order FC-31");
        order.pullEvents().forEach(DomainEventsDemo::dispatch);
        handled.forEach(h -> System.out.println("  " + h));
        System.out.println("events left on the aggregate: " + order.pullEvents().size());
    }
}
```

1. A sealed hierarchy of events, named in the past tense.
2. The aggregate records events as part of the state change, without knowing who listens.
3. Events are dispatched only after saving, so handlers never act on unsaved changes.

**Run it:** `java DomainEventsDemo.java`

```text
saved order FC-31
  loyalty: +5 points for C-17
  warehouse: pick FC-31
  payments: refund FC-31 (customer changed mind)
events left on the aggregate: 0
```

| Kind | Scope | Delivery | Example |
|------|-------|----------|---------|
| Domain event | Within a bounded context | In-process, after commit | `OrderPlaced` → loyalty points |
| Integration event | Between contexts/services | Broker, via outbox | `OrderPlaced` → warehouse system |
| Event-sourced event | The aggregate's own history | Stored as the state | See [Distributed Data Patterns](distributed-data-patterns.md) |

**Use** domain events for consequences that live outside the aggregate. **Avoid** events as hidden method calls that must happen in the same transaction for correctness; that's a sign the boundary is wrong.
**Real-world Java:** Spring Data's `@DomainEvents` / `AbstractAggregateRoot.registerEvent`, Spring Modulith, Axon.
**Best practice:** events carry IDs and the facts handlers need, and are immutable records. **Gotcha:** Spring Data publishes `@DomainEvents` only when you call `save()` on the repository; modifying a managed entity without `save()` publishes nothing.

### Domain services

**Description.** Some domain logic doesn't naturally belong to one entity or value object: it involves several aggregates, or a policy that uses external facts. A **domain service** is a stateless object, named in the domain language, that holds that logic. It differs from an application service, which orchestrates use cases (load, call, save) but contains no business rules.

**Advantages / features.** Keeps entities focused; puts cross-object rules somewhere explicit and testable.

**DomainServiceDemo.java**

```java
/** A domain service holds domain logic that doesn't belong to any single entity or value object. */
public class DomainServiceDemo {

    record Money(long paise) { Money minus(Money o) { return new Money(Math.max(0, paise - o.paise)); } }
    record Order(String id, Money paid, Money deliveryFee, int hoursSinceDelivery, boolean memberAtPurchase) {}
    record Complaint(String orderId, String kind, Money itemValue) {}

    /** Refund rules need the order, the complaint and the customer's status: no single entity owns them. */
    static final class RefundPolicy {
        Money refundFor(Order order, Complaint c) {
            if (order.hoursSinceDelivery() > 48) return new Money(0);
            Money base = switch (c.kind()) {
                case "MISSING", "DAMAGED" -> c.itemValue();
                case "LATE" -> order.deliveryFee();
                default -> new Money(0);
            };
            if (order.memberAtPurchase() && c.kind().equals("LATE")) base = new Money(base.paise() + 5_000);
            return new Money(Math.min(base.paise(), order.paid().paise()));
        }
    }

    public static void main(String[] args) {
        RefundPolicy policy = new RefundPolicy();
        Order order = new Order("FC-40", new Money(51_800), new Money(2_500), 3, true);
        System.out.println("missing curd:  " + policy.refundFor(order, new Complaint("FC-40", "MISSING", new Money(4_500))).paise());
        System.out.println("late delivery: " + policy.refundFor(order, new Complaint("FC-40", "LATE", new Money(0))).paise());
        Order old = new Order("FC-12", new Money(30_000), new Money(2_500), 72, false);
        System.out.println("damaged, 3 days later: " + policy.refundFor(old, new Complaint("FC-12", "DAMAGED", new Money(9_000))).paise());
    }
}
```

1. A refund policy needs the order, the complaint and membership status, so no single object owns it.
2. Rules are expressed in domain terms: complaint window, kind of complaint.
3. Member perks are a business rule, not a technical one, so they belong here too.

**Run it:** `java DomainServiceDemo.java`

```text
missing curd:  4500
late delivery: 7500
damaged, 3 days later: 0
```

| | Domain service | Application service |
|-|----------------|---------------------|
| Contains business rules | Yes | No |
| Transactions, security | No | Yes |
| Talks to repositories | Rarely (via interfaces) | Yes |
| Named after | Domain concept (`RefundPolicy`) | Use case (`ResolveComplaint`) |

**Use** a domain service when a rule spans aggregates or needs external facts. **Avoid** moving entity behaviour into services; that slides back to an anaemic model.
**Real-world Java:** pricing engines, tax calculators, routing/allocation policies.
**Best practice:** keep domain services pure (inputs in, result out) so they're trivially testable. **Gotcha:** a "service" with 30 methods operating on one entity is a God Object, not a domain service.

### Factories and repositories in DDD

**Description.** A **factory** creates aggregates that are valid from the start when construction is complex (choosing a plan, checking allowed days, generating IDs). A **repository** gives collection-like access to aggregates: one repository per aggregate root, which loads and stores the **whole** aggregate. There are no repositories for internal entities, and no queries that return half an aggregate for modification.

**Advantages / features.** Construction rules in one place; persistence hidden behind domain language; aggregate boundaries respected by data access.

**DddFactoryRepositoryDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** DDD factories create whole, valid aggregates; repositories store and load whole aggregates by root ID only. */
public class DddFactoryRepositoryDemo {

    record Plan(String code, int litresPerDay, long pricePerLitrePaise, List<String> allowedDays) {}
    record Subscription(String id, String customerId, Plan plan, List<String> days, String status) {}

    static final class SubscriptionFactory {
        private final Map<String, Plan> catalog;
        private int seq = 1;
        SubscriptionFactory(Map<String, Plan> catalog) { this.catalog = catalog; }
        Subscription create(String customerId, String planCode, List<String> days) {
            Plan plan = Optional.ofNullable(catalog.get(planCode)).orElseThrow(() -> new IllegalArgumentException("unknown plan " + planCode));
            if (days.isEmpty() || !plan.allowedDays().containsAll(days)) throw new IllegalArgumentException(planCode + " delivers only on " + plan.allowedDays());
            return new Subscription("SUB-" + seq++, customerId, plan, List.copyOf(days), "ACTIVE");
        }
    }

    interface SubscriptionRepository {
        void save(Subscription s);
        Optional<Subscription> byId(String id);
        List<Subscription> activeFor(String customerId);
    }
    static final class InMemorySubscriptions implements SubscriptionRepository {
        private final Map<String, Subscription> rows = new TreeMap<>();
        public void save(Subscription s) { rows.put(s.id(), s); }
        public Optional<Subscription> byId(String id) { return Optional.ofNullable(rows.get(id)); }
        public List<Subscription> activeFor(String c) { return rows.values().stream().filter(s -> s.customerId().equals(c) && s.status().equals("ACTIVE")).toList(); }

    }

    public static void main(String[] args) {
        var weekdays = List.of("MON", "TUE", "WED", "THU", "FRI");
        var factory = new SubscriptionFactory(Map.of(
                "MILK-1L-WEEKDAY", new Plan("MILK-1L-WEEKDAY", 1, 6_800, weekdays),
                "MILK-2L-DAILY", new Plan("MILK-2L-DAILY", 2, 6_500, List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"))));
        SubscriptionRepository repo = new InMemorySubscriptions();

        repo.save(factory.create("C-17", "MILK-1L-WEEKDAY", List.of("MON", "WED", "FRI")));
        repo.save(factory.create("C-17", "MILK-2L-DAILY", List.of("SAT", "SUN")));
        try {
            factory.create("C-17", "MILK-1L-WEEKDAY", List.of("SUN"));
        } catch (IllegalArgumentException e) {
            System.out.println("factory refused: " + e.getMessage());
        }
        repo.activeFor("C-17").forEach(s -> System.out.println(s.id() + " " + s.plan().code() + " " + s.days()));
        System.out.println("SUB-2 loaded whole: " + repo.byId("SUB-2").map(s -> s.plan().litresPerDay() + "L on " + s.days()).orElse("none"));
    }
}
```

1. The factory checks the plan exists and the chosen days are allowed before creating anything.
2. The repository interface is per aggregate root, in domain language.
3. There's no `SubscriptionDayRepository`: days are only reachable through their subscription.

**Run it:** `java DddFactoryRepositoryDemo.java`

```text
factory refused: MILK-1L-WEEKDAY delivers only on [MON, TUE, WED, THU, FRI]
SUB-1 MILK-1L-WEEKDAY [MON, WED, FRI]
SUB-2 MILK-2L-DAILY [SAT, SUN]
SUB-2 loaded whole: 2L on [SAT, SUN]
```

| Factory form | When |
|--------------|------|
| Static creation method on the root | Simple creation (`Order.draft(customerId)`) |
| Factory class | Needs other data (plans, catalogues) or dependencies |
| Factory method on another aggregate | Creation is part of another's behaviour (`customer.startSubscription(...)`) |
| Reconstitution in the repository | Rebuilding from storage, bypassing "new" rules |

**Use** factories when creation has rules beyond field validation. **Avoid** a factory class for objects whose constructor is enough.
**Real-world Java:** Spring Data repositories per aggregate root; `jMolecules` `@Factory`, `@Repository`.
**Best practice:** separate "create new" (applies business rules, records `Started`) from "reconstitute from storage" (no events). **Gotcha:** generic repositories with `findAll(Specification)` for every entity invite loading and modifying internal entities directly.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop passes PIN codes around as strings, validated (or not) in a dozen places. Introduce a `PinCode` value object.

**PinCodeDemo.java**

```java
import java.util.List;

/** QuickDrop: replace a String PIN code used everywhere with a value object that validates once and knows its zone. */
public class PinCodeDemo {

    record PinCode(String value) {
        PinCode {
            value = value.strip();
            if (!value.matches("[1-9]\\d{5}")) throw new IllegalArgumentException("'" + value + "' is not a 6-digit PIN code");
        }
        String region() { return switch (value.charAt(0)) { case '1', '2' -> "North"; case '3', '4' -> "West"; case '5', '6' -> "South"; default -> "East"; }; }
        boolean sameCityAs(PinCode o) { return value.substring(0, 3).equals(o.value.substring(0, 3)); }
    }

    public static void main(String[] args) {
        PinCode home = new PinCode(" 560034 "), office = new PinCode("560103");
        System.out.println(home + " region " + home.region() + ", same city as office: " + home.sameCityAs(office));
        for (String raw : List.of("400001", "056003", "5600")) {
            try { System.out.println(new PinCode(raw).region()); }
            catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
```

1. Normalisation and validation happen once, in the compact constructor.
2. Domain behaviour (region, same city) lives with the data.

**Run it:** `java PinCodeDemo.java`

**Output:**

```text
PinCode[value=560034] region South, same city as office: true
West
rejected: '056003' is not a 6-digit PIN code
rejected: '5600' is not a 6-digit PIN code
```

**What to notice:**

- Every method that takes a `PinCode` can trust it; the dozen scattered checks can be deleted.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart's cart has rules: at most 2 kg of mangoes per order during the season, one coupon per order, nothing changes after checkout. Other contexts (analytics, recommendations) want to know what happened.

**CartAggregateDemo.java**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FreshCart's cart as an aggregate: value objects for money and quantities, invariants enforced by the root
 * (per-item limits, one active coupon, frozen after checkout), and domain events recorded for other contexts.
 */
public class CartAggregateDemo {

    record Money(long paise) {
        Money { if (paise < 0) throw new IllegalArgumentException("negative money"); }
        Money plus(Money o) { return new Money(paise + o.paise); }
        Money times(int q) { return new Money(paise * q); }
        @Override public String toString() { return "Rs " + paise / 100; }
    }
    record Sku(String code) {}
    record Line(Sku sku, int qty, Money unitPrice) { Money amount() { return unitPrice.times(qty); } }

    sealed interface CartEvent permits ItemAdded, CouponApplied, CheckedOut {}
    record ItemAdded(String cartId, String sku, int qty) implements CartEvent {}
    record CouponApplied(String cartId, String code) implements CartEvent {}
    record CheckedOut(String cartId, long totalPaise) implements CartEvent {}

    static final class Cart {
        static final Map<String, Integer> PER_ITEM_LIMIT = Map.of("MANGO-1KG", 2);
        private final String id;
        private final Map<Sku, Line> lines = new LinkedHashMap<>();
        private String coupon; private boolean checkedOut;
        private final List<CartEvent> events = new ArrayList<>();
        Cart(String id) { this.id = id; }

        void add(Sku sku, int qty, Money unitPrice) {
            requireOpen();
            int newQty = qty + (lines.containsKey(sku) ? lines.get(sku).qty() : 0);
            int limit = PER_ITEM_LIMIT.getOrDefault(sku.code(), 10);
            if (newQty > limit) throw new IllegalStateException("limit " + limit + " for " + sku.code());
            lines.put(sku, new Line(sku, newQty, unitPrice));
            events.add(new ItemAdded(id, sku.code(), qty));
        }
        void applyCoupon(String code) {
            requireOpen();
            if (coupon != null) throw new IllegalStateException("only one coupon per order (" + coupon + " applied)");
            coupon = code;
            events.add(new CouponApplied(id, code));
        }
        Money total() {
            Money subtotal = lines.values().stream().map(Line::amount).reduce(new Money(0), Money::plus);
            return "FRESH10".equals(coupon) ? new Money(subtotal.paise() * 90 / 100) : subtotal;
        }
        void checkout() {
            requireOpen();
            if (lines.isEmpty()) throw new IllegalStateException("cart is empty");
            checkedOut = true;
            events.add(new CheckedOut(id, total().paise()));
        }
        List<Line> lines() { return Collections.unmodifiableList(new ArrayList<>(lines.values())); }
        List<CartEvent> pullEvents() { var out = List.copyOf(events); events.clear(); return out; }
        private void requireOpen() { if (checkedOut) throw new IllegalStateException("cart " + id + " is checked out"); }
    }

    public static void main(String[] args) {
        Cart cart = new Cart("CART-5");
        cart.add(new Sku("MANGO-1KG"), 1, new Money(45_000));
        cart.add(new Sku("MILK-1L"), 3, new Money(6_800));
        for (Runnable r : List.<Runnable>of(
                () -> cart.add(new Sku("MANGO-1KG"), 2, new Money(45_000)),
                () -> cart.applyCoupon("FRESH10"),
                () -> cart.applyCoupon("SAVE50"),
                cart::checkout,
                () -> cart.add(new Sku("EGGS-12"), 1, new Money(9_000)))) {
            try { r.run(); } catch (IllegalStateException e) { System.out.println("refused: " + e.getMessage()); }
        }
        System.out.println("lines: " + cart.lines().stream().map(l -> l.qty() + "x" + l.sku().code()).toList() + ", total " + cart.total());
        cart.pullEvents().forEach(e -> System.out.println("event: " + e));
    }
}
```

1. Lines are internal to the aggregate, keyed by SKU, so adding the same item merges quantities.
2. Invariants are checked on every change, including the merged quantity.
3. Checkout records an event with the final total.

**Run it:** `java CartAggregateDemo.java`

**Output:**

```text
refused: limit 2 for MANGO-1KG
refused: only one coupon per order (FRESH10 applied)
refused: cart CART-5 is checked out
lines: [1xMANGO-1KG, 3xMILK-1L], total Rs 588
event: ItemAdded[cartId=CART-5, sku=MANGO-1KG, qty=1]
event: ItemAdded[cartId=CART-5, sku=MILK-1L, qty=3]
event: CouponApplied[cartId=CART-5, code=FRESH10]
event: CheckedOut[cartId=CART-5, totalPaise=58860]
```

**What to notice:**

- Refused operations recorded no events; the event list is an accurate history of accepted changes.
- No code outside `Cart` can break the mango limit, however it's called.

### Example 3: Production-grade

**Scenario:** DairyDay's milk subscription. Customers skip days (until 22:00 the night before) and pause up to 30 days; support staff resume subscriptions; a customer's phone and the support desk sometimes edit the same subscription at once. Billing charges per delivered litre. Every accepted change must produce an event for delivery planning, and no event may escape from a failed save.

**SubscriptionAggregateDemo.java**

```java
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * DairyDay's milk subscription as a production-style aggregate: value objects, invariants (cutoff, pause limits),
 * domain events, optimistic concurrency via a version, a factory, a repository that stores whole aggregates, and a
 * domain service for billing that needs both the subscription and the calendar.
 */
public class SubscriptionAggregateDemo {

    // ---------- Value objects ----------
    record SubscriptionId(String value) {}
    record Litres(int value) { Litres { if (value < 1 || value > 5) throw new IllegalArgumentException("1..5 litres"); } }
    record Money(long paise) { Money times(long n) { return new Money(paise * n); } }

    // ---------- Events ----------
    sealed interface Event permits Started, Skipped, Paused, Resumed {}
    record Started(SubscriptionId id, int litres) implements Event {}
    record Skipped(SubscriptionId id, LocalDate day) implements Event {}
    record Paused(SubscriptionId id, LocalDate from, LocalDate to) implements Event {}
    record Resumed(SubscriptionId id) implements Event {}

    // ---------- Aggregate root ----------
    static final class Subscription {
        static final int MAX_PAUSE_DAYS = 30;
        private final SubscriptionId id; private final String customerId;
        private Litres litres; private final Money pricePerLitre;
        private final TreeSet<LocalDate> skipped = new TreeSet<>();
        private LocalDate pausedFrom, pausedTo;
        private long version;
        private final List<Event> events = new ArrayList<>();

        private Subscription(SubscriptionId id, String customerId, Litres litres, Money price, long version) {
            this.id = id; this.customerId = customerId; this.litres = litres; this.pricePerLitre = price; this.version = version;
        }

        void skip(LocalDate day, LocalDate today, int hourNow) {
            if (!day.isAfter(today) || (day.equals(today.plusDays(1)) && hourNow >= 22))
                throw new IllegalStateException("too late to skip " + day + " (cutoff 22:00 the day before)");
            if (isPaused(day)) throw new IllegalStateException(day + " is already paused");
            if (skipped.add(day)) events.add(new Skipped(id, day));
        }
        void pause(LocalDate from, LocalDate to, LocalDate today) {
            if (!from.isAfter(today)) throw new IllegalStateException("pause must start in the future");
            if (from.datesUntil(to.plusDays(1)).count() > MAX_PAUSE_DAYS) throw new IllegalStateException("pause at most " + MAX_PAUSE_DAYS + " days");
            pausedFrom = from; pausedTo = to;
            skipped.removeIf(d -> isPaused(d));
            events.add(new Paused(id, from, to));
        }
        void resume() { if (pausedFrom == null) return; pausedFrom = null; pausedTo = null; events.add(new Resumed(id)); }

        boolean deliversOn(LocalDate day) { return !skipped.contains(day) && !isPaused(day) && day.getDayOfWeek() != DayOfWeek.SUNDAY; }
        private boolean isPaused(LocalDate d) { return pausedFrom != null && !d.isBefore(pausedFrom) && !d.isAfter(pausedTo); }
        List<Event> pullEvents() { var out = List.copyOf(events); events.clear(); return out; }
        SubscriptionId id() { return id; } long version() { return version; } Litres litres() { return litres; } Money pricePerLitre() { return pricePerLitre; }
    }

    // ---------- Factory ----------
    static final class SubscriptionFactory {
        private int seq = 1;
        Subscription start(String customerId, int litres, Money price) {
            var s = new Subscription(new SubscriptionId("SUB-" + seq++), customerId, new Litres(litres), price, 0);
            s.events.add(new Started(s.id, litres));
            return s;
        }
    }

    // ---------- Repository with optimistic concurrency ----------
    static final class ConcurrentModification extends RuntimeException {
        ConcurrentModification(SubscriptionId id) { super(id.value() + " was changed by someone else; reload and retry"); }
    }
    static final class SubscriptionRepository {
        private record Stored(String customerId, int litres, long price, TreeSet<LocalDate> skipped, LocalDate pf, LocalDate pt, long version) {}
        private final Map<String, Stored> table = new TreeMap<>();
        final List<Event> published = new ArrayList<>();

        void save(Subscription s) {
            Stored current = table.get(s.id.value());
            long expected = current == null ? 0 : current.version();
            if (s.version != expected) throw new ConcurrentModification(s.id);
            table.put(s.id.value(), new Stored(s.customerId, s.litres.value(), s.pricePerLitre.paise(), new TreeSet<>(s.skipped), s.pausedFrom, s.pausedTo, expected + 1));
            s.version = expected + 1;
            published.addAll(s.pullEvents());                                             // dispatched only after a successful save
        }
        Optional<Subscription> load(SubscriptionId id) {
            return Optional.ofNullable(table.get(id.value())).map(st -> {
                var s = new Subscription(id, st.customerId(), new Litres(st.litres()), new Money(st.price()), st.version());
                s.skipped.addAll(st.skipped()); s.pausedFrom = st.pf(); s.pausedTo = st.pt();
                return s;
            });
        }
    }

    // ---------- Domain service ----------
    static final class MonthlyBilling {
        Money billFor(Subscription s, LocalDate monthStart) {
            long days = monthStart.datesUntil(monthStart.plusMonths(1)).filter(s::deliversOn).count();
            return s.pricePerLitre().times(s.litres().value() * days);
        }
    }

    public static void main(String[] args) {
        var repo = new SubscriptionRepository();
        var factory = new SubscriptionFactory();
        LocalDate today = LocalDate.of(2026, 11, 1);

        Subscription sub = factory.start("C-17", 2, new Money(6_800));
        repo.save(sub);

        Subscription app = repo.load(sub.id()).orElseThrow();
        app.skip(LocalDate.of(2026, 11, 3), today, 20);
        app.pause(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24), today);
        repo.save(app);

        Subscription supportDesk = repo.load(sub.id()).orElseThrow();
        Subscription staleTab = repo.load(sub.id()).orElseThrow();
        supportDesk.resume();
        repo.save(supportDesk);
        staleTab.skip(LocalDate.of(2026, 11, 5), today, 9);
        try { repo.save(staleTab); } catch (ConcurrentModification e) { System.out.println("stale tab: " + e.getMessage()); }

        for (Runnable r : List.<Runnable>of(
                () -> app.skip(LocalDate.of(2026, 11, 2), today, 23),
                () -> app.pause(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 15), today))) {
            try { r.run(); } catch (IllegalStateException e) { System.out.println("refused: " + e.getMessage()); }
        }

        Subscription current = repo.load(sub.id()).orElseThrow();
        System.out.println("version " + current.version() + ", November bill: Rs " + new MonthlyBilling().billFor(current, LocalDate.of(2026, 11, 1)).paise() / 100);
        repo.published.forEach(e -> System.out.println("event: " + e));
    }
}
```

1. A version number enables optimistic concurrency.
2. Invariants live on the root: the 22:00 cutoff, no skipping paused days, the pause limit.
3. The factory creates valid subscriptions and records the `Started` event.
4. The repository saves the whole aggregate, checks the version, and only then publishes its events.
5. Billing is a domain service: it needs the subscription and the calendar.

**Run it:** `java SubscriptionAggregateDemo.java`

**Output:**

```text
stale tab: SUB-1 was changed by someone else; reload and retry
refused: too late to skip 2026-11-02 (cutoff 22:00 the day before)
refused: pause at most 30 days
version 3, November bill: Rs 3264
event: Started[id=SubscriptionId[value=SUB-1], litres=2]
event: Skipped[id=SubscriptionId[value=SUB-1], day=2026-11-03]
event: Paused[id=SubscriptionId[value=SUB-1], from=2026-11-20, to=2026-11-24]
event: Resumed[id=SubscriptionId[value=SUB-1]]
```

**What to notice:**

- The stale browser tab's skip was rejected instead of silently overwriting the support desk's resume.
- The November bill counts 24 delivery days: 30 days minus 5 Sundays minus 1 skip.
- Only events from successful saves were published; the stale tab's `Skipped` event never escaped.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Entities with typed IDs | Clear identity, no ID mix-ups | More small types |
| Value objects | Validation once, expressive code | Mapping to persistence |
| Aggregates | Enforced invariants, clear transactions | Design effort; wrong boundaries hurt |
| Domain events | Decoupled side effects | Eventual consistency; more moving parts |
| Domain services | Explicit cross-object rules | Risk of anaemic drift |
| Factories / repositories | Valid creation, aggregate-shaped persistence | More types than plain CRUD |

## 8. When to Use / When NOT to Use

**Use them when:**

- the domain has real, changing business rules (pricing, scheduling, subscriptions);
- domain experts are available and the language matters;
- the code will live for years.

**Don't use them when:**

- the service is CRUD over a few tables with no rules;
- it's a reporting or analytics workload (use read models and SQL);
- the team has no access to domain knowledge; patterns without the model are ceremony.

## 9. Best Practices

1. **Prefer value objects; make entities only for tracked things.** *(Consensus.)*
2. **Small aggregates, referenced by ID.** *(Consensus; Vaughn Vernon.)*
3. **One aggregate per transaction; eventual consistency between aggregates.** *(Consensus, with pragmatic exceptions.)*
4. **Dispatch domain events after the save succeeds.** *(Consensus.)*
5. **Use optimistic locking on aggregates.** *(Consensus.)*
6. **Name everything in the ubiquitous language.** *(Consensus.)*
7. **Keep framework annotations out of the domain where practical.** *(Opinion; many teams accept JPA annotations.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Invariant bypassed through a mutable collection (production failure mode)**  
> **Symptom:** orders with 60 items went through despite a 30-item limit; packers ran out of crates.
> **Root cause:** a bulk-import feature called `order.getLines().addAll(...)` on the JPA-managed list, bypassing `addLine`.
> **Fix:** expose read-only views, keep the collection private, and add a test that external modification fails.

> 🚨 **Giant aggregate**  
> **Symptom:** `OptimisticLockException` storms whenever a customer is busy; loading a customer takes 2 seconds.
> **Root cause:** `Customer` aggregate contained all orders, addresses and subscriptions.
> **Fix:** split into separate aggregates referencing `CustomerId`; keep only true invariants together.

> 🚨 **Events published before save**  
> **Symptom:** loyalty points granted for orders that failed to save.
> **Root cause:** events dispatched from inside the aggregate method.
> **Fix:** record in the aggregate, dispatch after commit.

> 🚨 **Value objects with setters**  
> **Symptom:** a shared `Money` instance changed in one order and altered another's total.
> **Root cause:** mutable "value" object shared between aggregates.
> **Fix:** immutable records; operations return new instances.

## 11. Configuration & Tuning

| Setting / tool | Default | What it does | When to change it | Risk of changing |
|----------------|---------|--------------|-------------------|------------------|
| JPA `@Version` | none | Optimistic locking per aggregate root | Always on roots | Must handle conflicts |
| `@Embeddable` records | — | Value objects as columns | Value objects | Older Hibernate versions need classes |
| `@OneToMany(orphanRemoval = true)` | false | Child lifecycle bound to root | Aggregate internals | Accidental deletes if misused |
| Spring Data `AbstractAggregateRoot` | — | Event registration on save | Domain events | Only on `save()` |
| jMolecules + ArchUnit rules | — | Verifies DDD building-block rules | Larger teams | Learning curve |
| ID generation (UUIDv7/ULID) | DB sequence | Identity before persistence | Domain-generated IDs | Index locality (use time-ordered IDs) |

## 12. Security Considerations

- Aggregates enforce business invariants; authorisation (who may call `skip`) usually belongs to the application service, but domain rules like "only the owner can pause" can be modelled explicitly.
- Typed IDs prevent mixing `CustomerId` and `OrderId`, a source of IDOR bugs.
- Value objects centralise input validation (lengths, formats), reducing injection risk at deeper layers.
- Domain events may carry personal data; include only what consumers need.

## 13. Performance & Scalability

Small aggregates load fast and lock little. Large collections inside aggregates hurt; model them as separate aggregates or query them via read models. Value objects are typically scalar-replaced or cheap. Optimistic concurrency scales well when conflicts are rare; frequent conflicts mean the aggregate boundary is too wide. Use read models (CQRS) for list screens instead of loading many aggregates.

## 14. Testing It

Aggregates and value objects are plain Java: test invariants, state transitions, events and concurrency behaviour directly, without Spring or a database:

**SubscriptionAggregateTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionAggregateTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);   // a Sunday
    SubscriptionAggregateDemo.SubscriptionRepository repo;
    SubscriptionAggregateDemo.Subscription sub;

    @BeforeEach
    void setUp() {
        repo = new SubscriptionAggregateDemo.SubscriptionRepository();
        sub = new SubscriptionAggregateDemo.SubscriptionFactory().start("C-1", 1, new SubscriptionAggregateDemo.Money(6_800));
        repo.save(sub);
    }

    @Test
    void skipRespectsTheCutoff() {
        var tomorrow = TODAY.plusDays(1);
        assertThrows(IllegalStateException.class, () -> sub.skip(tomorrow, TODAY, 22));
        sub.skip(tomorrow, TODAY, 21);
        assertFalse(sub.deliversOn(tomorrow));
    }

    @Test
    void pauseAbsorbsSkipsAndIsLimited() {
        var d = LocalDate.of(2026, 11, 10);
        sub.skip(d, TODAY, 9);
        sub.pause(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12), TODAY);
        assertFalse(sub.deliversOn(d));
        sub.resume();
        assertTrue(sub.deliversOn(d), "the skip was absorbed by the pause and doesn't come back");
        assertThrows(IllegalStateException.class, () -> sub.pause(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), TODAY));
    }

    @Test
    void secondWriterGetsAConcurrencyErrorAndNoEventsAreLeaked() {
        var a = repo.load(sub.id()).orElseThrow();
        var b = repo.load(sub.id()).orElseThrow();
        a.skip(LocalDate.of(2026, 11, 4), TODAY, 9);
        repo.save(a);
        b.skip(LocalDate.of(2026, 11, 5), TODAY, 9);
        int publishedBefore = repo.published.size();
        assertThrows(SubscriptionAggregateDemo.ConcurrentModification.class, () -> repo.save(b));
        assertEquals(publishedBefore, repo.published.size());
        assertTrue(repo.load(sub.id()).orElseThrow().deliversOn(LocalDate.of(2026, 11, 5)));
    }

    @Test
    void billingCountsOnlyDeliveryDays() {
        var s = repo.load(sub.id()).orElseThrow();
        // November 2026 has 30 days and 5 Sundays: 25 deliveries of 1 litre at Rs 68
        assertEquals(25 * 6_800, new SubscriptionAggregateDemo.MonthlyBilling().billFor(s, LocalDate.of(2026, 11, 1)).paise());
    }

    @Test
    void litresAreAValueObjectWithLimits() {
        assertThrows(IllegalArgumentException.class, () -> new SubscriptionAggregateDemo.Litres(6));
    }

    @Test
    void cartEnforcesOneCouponAndFreezesAfterCheckout() {
        var cart = new CartAggregateDemo.Cart("C");
        cart.add(new CartAggregateDemo.Sku("MILK-1L"), 1, new CartAggregateDemo.Money(6_800));
        cart.applyCoupon("FRESH10");
        assertThrows(IllegalStateException.class, () -> cart.applyCoupon("OTHER"));
        cart.checkout();
        assertThrows(IllegalStateException.class, () -> cart.add(new CartAggregateDemo.Sku("X"), 1, new CartAggregateDemo.Money(1)));
    }
}
```

## 15. Observability & Operations

Log domain events (type and aggregate ID) as structured logs; they make an excellent audit trail. Count invariant violations by rule (`skip_refused{reason="cutoff"}`) to show product teams where users struggle. Track optimistic-lock conflicts per aggregate type; spikes suggest contention or oversized aggregates.

## 16. Version Notes

| Year / version | Change |
|----------------|--------|
| 2003 | Evans, *Domain-Driven Design* |
| 2011 | Vernon's "Effective Aggregate Design" essays |
| 2013 | Vernon, *Implementing Domain-Driven Design* |
| Java 16 | Records: concise value objects and events |
| Java 17 | Sealed types: closed event hierarchies |
| Java 21 | Pattern matching for `switch`: exhaustive event handling |
| Hibernate 6.2 / Jakarta Persistence 3.2 | Records as embeddables |

## 17. Expert Deep Dive

- **Invariants vs policies.** Invariants must hold within the aggregate after every transaction; policies ("when an order is placed, award points") can be eventually consistent and belong in event handlers.
- **Reconstitution.** Loading from storage must not re-run "new" rules or record events; use a separate path (package-private constructor, factory method) for repositories.
- **Aggregate design by contention.** Look at which data is edited together and by whom; the boundary that minimises concurrent edits while keeping invariants is usually right.
- **Event granularity.** Fine-grained events (`ItemAdded`) serve analytics; coarse events (`CheckedOut`) serve integration. Don't publish internal events to other contexts unchanged.
- **Modelling with types.** Sealed types make illegal states unrepresentable (`Active`, `Paused(from, to)`, `Cancelled(reason)` instead of nullable fields).

> 📖 **War story: the 60-item orders**  
> FreshCart's `Order` aggregate enforced a 30-item limit in `addLine`. A new B2B bulk-order feature, written under deadline, built orders by calling `order.getLines().addAll(importedLines)`; the getter returned Hibernate's managed `PersistentBag`. For three weeks, cafés placed 60-item orders that didn't fit in delivery vans; riders had to make second trips at FreshCart's cost. **Fixes:** the getter now returns an unmodifiable view; bulk orders got their own `addLines` method that checks limits (with a higher B2B limit as an explicit rule); an ArchUnit test forbids public methods returning mutable collections from aggregate roots. **Lesson:** an invariant is only as strong as the encapsulation around it.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Entity vs value object?** **Identity** vs **value equality**.
2. **Why make value objects immutable?** **Safe sharing**, no surprise changes.
3. **What's an aggregate root?** The **entry point** that enforces the aggregate's rules.
4. **What's a domain event?** A **business fact that happened**, in the past tense.
5. **What's a repository in DDD?** Collection-like access to **whole aggregates**.

**▸ 🟡 L3**

1. **Why reference other aggregates by ID?** To **avoid cross-aggregate changes** and huge object graphs.
2. **Domain service vs application service?** **Business rules** vs **orchestration/transactions**.
3. **When are domain events dispatched?** **After the aggregate is saved** (commit).
4. **How do you protect invariants?** **Encapsulation**: private state, read-only views, methods that check rules.
5. **Why typed IDs?** **Type safety**: can't pass an `OrderId` where a `CustomerId` is expected.

**▸ 🔴 L4–L5**

1. **How do you choose aggregate boundaries?** **True invariants + contention patterns**; keep them small.
2. **What if a business rule spans two aggregates?** **Eventual consistency with events and compensation**, or reconsider the boundary.
3. **How do you handle concurrent edits?** **Optimistic locking** with conflict handling in the UI.
4. **When is tactical DDD overkill?** **CRUD and reporting**; little behaviour to model.
5. **How do you persist rich models with JPA without leaking?** **Embeddables, field access, protected constructors**, or separate persistence models.

**▸ 💻 Hands-on**

**Task:** Add a `changeLitres(Litres, LocalDate effectiveFrom)` rule to `Subscription`: changes take effect from the day after tomorrow at the earliest, and record a `LitresChanged` event.

> ✅ **Solution**  
> ```java
> record LitresChanged(SubscriptionId id, int litres, LocalDate from) implements Event {}   // add to the sealed list
>
> void changeLitres(Litres newLitres, LocalDate from, LocalDate today) {
>     if (from.isBefore(today.plusDays(2))) throw new IllegalStateException("changes apply from " + today.plusDays(2));
>     if (newLitres.equals(litres)) return;
>     litres = newLitres;            // a fuller model would keep a schedule of changes by date
>     events.add(new LitresChanged(id, newLitres.value(), from));
> }
> ```

## 19. Practice

- [ ] Replace `String` IDs in a project with typed ID records and see what the compiler finds.
    > 💡 **Hint**  
    > Start with the most frequently passed ID.

- [ ] Map `Money` as a JPA `@Embeddable` record and persist an aggregate with it.
    > 💡 **Hint**  
    > Hibernate 6.2+ supports records as embeddables.

- [ ] Model subscription status as a sealed type (`Active`, `Paused`, `Cancelled`) instead of nullable dates.
    > 💡 **Hint**  
    > Pattern-match in `deliversOn`.

- [ ] Find an aggregate in your code base that's edited concurrently by different roles and propose a split.
    > 💡 **Hint**  
    > Look at optimistic-lock failures in logs.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Entity: identity; value object: immutable, equal by value, self-validating.
> - Aggregate: one root enforces invariants; small; referenced by ID; one per transaction.
> - Domain events: recorded by aggregates, dispatched after save.
> - Domain service: stateless rule spanning objects; application service orchestrates.
> - Factory creates valid aggregates; repository loads/saves whole aggregates.
>
> ```java
> record Money(long paise) {
>     Money { if (paise < 0) throw new IllegalArgumentException("negative"); }
>     Money plus(Money o) { return new Money(paise + o.paise); }
> }
> ```

## 21. Further Reading

- Eric Evans, *Domain-Driven Design* (2003).
- Vaughn Vernon, *Implementing Domain-Driven Design* (2013) and ["Effective Aggregate Design"](https://www.dddcommunity.org/library/vernon_2011/).
- Scott Wlaschin, *Domain Modeling Made Functional* (2018), for type-driven modelling ideas that translate well to Java 21.
- [jMolecules](https://github.com/xmolecules/jmolecules) for expressing DDD concepts in Java.
- [Spring Data: publishing events from aggregate roots](https://docs.spring.io/spring-data/commons/reference/repositories/core-domain-events.html).

---

[← Previous: Architectural Patterns](architectural-patterns.md) · [Next: Domain-Driven Design: Strategic Patterns →](ddd-strategic.md)
