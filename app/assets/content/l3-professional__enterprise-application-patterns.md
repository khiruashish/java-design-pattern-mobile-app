# Enterprise Application Patterns

L3 🟡 Professional · ⏱ 45 min read · Prerequisites: [Dependency Injection](../l2-practitioner/dependency-injection.md), [Concurrency Patterns](concurrency-patterns.md)

> 📌 **TL;DR**  
> Martin Fowler's *Patterns of Enterprise Application Architecture* (PoEAA, 2002) names the building blocks of almost every business application: how to organise **business logic** (Transaction Script or Domain Model), where **use cases** live (Service Layer), and how objects reach the **database** (Repository, Data Mapper or Active Record, Unit of Work, Identity Map, Lazy Load), plus how data crosses **boundaries** (DTOs and assemblers). JPA/Hibernate and Spring Data implement most of them for you, so knowing them explains what your ORM is doing, why N+1 queries and lost updates happen, and when to step outside the framework.

## 1. What It Is

**Plain English.** A grocery app is mostly "take a request, apply business rules, save data, return an answer". These patterns are the standard answers to the questions every such app faces: where do the rules go, how do objects get in and out of tables, how do we keep many changes consistent, and what do we send to the mobile app?

**Definition.** The patterns on this page, by layer:

| Layer | Pattern | One line |
|-------|---------|----------|
| Domain logic | Transaction Script | One procedure per use case |
| Domain logic | Domain Model | Objects with data and behaviour enforce the rules |
| Application | Service Layer | The set of use cases; owns transactions |
| Data source | Repository | Collection-like access to aggregates |
| Data source | Data Mapper / Active Record | Separate mapper vs object that saves itself |
| Object-relational behaviour | Unit of Work | Track changes, write them together |
| Object-relational behaviour | Identity Map | One object per row per unit of work |
| Object-relational behaviour | Lazy Load | Load related data when first used |
| Distribution | DTO and assembler | Boundary-shaped data and its conversion |

## 2. Why It Exists

Business applications combine three hard things: complex, changing rules; relational databases whose shape differs from objects (the "object-relational impedance mismatch"); and many concurrent users. Without structure, SQL, rules and HTTP code tangle together, rules get duplicated across endpoints, and concurrency bugs (lost updates, half-saved orders) appear. These patterns separate the concerns so each can change independently, and give teams shared names for the pieces.

## 3. Mental Model / Analogy

**Analogy.** A supermarket. Shop-floor staff follow the store's rules (Domain Model). The customer-service desk handles requests start to finish (Service Layer). The stockroom clerk fetches and shelves items without the customer seeing the stockroom (Repository, Data Mapper). The clerk writes changes on a clipboard and updates the ledger once at closing (Unit of Work), never keeps two cards for the same item (Identity Map), and fetches a pallet's contents only when someone opens it (Lazy Load). Receipts show what the customer needs, not supplier costs (DTO).

**Technical model.** Requests enter through controllers, call the **service layer**, which opens a **unit of work**, loads aggregates via **repositories** (mapped by a **data mapper**, cached in an **identity map**), calls domain methods, then commits; the result leaves as **DTOs**.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Transaction Script vs. Domain Model

**Description.** A **Transaction Script** puts each use case's logic in one procedure that reads data, applies rules and writes results. A **Domain Model** puts rules into objects (`Order`, `Line`) that protect their own state, so every use case that touches an order gets the same rules. Scripts are simpler for simple logic; domain models win when rules are many, shared and changing.

**Advantages / features.** Scripts: obvious, fast to write. Domain model: rules in one place, invalid states impossible, easy unit tests.

**Before (Transaction Script) and after (Domain Model):**

**TransactionScriptVsDomainModelDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The same "place order" rule written as a Transaction Script and as a Domain Model. */
public class TransactionScriptVsDomainModelDemo {

    // ---- Before: Transaction Script. One procedure per use case, data in plain maps. ----
    static Map<String, Object> placeOrderScript(List<Map<String, Object>> lines, String coupon) {
        long total = 0;
        for (Map<String, Object> line : lines) {
            long price = (Long) line.get("price");
            int qty = (Integer) line.get("qty");
            total += price * qty;
        }
        if ("FRESH10".equals(coupon) && total >= 50_000) total -= total / 10;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "PLACED");
        row.put("total", total);
        return row;   // nothing stops another script setting qty = -3 or status = "SHIPPED" twice
    }

    // ---- After: Domain Model. Data and the rules that protect it live together. ----
    record Line(String sku, long price, int qty) {
        Line {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive: " + sku);
        }
        long amount() { return price * qty; }
    }

    static final class Order {
        enum Status { DRAFT, PLACED, CANCELLED }
        private final List<Line> lines = new ArrayList<>();
        private String coupon;
        private Status status = Status.DRAFT;

        void add(String sku, long price, int qty) {
            requireDraft();
            lines.add(new Line(sku, price, qty));
        }
        void applyCoupon(String code) { requireDraft(); coupon = code; }
        long subtotal() { return lines.stream().mapToLong(Line::amount).sum(); }
        long total() {
            long s = subtotal();
            return "FRESH10".equals(coupon) && s >= 50_000 ? s - s / 10 : s;
        }
        void place() {
            requireDraft();
            if (lines.isEmpty()) throw new IllegalStateException("empty order");
            status = Status.PLACED;
        }
        Status status() { return status; }
        private void requireDraft() {
            if (status != Status.DRAFT) throw new IllegalStateException("order is " + status);
        }
    }

    public static void main(String[] args) {
        var scriptResult = placeOrderScript(List.of(
                Map.of("sku", "MILK-1L", "price", 6_800L, "qty", 4),
                Map.of("sku", "ATTA-5KG", "price", 32_000L, "qty", 1)), "FRESH10");
        System.out.println("script: " + scriptResult);

        Order order = new Order();
        order.add("MILK-1L", 6_800, 4);
        order.add("ATTA-5KG", 32_000, 1);
        order.applyCoupon("FRESH10");
        order.place();
        System.out.println("domain: status=" + order.status() + ", total=" + order.total());

        try {
            order.add("EGGS-12", 9_000, 1);
        } catch (IllegalStateException e) {
            System.out.println("domain refuses: " + e.getMessage());
        }
        try {
            new Order().add("EGGS-12", 9_000, -3);
        } catch (IllegalArgumentException e) {
            System.out.println("domain refuses: " + e.getMessage());
        }
    }
}
```

1. The script computes correctly, but its data is a plain map: other scripts can put anything in it.
2. The domain object owns its lines, coupon and status; there's no way to change them except through its methods.
3. State transitions are guarded: a placed order can't get new lines.

**Run it:** `java TransactionScriptVsDomainModelDemo.java`

```text
script: {status=PLACED, total=53280}
domain: status=PLACED, total=53280
domain refuses: order is PLACED
domain refuses: qty must be positive: EGGS-12
```

| | Transaction Script | Domain Model |
|-|--------------------|--------------|
| Logic lives in | Service procedures | Entities and value objects |
| Good for | CRUD, reports, simple flows | Complex, shared, evolving rules |
| Duplication risk | High as use cases grow | Low |
| Persistence | Easy (plain SQL) | Needs mapping (ORM or mapper) |
| Anaemic-model smell | n/a | When entities are only getters/setters |

**Use** scripts for simple CRUD and batch jobs; a domain model for the core business (pricing, ordering, slots). **Avoid** an anaemic domain model: entities with only getters and setters plus "service" scripts doing everything is a Transaction Script with extra layers.
**Real-world Java:** Spring Boot apps with `@Entity` classes and `@Service` scripts (often anaemic); DDD-style aggregates in Axon or jMolecules-based projects.
**Best practice:** start with scripts, and move rules into objects once two use cases need the same rule. **Gotcha:** JPA requires a no-arg constructor and often setters; keep them `protected` so domain invariants aren't bypassed.

### Service Layer

**Description.** The Service Layer defines the application's boundary: one method per use case (`placeOrder`, `cancelOrder`). It coordinates repositories, domain objects and external systems, and owns the **transaction** boundary. Business rules stay in the domain; the service sequences them.

**Advantages / features.** One place for transactions, security checks and orchestration; every client (REST, mobile, batch, messaging) calls the same use cases.

**ServiceLayerDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Service Layer defines the application's use cases and owns the transaction around each one. */
public class ServiceLayerDemo {

    static final List<String> log = new ArrayList<>();

    /** Stands in for Spring's @Transactional: begin, then commit or roll back. */
    static <T> T inTransaction(String name, java.util.function.Supplier<T> work) {
        log.add("BEGIN " + name);
        try {
            T result = work.get();
            log.add("COMMIT");
            return result;
        } catch (RuntimeException e) {
            log.add("ROLLBACK (" + e.getMessage() + ")");
            throw e;
        }
    }

    record Order(String id, String customer, long total) {}

    interface OrderRepository { void save(Order o); }
    interface StockGateway { void reserve(String sku, int qty); }
    interface Notifier { void orderPlaced(Order o); }

    /** Thin coordination only. Pricing rules belong to the domain, not here. */
    static final class CheckoutService {
        private final OrderRepository orders;
        private final StockGateway stock;
        private final Notifier notifier;
        private int nextId = 1;

        CheckoutService(OrderRepository orders, StockGateway stock, Notifier notifier) {
            this.orders = orders; this.stock = stock; this.notifier = notifier;
        }

        String placeOrder(String customer, String sku, int qty, long unitPrice) {
            Order order = inTransaction("placeOrder", () -> {
                stock.reserve(sku, qty);
                Order o = new Order("ORD-" + nextId++, customer, unitPrice * qty);
                orders.save(o);
                return o;
            });
            notifier.orderPlaced(order);
            return order.id();
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> shelf = new LinkedHashMap<>(Map.of("PANEER-200G", 3));
        Map<String, Order> table = new LinkedHashMap<>();
        CheckoutService service = new CheckoutService(
                o -> { table.put(o.id(), o); log.add("INSERT order " + o.id()); },
                (sku, qty) -> {
                    int left = shelf.getOrDefault(sku, 0);
                    if (left < qty) throw new IllegalStateException("only " + left + " " + sku);
                    shelf.put(sku, left - qty);
                    log.add("UPDATE stock " + sku + " -> " + (left - qty));
                },
                o -> log.add("SMS to " + o.customer() + ": " + o.id() + " placed"));

        System.out.println("placed " + service.placeOrder("asha", "PANEER-200G", 2, 9_500));
        try {
            service.placeOrder("ravi", "PANEER-200G", 5, 9_500);
        } catch (IllegalStateException e) {
            System.out.println("failed: " + e.getMessage());
        }
        log.forEach(line -> System.out.println("  " + line));
        System.out.println("orders saved: " + table.keySet());
    }
}
```

1. A stand-in for `@Transactional`: begin, then commit or roll back on exception.
2. The service sequences the steps; it holds no pricing or stock rules.
3. The SMS goes out **after** commit, so a rolled-back order never sends "placed".

**Run it:** `java ServiceLayerDemo.java`

```text
placed ORD-1
failed: only 1 PANEER-200G
  BEGIN placeOrder
  UPDATE stock PANEER-200G -> 1
  INSERT order ORD-1
  COMMIT
  SMS to asha: ORD-1 placed
  BEGIN placeOrder
  ROLLBACK (only 1 PANEER-200G)
orders saved: [ORD-1]
```

| Responsibility | Service layer | Domain model | Controller |
|----------------|---------------|--------------|------------|
| Transaction boundary | ✅ | | |
| Business rules | | ✅ | |
| Authorisation per use case | ✅ | | sometimes |
| HTTP/JSON mapping | | | ✅ |
| Calling external systems | ✅ (via ports) | | |

**Use** a service layer when several clients share use cases or transactions span repositories. **Avoid** services that just forward to a repository; that's an extra layer for nothing.
**Real-world Java:** Spring `@Service` + `@Transactional`; Jakarta EE stateless session beans.
**Best practice:** name services after use cases, not tables (`CheckoutService`, not `OrderTableService`). **Gotcha:** sending emails or messages inside the transaction means they go out even when it later rolls back; use `@TransactionalEventListener(phase = AFTER_COMMIT)` or an outbox.

### Repository

**Description.** A Repository gives the domain a collection-like interface (`findById`, `save`, `findAvailableInZone`) for aggregates, hiding how they're stored. Implementations can use JDBC, JPA, a document store or memory. Query methods speak the domain's language.

**Advantages / features.** Domain code free of SQL; fast in-memory implementations for tests; storage can change behind the interface.

**RepositoryDemo.java**

```java
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A Repository looks like an in-memory collection of aggregates; callers never see SQL or tables. */
public class RepositoryDemo {

    record Rider(String id, String name, String zone, boolean available, double rating) {}

    interface RiderRepository {
        Optional<Rider> findById(String id);
        List<Rider> findAvailableInZone(String zone);
        void save(Rider rider);
    }

    /** Fast, deterministic implementation for tests and this demo. A JDBC or JPA one has the same interface. */
    static final class InMemoryRiderRepository implements RiderRepository {
        private final Map<String, Rider> rows = new TreeMap<>();
        public Optional<Rider> findById(String id) { return Optional.ofNullable(rows.get(id)); }
        public List<Rider> findAvailableInZone(String zone) {
            return rows.values().stream()
                    .filter(r -> r.available() && r.zone().equals(zone))
                    .sorted(Comparator.comparingDouble(Rider::rating).reversed())
                    .toList();
        }
        public void save(Rider rider) { rows.put(rider.id(), rider); }
    }

    /** Domain-facing code depends only on the interface. */
    static Optional<Rider> bestRiderFor(RiderRepository riders, String zone) {
        return riders.findAvailableInZone(zone).stream().findFirst();
    }

    public static void main(String[] args) {
        RiderRepository riders = new InMemoryRiderRepository();
        riders.save(new Rider("R1", "Arjun", "KORAMANGALA", true, 4.6));
        riders.save(new Rider("R2", "Bina", "KORAMANGALA", true, 4.9));
        riders.save(new Rider("R3", "Chetan", "INDIRANAGAR", true, 4.8));
        riders.save(new Rider("R4", "Divya", "KORAMANGALA", false, 5.0));

        System.out.println("available in Koramangala: "
                + riders.findAvailableInZone("KORAMANGALA").stream().map(Rider::name).toList());
        System.out.println("best for Koramangala: " + bestRiderFor(riders, "KORAMANGALA").map(Rider::name).orElse("none"));
        System.out.println("best for HSR: " + bestRiderFor(riders, "HSR").map(Rider::name).orElse("none"));
        System.out.println("R9 exists: " + riders.findById("R9").isPresent());
    }
}
```

1. The interface is in the domain's language and exposes no storage concepts.
2. An in-memory implementation is perfect for tests; a JPA one would implement the same interface.
3. Business code works with any implementation.

**Run it:** `java RepositoryDemo.java`

```text
available in Koramangala: [Bina, Arjun]
best for Koramangala: Bina
best for HSR: none
R9 exists: false
```

| | Repository | DAO |
|-|------------|-----|
| Granularity | One per aggregate | Often one per table |
| Language | Domain (`findOverdueOrders`) | Data (`selectByStatusAndDate`) |
| Returns | Domain objects | Rows, entities or DTOs |
| Typical home | Domain layer interface, infra implementation | Data access layer |

**Use** repositories for aggregates in a domain model. **Avoid** generic repositories exposing arbitrary queries everywhere (`findAll` with 50 filter parameters); that leaks storage concerns.
**Real-world Java:** Spring Data `JpaRepository`, `CrudRepository`, derived query methods; Micronaut Data.
**Best practice:** keep repository interfaces in the domain package and implementations in infrastructure. **Gotcha:** Spring Data's `findAll()` on a large table loads everything into memory; page or stream instead.

### Data Mapper vs. Active Record

**Description.** With **Active Record**, each object wraps a table row and has `save()`/`find()` methods; simple and quick, but domain and database are coupled. With a **Data Mapper**, a separate mapper moves data between objects and tables, so the domain object knows nothing about storage and table design can differ from object design.

**Advantages / features.** Active Record: minimal code for CRUD. Data Mapper: rich domain objects, independent schema, persistence-free unit tests.

**DataMapperVsActiveRecordDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Active Record: the object saves itself. Data Mapper: a separate class moves data between objects and tables. */
public class DataMapperVsActiveRecordDemo {

    /** A tiny "database": table name -> primary key -> row. Every statement is logged. */
    static final Map<String, Map<String, Map<String, Object>>> DB = new TreeMap<>();
    static final List<String> SQL = new ArrayList<>();

    static void upsert(String table, String id, Map<String, Object> row) {
        SQL.add("UPSERT " + table + " " + new TreeMap<>(row));
        DB.computeIfAbsent(table, t -> new TreeMap<>()).put(id, row);
    }

    // ---- Active Record: one class = one table row, with persistence methods on it. ----
    static final class ProductRecord {
        String sku; String name; long pricePaise;
        ProductRecord(String sku, String name, long pricePaise) { this.sku = sku; this.name = name; this.pricePaise = pricePaise; }
        void save() { upsert("product", sku, Map.of("sku", sku, "name", name, "price_paise", pricePaise)); }
        static ProductRecord find(String sku) {
            var row = DB.get("product").get(sku);
            return new ProductRecord((String) row.get("sku"), (String) row.get("name"), (Long) row.get("price_paise"));
        }
    }

    // ---- Data Mapper: the domain object knows nothing about storage. ----
    record Money(long paise) {
        Money { if (paise < 0) throw new IllegalArgumentException("negative price"); }
        @Override public String toString() { return "Rs " + paise / 100 + "." + String.format("%02d", paise % 100); }
    }
    record Product(String sku, String name, Money price) {
        Product withDiscount(int percent) { return new Product(sku, name, new Money(price.paise() * (100 - percent) / 100)); }
    }

    static final class ProductMapper {
        void insert(Product p) {
            upsert("catalog_item", p.sku(), Map.of("item_code", p.sku(), "title", p.name(), "mrp_paise", p.price().paise()));
        }
        Product find(String sku) {
            var row = DB.get("catalog_item").get(sku);
            return new Product((String) row.get("item_code"), (String) row.get("title"), new Money((Long) row.get("mrp_paise")));
        }
    }

    public static void main(String[] args) {
        new ProductRecord("CURD-400G", "Curd 400g", 4_500).save();
        ProductRecord curd = ProductRecord.find("CURD-400G");
        curd.pricePaise = -100;                     // nothing protects the row's rules
        curd.save();

        ProductMapper mapper = new ProductMapper();
        mapper.insert(new Product("GHEE-1L", "Ghee 1L", new Money(65_000)));
        Product ghee = mapper.find("GHEE-1L").withDiscount(10);
        mapper.insert(ghee);
        System.out.println("domain object: " + ghee);
        SQL.forEach(s -> System.out.println("  " + s));
    }
}
```

1. Active Record: fields mirror columns and the object saves itself, so a negative price is saved happily.
2. The domain object validates its value and has behaviour, but no persistence code.
3. The mapper translates between the domain's names and the table's (`sku` to `item_code`, `price` to `mrp_paise`).

**Run it:** `java DataMapperVsActiveRecordDemo.java`

```text
domain object: Product[sku=GHEE-1L, name=Ghee 1L, price=Rs 585.00]
  UPSERT product {name=Curd 400g, price_paise=4500, sku=CURD-400G}
  UPSERT product {name=Curd 400g, price_paise=-100, sku=CURD-400G}
  UPSERT catalog_item {item_code=GHEE-1L, mrp_paise=65000, title=Ghee 1L}
  UPSERT catalog_item {item_code=GHEE-1L, mrp_paise=58500, title=Ghee 1L}
```

| | Active Record | Data Mapper |
|-|---------------|-------------|
| Domain/DB coupling | Tight | Loose |
| Code for CRUD | Least | More (or an ORM) |
| Testing without DB | Hard | Easy |
| Schema differs from objects | Painful | Fine |
| Examples | Rails ActiveRecord, ActiveJDBC, Panache `PanacheEntity` | Hibernate/JPA, MyBatis, jOOQ with records |

**Use** Active Record for simple CRUD apps and admin tools. **Avoid** it for complex domains, where persistence concerns crowd out business logic.
**Real-world Java:** JPA (Hibernate) is a Data Mapper with annotations; Quarkus Panache offers an Active Record style on top of it.
**Best practice:** keep mapping configuration (annotations or XML) out of business methods. **Gotcha:** JPA entity annotations still couple the class to its table; truly independent models need separate persistence classes and explicit mapping.

### Unit of Work

**Description.** A Unit of Work records objects that are new, changed ("dirty") or removed during a business transaction, then writes everything in one database transaction at commit, in the right order, with redundant writes collapsed. If anything fails, nothing is written.

**Advantages / features.** Fewer round trips (batched writes); consistency; callers don't call `save` after every change.

**UnitOfWorkDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A Unit of Work tracks what changed during a business transaction and writes it all at once, or not at all. */
public class UnitOfWorkDemo {

    static final class Item {
        final String sku; int stock;
        Item(String sku, int stock) { this.sku = sku; this.stock = stock; }
    }

    static final class UnitOfWork {
        private final Set<Item> newItems = new LinkedHashSet<>();
        private final Set<Item> dirty = new LinkedHashSet<>();
        private final Set<Item> removed = new LinkedHashSet<>();
        final List<String> executed = new ArrayList<>();

        void registerNew(Item i) { newItems.add(i); }
        void registerDirty(Item i) { if (!newItems.contains(i)) dirty.add(i); }
        void registerRemoved(Item i) {
            if (newItems.remove(i)) return;            // created and deleted in the same unit: no SQL at all
            dirty.remove(i);
            removed.add(i);
        }

        void commit() {
            List<String> batch = new ArrayList<>();
            newItems.forEach(i -> batch.add("INSERT item(" + i.sku + ", " + i.stock + ")"));
            dirty.forEach(i -> batch.add("UPDATE item SET stock=" + i.stock + " WHERE sku=" + i.sku));
            removed.forEach(i -> batch.add("DELETE item WHERE sku=" + i.sku));
            executed.add("BEGIN");
            executed.addAll(batch);
            executed.add("COMMIT");
            newItems.clear(); dirty.clear(); removed.clear();
        }
        void rollback() { newItems.clear(); dirty.clear(); removed.clear(); executed.add("(nothing written)"); }
    }

    public static void main(String[] args) {
        Item milk = new Item("MILK-1L", 40), bread = new Item("BREAD", 12), stale = new Item("CAKE-OLD", 2);

        UnitOfWork uow = new UnitOfWork();
        Item eggs = new Item("EGGS-12", 30);
        uow.registerNew(eggs);
        milk.stock -= 2; uow.registerDirty(milk);
        milk.stock -= 1; uow.registerDirty(milk);      // second change, still one UPDATE
        eggs.stock -= 6; uow.registerDirty(eggs);      // new item: its INSERT already has the latest value
        uow.registerRemoved(stale);
        Item temp = new Item("TEMP", 1);
        uow.registerNew(temp); uow.registerRemoved(temp);
        uow.commit();
        uow.executed.forEach(s -> System.out.println("  " + s));

        UnitOfWork failed = new UnitOfWork();
        bread.stock -= 50; failed.registerDirty(bread);
        if (bread.stock < 0) failed.rollback();
        System.out.println("second unit: " + failed.executed);
    }
}
```

1. Three sets track what changed, in insertion order.
2. Changing an object twice still produces one UPDATE; a new object's INSERT already contains its latest values.
3. Commit writes inserts, updates and deletes in one transaction; an object created and removed in the same unit produces no SQL at all.

**Run it:** `java UnitOfWorkDemo.java`

```text
  BEGIN
  INSERT item(EGGS-12, 24)
  UPDATE item SET stock=37 WHERE sku=MILK-1L
  DELETE item WHERE sku=CAKE-OLD
  COMMIT
second unit: [(nothing written)]
```

| Tracking style | How changes are found | Example |
|----------------|----------------------|---------|
| Caller registration | Code calls `registerDirty` | This demo, hand-rolled DAOs |
| Snapshot comparison | Compare with loaded copy at flush | Hibernate's default dirty checking |
| Bytecode enhancement | Setters mark the object dirty | Hibernate enhancement, EclipseLink weaving |

**Use** a unit of work for use cases that change several objects. **Avoid** long-lived units (minutes, whole sessions): memory grows, and data goes stale.
**Real-world Java:** JPA `EntityManager`/Hibernate `Session` (flush at commit), Spring's `@Transactional` scope.
**Best practice:** one unit per request or use case. **Gotcha:** in JPA, changing a managed entity is saved at commit even if you never call `save()`; surprising when code modifies an entity "just for display".

### Identity Map

**Description.** An Identity Map keeps every object loaded in a unit of work, keyed by ID. Loading the same row twice returns the same object, so changes made by different parts of the code can't conflict, and repeated lookups skip the database.

**Advantages / features.** No lost updates within a unit; fewer queries; object identity (`==`) matches row identity.

**IdentityMapDemo.java**

```java
import java.util.HashMap;
import java.util.Map;

/** An Identity Map makes sure each database row is loaded into exactly one object per unit of work. */
public class IdentityMapDemo {

    static final Map<String, Integer> TABLE = new HashMap<>(Map.of("W-ASHA", 50_000));
    static int queries = 0;

    static final class Wallet {
        final String id; int balance;
        Wallet(String id, int balance) { this.id = id; this.balance = balance; }
    }

    static Wallet loadFromDb(String id) { queries++; return new Wallet(id, TABLE.get(id)); }

    static final class WalletFinder {
        private final Map<String, Wallet> identityMap = new HashMap<>();
        Wallet find(String id) { return identityMap.computeIfAbsent(id, IdentityMapDemo::loadFromDb); }
    }

    public static void main(String[] args) {
        // Before: two parts of one request load the same row into two objects.
        Wallet forRefund = loadFromDb("W-ASHA");
        Wallet forPayment = loadFromDb("W-ASHA");
        forRefund.balance += 10_000;
        forPayment.balance -= 30_000;
        TABLE.put("W-ASHA", forRefund.balance);
        TABLE.put("W-ASHA", forPayment.balance);
        System.out.println("without map: same object=" + (forRefund == forPayment)
                + ", saved balance=" + TABLE.get("W-ASHA") + " (expected 30000), queries=" + queries);

        // After: both parts get the same object; both changes survive.
        TABLE.put("W-ASHA", 50_000); queries = 0;
        WalletFinder finder = new WalletFinder();
        Wallet a = finder.find("W-ASHA");
        Wallet b = finder.find("W-ASHA");
        a.balance += 10_000;
        b.balance -= 30_000;
        TABLE.put("W-ASHA", a.balance);
        System.out.println("with map:    same object=" + (a == b)
                + ", saved balance=" + TABLE.get("W-ASHA") + ", queries=" + queries);
    }
}
```

1. The map is scoped to one unit of work, not the whole application.
2. The first `find` loads and caches; later ones return the cached object.
3. Without the map, the refund and the payment each had their own copy; the last save wins and the refund is lost.

**Run it:** `java IdentityMapDemo.java`

```text
without map: same object=false, saved balance=20000 (expected 30000), queries=2
with map:    same object=true, saved balance=30000, queries=1
```

| | Identity Map | Second-level / shared cache |
|-|--------------|-----------------------------|
| Scope | One unit of work | Whole application or cluster |
| Purpose | Consistency within a transaction | Performance across transactions |
| Staleness risk | Low (short-lived) | High; needs invalidation |
| JPA | Persistence context (always on) | Hibernate L2 cache (opt-in) |

**Use** an identity map in any ORM-like layer. **Avoid** sharing it across requests; it becomes a cache with staleness and concurrency problems.
**Real-world Java:** JPA persistence context: `em.find(Order.class, 1) == em.find(Order.class, 1)` in one transaction.
**Best practice:** keep units short so the map stays small and fresh. **Gotcha:** JPQL/Criteria queries still hit the database, but return the already-managed instances, so they may not reflect changes made by native SQL in the same transaction until refreshed.

### Lazy Load

**Description.** Lazy Load defers loading related data until it's accessed: a virtual proxy, value holder or ghost object fetches on first use. It avoids loading huge object graphs, but iterating over many parents and touching each child collection causes the **N+1 query problem**: one query for parents plus one per parent.

**Advantages / features.** Cheap loading of objects whose relations aren't needed; transparent to callers.

**LazyLoadDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Lazy Load defers loading related data until it is used, and shows why that can cause N+1 queries. */
public class LazyLoadDemo {

    static final List<String> SQL = new ArrayList<>();
    static final Map<String, List<String>> ORDERS = new LinkedHashMap<>();
    static {
        ORDERS.put("asha", List.of("ORD-1", "ORD-4"));
        ORDERS.put("ravi", List.of("ORD-2"));
        ORDERS.put("meera", List.of("ORD-3", "ORD-5", "ORD-6"));
    }

    /** A value holder: loads once, on first get(). */
    static final class Lazy<T> implements Supplier<T> {
        private Supplier<T> loader; private T value;
        Lazy(Supplier<T> loader) { this.loader = loader; }
        public T get() {
            if (loader != null) { value = loader.get(); loader = null; }
            return value;
        }
    }

    record Customer(String name, Lazy<List<String>> orders) {}

    static List<Customer> loadCustomers() {
        SQL.add("SELECT * FROM customer");
        return ORDERS.keySet().stream()
                .map(n -> new Customer(n, new Lazy<>(() -> {
                    SQL.add("SELECT * FROM orders WHERE customer='" + n + "'");
                    return ORDERS.get(n);
                })))
                .toList();
    }

    public static void main(String[] args) {
        List<Customer> customers = loadCustomers();
        System.out.println("names only: " + customers.stream().map(Customer::name).toList() + ", queries=" + SQL.size());

        SQL.clear();
        customers = loadCustomers();
        int total = customers.stream().mapToInt(c -> c.orders().get().size()).sum();
        System.out.println("N+1: " + total + " orders, queries=" + SQL.size());

        SQL.clear();
        SQL.add("SELECT * FROM customer");
        SQL.add("SELECT * FROM orders WHERE customer IN ('asha','ravi','meera')");
        int batched = ORDERS.values().stream().mapToInt(List::size).sum();
        System.out.println("batch fetch: " + batched + " orders, queries=" + SQL.size());
    }
}
```

1. A value holder runs its loader once, on first access.
2. Showing only names costs one query; the orders are never loaded.
3. When the screen does need the orders, fetching them in one batch query beats loading them one customer at a time.

**Run it:** `java LazyLoadDemo.java`

```text
names only: [asha, ravi, meera], queries=1
N+1: 6 orders, queries=4
batch fetch: 6 orders, queries=2
```

| Fix for N+1 | JPA / Hibernate |
|-------------|-----------------|
| Join fetch | `SELECT c FROM Customer c JOIN FETCH c.orders` |
| Entity graph | `@EntityGraph(attributePaths = "orders")` |
| Batch fetching | `@BatchSize(size = 50)` or `hibernate.default_batch_fetch_size` |
| DTO projection | `SELECT new OrderSummary(...)` or a Spring Data interface projection |

**Use** lazy loading as the default for collections. **Avoid** relying on it in loops; decide fetch plans per use case.
**Real-world Java:** JPA `FetchType.LAZY` (default for collections), Hibernate proxies, Spring's `ObjectProvider`.
**Best practice:** log SQL in tests and assert query counts for list screens. **Gotcha:** accessing a lazy collection after the transaction closes throws `LazyInitializationException`; "open session in view" hides it at the cost of queries during rendering.

### DTO and assemblers

**Description.** A Data Transfer Object carries data across a boundary (API, message, UI) in exactly the shape the other side needs. An **assembler** (or mapper) converts domain objects to DTOs and requests into domain objects. DTOs keep internal fields private and decouple the API's contract from the domain's evolution.

**Advantages / features.** No leaking of internal data; stable API contracts; fewer round trips (one DTO per screen).

**DtoAssemblerDemo.java**

```java
import java.util.List;

/** DTOs carry exactly the data a boundary needs; an assembler converts between DTOs and domain objects. */
public class DtoAssemblerDemo {

    // Domain objects: rich, with internal data that must not leak.
    record Line(String sku, String name, int qty, long pricePaise, long costPaise) {}
    record Order(String id, String customerPhone, String address, List<Line> lines, String internalFraudScore) {
        long total() { return lines.stream().mapToLong(l -> l.qty() * l.pricePaise()).sum(); }
    }

    // DTOs: flat, immutable, shaped for one screen or API.
    record OrderSummaryDto(String orderId, String maskedPhone, int items, String total) {}
    record PlaceOrderRequest(String phone, String address, List<String> skus) {}

    static final class OrderAssembler {
        static OrderSummaryDto toSummary(Order o) {
            String phone = o.customerPhone();
            String masked = "******" + phone.substring(phone.length() - 4);
            int items = o.lines().stream().mapToInt(Line::qty).sum();
            return new OrderSummaryDto(o.id(), masked, items, "Rs " + o.total() / 100);
        }
        static Order fromRequest(String newId, PlaceOrderRequest r, java.util.function.Function<String, Line> catalog) {
            if (r.skus().isEmpty()) throw new IllegalArgumentException("no items");
            return new Order(newId, r.phone(), r.address(), r.skus().stream().map(catalog).toList(), "unscored");
        }
    }

    public static void main(String[] args) {
        var request = new PlaceOrderRequest("9876543210", "12 MG Road", List.of("MILK-1L", "MILK-1L", "BREAD"));
        Order order = OrderAssembler.fromRequest("ORD-77", request,
                sku -> sku.equals("BREAD") ? new Line(sku, "Bread", 1, 4_500, 3_100) : new Line(sku, "Milk 1L", 1, 6_800, 5_900));
        System.out.println("domain has cost and fraud data: " + (order.lines().get(0).costPaise() > 0) + ", " + order.internalFraudScore());
        System.out.println("api returns: " + OrderAssembler.toSummary(order));
    }
}
```

1. The response DTO contains only what the app shows, with the phone masked.
2. The request DTO is what the client sends; it isn't trusted as a domain object.
3. The assembler is the one place that knows both shapes.

**Run it:** `java DtoAssemblerDemo.java`

```text
domain has cost and fraud data: true, unscored
api returns: OrderSummaryDto[orderId=ORD-77, maskedPhone=******3210, items=3, total=Rs 181]
```

| Mapping approach | Pros | Cons |
|------------------|------|------|
| Hand-written assembler | Explicit, debuggable | Boilerplate |
| MapStruct | Compile-time generated, fast | Annotation config to learn |
| ModelMapper (reflection) | Little code | Runtime surprises, slower |
| JPA/Spring Data projections | No entity loaded at all | Tied to persistence layer |

**Use** DTOs at every external boundary. **Avoid** returning JPA entities from REST controllers: lazy-loading errors, leaked fields and an API that changes whenever the table does.
**Real-world Java:** Java records as DTOs, MapStruct, Spring Data projections, Jackson views.
**Best practice:** separate request and response DTOs, and validate requests (`@Valid`) at the edge. **Gotcha:** mass assignment: binding request JSON straight onto an entity lets clients set fields like `role` or `price`.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** GreenValley farm tracks crates of produce and needs "how many kg of tomatoes do we have?"

**FarmStockDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** GreenValley farm: a Repository hides storage and a Service Layer exposes one use case. */
public class FarmStockDemo {

    record Crate(String id, String produce, int kg) {}

    interface CrateRepository {
        void save(Crate c);
        Optional<Crate> findById(String id);
        List<Crate> findByProduce(String produce);
    }

    static final class InMemoryCrates implements CrateRepository {
        private final Map<String, Crate> rows = new TreeMap<>();
        public void save(Crate c) { rows.put(c.id(), c); }
        public Optional<Crate> findById(String id) { return Optional.ofNullable(rows.get(id)); }
        public List<Crate> findByProduce(String p) { return rows.values().stream().filter(c -> c.produce().equals(p)).toList(); }
    }

    record StockService(CrateRepository crates) {
        int kgAvailable(String produce) { return crates.findByProduce(produce).stream().mapToInt(Crate::kg).sum(); }
    }

    public static void main(String[] args) {
        CrateRepository crates = new InMemoryCrates();
        crates.save(new Crate("C1", "tomato", 20));
        crates.save(new Crate("C2", "tomato", 15));
        crates.save(new Crate("C3", "spinach", 8));
        StockService stock = new StockService(crates);
        System.out.println("tomato kg: " + stock.kgAvailable("tomato"));
        System.out.println("okra kg: " + stock.kgAvailable("okra"));
        System.out.println("C3: " + crates.findById("C3").orElseThrow());
    }
}
```

1. A repository interface in the farm's language.
2. A service exposing the use case; it doesn't know crates are stored in a `TreeMap`.

**Run it:** `java FarmStockDemo.java`

**Output:**

```text
tomato kg: 35
okra kg: 0
C3: Crate[id=C3, produce=spinach, kg=8]
```

**What to notice:**

- Swapping the in-memory repository for a JDBC one changes no service code.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** checkout takes a cart DTO, reserves stock for each line, creates the order with delivery-fee rules, and returns a response DTO. If any line is out of stock, no stock is taken.

**CheckoutServiceDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** FreshCart checkout: a rich domain model, repositories, a service layer with a transaction, and DTOs at the edge. */
public class CheckoutServiceDemo {

    // ---- Domain model ----
    record Product(String sku, String name, long pricePaise, int stock) {
        Product take(int qty) {
            if (qty > stock) throw new OutOfStock(sku, stock);
            return new Product(sku, name, pricePaise, stock - qty);
        }
    }
    static final class OutOfStock extends RuntimeException {
        OutOfStock(String sku, int left) { super(sku + " has only " + left + " left"); }
    }
    record Line(String sku, int qty, long pricePaise) { long amount() { return qty * pricePaise; } }
    record Order(String id, String customer, List<Line> lines) {
        Order { if (lines.isEmpty()) throw new IllegalArgumentException("empty cart"); lines = List.copyOf(lines); }
        long subtotal() { return lines.stream().mapToLong(Line::amount).sum(); }
        long deliveryFee() { return subtotal() >= 49_900 ? 0 : 2_500; }
        long total() { return subtotal() + deliveryFee(); }
    }

    // ---- Repositories and a fake transactional store ----
    static final class Store {
        Map<String, Product> products = new TreeMap<>();
        Map<String, Order> orders = new TreeMap<>();
        <T> T transaction(java.util.function.Supplier<T> work) {
            var savedProducts = new TreeMap<>(products);
            var savedOrders = new TreeMap<>(orders);
            try { return work.get(); }
            catch (RuntimeException e) { products = savedProducts; orders = savedOrders; throw e; }
        }
    }
    record ProductRepository(Store s) {
        Optional<Product> find(String sku) { return Optional.ofNullable(s.products.get(sku)); }
        void save(Product p) { s.products.put(p.sku(), p); }
    }
    record OrderRepository(Store s) {
        void save(Order o) { s.orders.put(o.id(), o); }
        int count() { return s.orders.size(); }
    }

    // ---- DTOs ----
    record CartItem(String sku, int qty) {}
    record CheckoutRequest(String customer, List<CartItem> items) {}
    sealed interface CheckoutResponse permits Confirmed, Rejected {}
    record Confirmed(String orderId, String total, String delivery) implements CheckoutResponse {}
    record Rejected(String reason) implements CheckoutResponse {}

    // ---- Service layer ----
    static final class CheckoutService {
        private final Store store; private final ProductRepository products; private final OrderRepository orders;
        private int seq = 100;
        CheckoutService(Store store) { this.store = store; products = new ProductRepository(store); orders = new OrderRepository(store); }

        CheckoutResponse checkout(CheckoutRequest req) {
            try {
                Order order = store.transaction(() -> {
                    List<Line> lines = new ArrayList<>();
                    for (CartItem item : req.items()) {
                        Product p = products.find(item.sku()).orElseThrow(() -> new IllegalArgumentException("unknown " + item.sku()));
                        products.save(p.take(item.qty()));
                        lines.add(new Line(p.sku(), item.qty(), p.pricePaise()));
                    }
                    Order o = new Order("FC-" + seq++, req.customer(), lines);
                    orders.save(o);
                    return o;
                });
                return new Confirmed(order.id(), rupees(order.total()), order.deliveryFee() == 0 ? "free" : rupees(order.deliveryFee()));
            } catch (OutOfStock | IllegalArgumentException e) {
                return new Rejected(e.getMessage());
            }
        }
        static String rupees(long p) { return "Rs " + p / 100 + "." + String.format("%02d", p % 100); }
    }

    public static void main(String[] args) {
        Store store = new Store();
        var products = new ProductRepository(store);
        products.save(new Product("MILK-1L", "Milk 1L", 6_800, 10));
        products.save(new Product("MANGO-1KG", "Alphonso 1kg", 45_000, 2));
        CheckoutService service = new CheckoutService(store);

        System.out.println(service.checkout(new CheckoutRequest("asha", List.of(new CartItem("MILK-1L", 2)))));
        System.out.println(service.checkout(new CheckoutRequest("ravi", List.of(new CartItem("MILK-1L", 1), new CartItem("MANGO-1KG", 1)))));
        System.out.println(service.checkout(new CheckoutRequest("meera", List.of(new CartItem("MILK-1L", 3), new CartItem("MANGO-1KG", 5)))));
        System.out.println(service.checkout(new CheckoutRequest("dev", List.of())));
        System.out.println("milk left: " + products.find("MILK-1L").orElseThrow().stock()
                + ", mango left: " + products.find("MANGO-1KG").orElseThrow().stock()
                + ", orders: " + new OrderRepository(store).count());
    }
}
```

1. A fake store with a transaction that restores the previous state on failure, standing in for `@Transactional`.
2. The service layer orchestrates repositories and domain objects; stock and fee rules live in `Product` and `Order`.
3. The response DTO carries formatted amounts, not domain objects.

**Run it:** `java CheckoutServiceDemo.java`

**Output:**

```text
Confirmed[orderId=FC-100, total=Rs 161.00, delivery=Rs 25.00]
Confirmed[orderId=FC-101, total=Rs 518.00, delivery=free]
Rejected[reason=MANGO-1KG has only 1 left]
Rejected[reason=empty cart]
milk left: 7, mango left: 1, orders: 2
```

**What to notice:**

- Meera's order failed on mangoes after milk was already taken; the rollback restored the milk (7 left, not 4).
- Errors become `Rejected` DTOs; the client never sees exceptions or stack traces.

### Example 3: Production-grade

**Scenario:** ParcelGo's dispatch desks assign riders to shipments. Two dispatchers sometimes edit the same shipment at once; the second must get a clear conflict instead of silently overwriting. Reads shouldn't produce writes. Each request runs in its own unit of work and returns a DTO. This is, in miniature, what JPA does for you.

**OrderPersistenceDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * ParcelGo shipment persistence: Data Mapper + Identity Map + Unit of Work with dirty checking and
 * optimistic locking, behind a Repository, with DTOs at the service boundary. This is the core of what JPA does.
 */
public class OrderPersistenceDemo {

    // ---------- A tiny database with versioned rows and atomic batches ----------
    record Row(String id, String status, String rider, long version) {}

    static final class Database {
        final Map<String, Row> shipments = new TreeMap<>();
        final List<String> log = new ArrayList<>();
        int selects = 0;

        Optional<Row> select(String id) { selects++; return Optional.ofNullable(shipments.get(id)); }

        /** Applies all statements or none. An UPDATE whose version doesn't match fails the whole batch. */
        synchronized void executeBatch(List<Row> inserts, List<Row> updates) {
            for (Row u : updates) {
                Row current = shipments.get(u.id());
                if (current == null || current.version() != u.version())
                    throw new StaleUpdate(u.id(), u.version(), current == null ? -1 : current.version());
            }
            inserts.forEach(r -> { shipments.put(r.id(), r); log.add("INSERT " + r); });
            updates.forEach(r -> {
                Row next = new Row(r.id(), r.status(), r.rider(), r.version() + 1);
                shipments.put(r.id(), next);
                log.add("UPDATE " + r.id() + " SET status=" + r.status() + ", rider=" + r.rider() + ", version=" + next.version() + " WHERE version=" + r.version());
            });
        }
    }
    static final class StaleUpdate extends RuntimeException {
        StaleUpdate(String id, long expected, long actual) { super(id + " changed by someone else (had v" + expected + ", now v" + actual + ")"); }
    }

    // ---------- Domain model: no persistence code ----------
    static final class Shipment {
        enum Status { BOOKED, ASSIGNED, PICKED_UP, DELIVERED }
        private final String id; private Status status; private String rider;
        Shipment(String id) { this(id, Status.BOOKED, null); }
        Shipment(String id, Status status, String rider) { this.id = id; this.status = status; this.rider = rider; }
        void assign(String riderId) {
            if (status != Status.BOOKED && status != Status.ASSIGNED) throw new IllegalStateException(id + " is " + status);
            rider = riderId; status = Status.ASSIGNED;
        }
        void pickUp() {
            if (status != Status.ASSIGNED) throw new IllegalStateException(id + " is " + status);
            status = Status.PICKED_UP;
        }
        String id() { return id; } Status status() { return status; } String rider() { return rider; }
    }

    // ---------- Data Mapper ----------
    static final class ShipmentMapper {
        Shipment toDomain(Row r) { return new Shipment(r.id(), Shipment.Status.valueOf(r.status()), r.rider()); }
        Row toRow(Shipment s, long version) { return new Row(s.id(), s.status().name(), s.rider(), version); }
    }

    // ---------- Unit of Work with Identity Map, dirty checking and optimistic locking ----------
    static final class UnitOfWork implements AutoCloseable {
        private final Database db; private final ShipmentMapper mapper = new ShipmentMapper();
        private final Map<String, Shipment> identityMap = new LinkedHashMap<>();
        private final Map<String, Row> snapshots = new LinkedHashMap<>();
        private final List<Shipment> created = new ArrayList<>();

        UnitOfWork(Database db) { this.db = db; }

        Optional<Shipment> find(String id) {
            if (identityMap.containsKey(id)) return Optional.of(identityMap.get(id));
            return db.select(id).map(row -> {
                Shipment s = mapper.toDomain(row);
                identityMap.put(id, s);
                snapshots.put(id, row);                                   // remember what was loaded, with its version
                return s;
            });
        }
        void add(Shipment s) { created.add(s); identityMap.put(s.id(), s); }

        void commit() {
            List<Row> inserts = created.stream().map(s -> mapper.toRow(s, 0)).toList();
            List<Row> updates = new ArrayList<>();
            snapshots.forEach((id, loaded) -> {
                Row now = mapper.toRow(identityMap.get(id), loaded.version());
                if (!now.equals(loaded)) updates.add(now);                 // unchanged objects produce no SQL
            });
            db.executeBatch(inserts, updates);
            created.clear(); snapshots.clear(); identityMap.clear();
        }
        @Override public void close() { identityMap.clear(); snapshots.clear(); created.clear(); }
    }

    // ---------- Service layer with DTOs ----------
    record ShipmentView(String id, String status, String rider) {}
    sealed interface Outcome permits Done, Conflict, Invalid {}
    record Done(ShipmentView shipment) implements Outcome {}
    record Conflict(String message) implements Outcome {}
    record Invalid(String message) implements Outcome {}

    record ShipmentService(Database db) {
        Outcome book(String id) { return inUnit(u -> { Shipment s = new Shipment(id); u.add(s); return s; }); }
        Outcome assign(String id, String rider) { return inUnit(u -> { Shipment s = load(u, id); s.assign(rider); return s; }); }
        Outcome pickUp(String id) { return inUnit(u -> { Shipment s = load(u, id); s.pickUp(); return s; }); }

        private Shipment load(UnitOfWork u, String id) { return u.find(id).orElseThrow(() -> new IllegalStateException("no shipment " + id)); }
        private Outcome inUnit(Function<UnitOfWork, Shipment> work) {
            try (UnitOfWork u = new UnitOfWork(db)) {
                Shipment s = work.apply(u);
                u.commit();
                return new Done(view(s));
            } catch (StaleUpdate e) { return new Conflict(e.getMessage()); }
            catch (IllegalStateException e) { return new Invalid(e.getMessage()); }
        }
        static ShipmentView view(Shipment s) { return new ShipmentView(s.id(), s.status().name(), s.rider() == null ? "-" : s.rider()); }
    }

    public static void main(String[] args) {
        Database db = new Database();
        ShipmentService service = new ShipmentService(db);

        System.out.println(service.book("PG-1"));
        System.out.println(service.assign("PG-1", "rider-arjun"));

        // Two dispatchers load PG-1 at the same time and both reassign it.
        UnitOfWork deskA = new UnitOfWork(db), deskB = new UnitOfWork(db);
        deskA.find("PG-1").orElseThrow().assign("rider-bina");
        deskB.find("PG-1").orElseThrow().assign("rider-chetan");
        deskA.commit();
        try { deskB.commit(); } catch (StaleUpdate e) { System.out.println("desk B: " + e.getMessage()); }

        // Same row twice in one unit: one object, one SELECT. Unchanged object: no UPDATE.
        db.selects = 0;
        UnitOfWork reader = new UnitOfWork(db);
        System.out.println("same object: " + (reader.find("PG-1").get() == reader.find("PG-1").get()) + ", selects: " + db.selects);
        int before = db.log.size();
        reader.commit();
        System.out.println("statements for a read-only unit: " + (db.log.size() - before));

        System.out.println(service.pickUp("PG-1"));
        System.out.println(service.assign("PG-1", "rider-arjun"));
        System.out.println(service.pickUp("PG-9"));
        System.out.println("SQL log:");
        db.log.forEach(s -> System.out.println("  " + s));
    }
}
```

1. The data mapper is the only code that knows both row and domain shapes.
2. Identity map plus snapshots: one object per row, and a copy of what was loaded, including its version.
3. Dirty checking compares current state with the snapshot; unchanged objects produce no SQL.
4. The batch is atomic, and each UPDATE carries `WHERE version = ?`: optimistic locking.
5. The service returns view DTOs and typed outcomes (`Done`, `Conflict`, `Invalid`).

**Run it:** `java OrderPersistenceDemo.java`

**Output:**

```text
Done[shipment=ShipmentView[id=PG-1, status=BOOKED, rider=-]]
Done[shipment=ShipmentView[id=PG-1, status=ASSIGNED, rider=rider-arjun]]
desk B: PG-1 changed by someone else (had v1, now v2)
same object: true, selects: 1
statements for a read-only unit: 0
Done[shipment=ShipmentView[id=PG-1, status=PICKED_UP, rider=rider-bina]]
Invalid[message=PG-1 is PICKED_UP]
Invalid[message=no shipment PG-9]
SQL log:
  INSERT Row[id=PG-1, status=BOOKED, rider=null, version=0]
  UPDATE PG-1 SET status=ASSIGNED, rider=rider-arjun, version=1 WHERE version=0
  UPDATE PG-1 SET status=ASSIGNED, rider=rider-bina, version=2 WHERE version=1
  UPDATE PG-1 SET status=PICKED_UP, rider=rider-bina, version=3 WHERE version=2
```

**What to notice:**

- Desk B's change was rejected, so rider-bina's assignment (desk A) wasn't silently replaced.
- A read-only unit of work wrote nothing.
- In JPA, the same behaviour comes from `@Version`, the persistence context and flush-time dirty checking; the failure is `OptimisticLockException`.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Domain Model | Rules in one place; testable | Needs mapping; learning curve |
| Service Layer | Clear use cases and transactions | Can become a thin pass-through |
| Repository | Storage-independent domain | Abstraction over powerful query tools |
| Data Mapper / ORM | Rich objects, independent schema | Complexity; hidden SQL |
| Unit of Work + Identity Map | Consistency, batching | Memory per transaction; implicit writes |
| Lazy Load | Load only what's needed | N+1, `LazyInitializationException` |
| DTOs | Safe, stable contracts | Mapping code |

## 8. When to Use / When NOT to Use

**Use them when:**

- the application has real business rules and a relational database;
- several clients (web, mobile, batch) share use cases;
- multiple users edit the same data.

**Don't use them when:**

- the app is a thin CRUD layer over tables; Active Record or Spring Data REST may be enough;
- reports and analytics: query SQL directly into DTOs, no domain objects needed;
- extreme throughput paths where ORM overhead matters: use jOOQ or JDBC for those paths.

## 9. Best Practices

1. **Keep business rules in the domain model, transactions in the service layer.** *(Consensus.)*
2. **One unit of work (transaction) per use case; keep it short.** *(Consensus.)*
3. **Use optimistic locking (`@Version`) on entities edited by people.** *(Consensus.)*
4. **Choose fetch plans per use case; watch for N+1.** *(Consensus.)*
5. **Never expose entities across the API boundary; use DTOs.** *(Consensus.)*
6. **Use CQRS-lite: domain model for writes, SQL projections for reads.** *(Opinion, widely used.)*
7. **Send side effects after commit (outbox or after-commit events).** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Lost update without optimistic locking (production failure mode)**  
> **Symptom:** a rider showed up for a shipment that had been reassigned; support saw the old rider in the database.
> **Root cause:** two dispatchers loaded the shipment, both saved; the last write silently replaced the first.
> **Fix:** a `@Version` column; on `OptimisticLockException`, reload and show "this shipment was changed by someone else".

> 🚨 **N+1 queries on the order history screen**  
> **Symptom:** the orders page took 4 seconds for customers with long histories; the database showed hundreds of identical queries per request.
> **Root cause:** a lazy `lines` collection accessed in a loop while rendering.
> **Fix:** `@EntityGraph` or `JOIN FETCH` for that query, or a DTO projection; a test asserting the query count.

> 🚨 **Anaemic domain model**  
> **Symptom:** the same discount rule exists in four services, each slightly different.
> **Root cause:** entities with only getters/setters; all rules in services.
> **Fix:** move rules into entity and value-object methods; services call them.

> 🚨 **Entity returned from a REST controller**  
> **Symptom:** `LazyInitializationException` in JSON serialisation, or internal fields (cost price) in the API.
> **Root cause:** serialising the entity graph outside the transaction.
> **Fix:** response DTOs built inside the service.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `spring.jpa.open-in-view` | `true` (with warning) | Keeps the persistence context open while rendering | Set `false` | Exposes hidden lazy loads |
| `hibernate.default_batch_fetch_size` | none | Loads lazy collections in batches | 16–100 for list screens | Larger IN lists |
| `hibernate.jdbc.batch_size` | none | Batches INSERT/UPDATE | 20–50 for bulk writes | Needs ordered inserts |
| `hibernate.order_inserts` / `order_updates` | `false` | Groups statements for batching | With batch size | Minor |
| `@Version` | none | Optimistic locking | Entities edited concurrently | Must handle conflicts |
| `spring.jpa.show-sql` / `org.hibernate.SQL` logging | off | Logs SQL | In tests and debugging | Log noise in prod |

## 12. Security Considerations

- **Mass assignment:** bind requests to DTOs, never directly to entities.
- **Data exposure:** response DTOs decide what leaves the system; entities with sensitive fields must never be serialised.
- **Authorisation at the service layer:** check ownership in use cases (`order.customerId == currentUser`) so every client is covered.
- **SQL injection:** repositories and mappers use bound parameters; never build JPQL or SQL with string concatenation.

## 13. Performance & Scalability

ORMs cost CPU and memory for dirty checking and identity maps, but the dominant cost is usually query count and shape. Measure queries per request; fix N+1 with fetch plans or projections; batch writes. Use read-only transactions (`@Transactional(readOnly = true)`) so Hibernate skips dirty checking. Keep units of work small; a persistence context holding 100,000 entities makes every flush slow. For read-heavy screens, project straight into DTOs.

## 14. Testing It

Test domain rules without a database, repositories against a real database (Testcontainers), and persistence behaviour (identity, dirty checking, optimistic locking, rollback) explicitly:

**OrderPersistenceTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderPersistenceTest {

    OrderPersistenceDemo.Database db;
    OrderPersistenceDemo.ShipmentService service;

    @BeforeEach
    void setUp() {
        db = new OrderPersistenceDemo.Database();
        service = new OrderPersistenceDemo.ShipmentService(db);
        service.book("PG-1");
    }

    @Test
    void identityMapReturnsOneObjectPerRowAndQueriesOnce() {
        var unit = new OrderPersistenceDemo.UnitOfWork(db);
        db.selects = 0;
        assertSame(unit.find("PG-1").orElseThrow(), unit.find("PG-1").orElseThrow());
        assertEquals(1, db.selects);
    }

    @Test
    void unchangedObjectsWriteNothing() {
        var unit = new OrderPersistenceDemo.UnitOfWork(db);
        unit.find("PG-1");
        int before = db.log.size();
        unit.commit();
        assertEquals(before, db.log.size());
    }

    @Test
    void secondConcurrentWriterGetsAConflictAndNothingIsLost() {
        var a = new OrderPersistenceDemo.UnitOfWork(db);
        var b = new OrderPersistenceDemo.UnitOfWork(db);
        a.find("PG-1").orElseThrow().assign("R-A");
        b.find("PG-1").orElseThrow().assign("R-B");
        a.commit();
        assertThrows(OrderPersistenceDemo.StaleUpdate.class, b::commit);
        assertEquals("R-A", db.shipments.get("PG-1").rider());
        assertEquals(1, db.shipments.get("PG-1").version());
    }

    @Test
    void batchIsAllOrNothing() {
        var stale = new OrderPersistenceDemo.Row("PG-1", "ASSIGNED", "R-X", 7);
        var fresh = new OrderPersistenceDemo.Row("PG-2", "BOOKED", null, 0);
        assertThrows(OrderPersistenceDemo.StaleUpdate.class, () -> db.executeBatch(List.of(fresh), List.of(stale)));
        assertEquals(false, db.shipments.containsKey("PG-2"));
    }

    @Test
    void domainRulesAreReportedAsInvalidNotPersisted() {
        assertInstanceOf(OrderPersistenceDemo.Invalid.class, service.pickUp("PG-1"));    // not assigned yet
        assertEquals("BOOKED", db.shipments.get("PG-1").status());
    }

    @Test
    void checkoutRollsBackStockWhenAnyLineFails() {
        var store = new CheckoutServiceDemo.Store();
        var products = new CheckoutServiceDemo.ProductRepository(store);
        products.save(new CheckoutServiceDemo.Product("A", "A", 100, 5));
        products.save(new CheckoutServiceDemo.Product("B", "B", 100, 1));
        var checkout = new CheckoutServiceDemo.CheckoutService(store);
        var result = checkout.checkout(new CheckoutServiceDemo.CheckoutRequest("x",
                List.of(new CheckoutServiceDemo.CartItem("A", 2), new CheckoutServiceDemo.CartItem("B", 3))));
        assertInstanceOf(CheckoutServiceDemo.Rejected.class, result);
        assertEquals(5, products.find("A").orElseThrow().stock());
    }
}
```

With JPA, the equivalent tests use `@DataJpaTest`, two `EntityManager`s (or two transactions) for the conflict case, and a query-count assertion (Hibernate statistics or datasource-proxy) for N+1.

## 15. Observability & Operations

Log slow queries and query counts per request (Hibernate statistics, datasource-proxy, p6spy). Track optimistic-lock conflicts as a metric; a spike means users fight over the same records or a retry loop is wrong. Trace spans per repository call (Micrometer Tracing with JDBC instrumentation). Watch connection-pool wait time; long units of work hold connections.

## 16. Version Notes

| Version | Change relevant to these patterns |
|---------|-----------------------------------|
| PoEAA (2002) | Patterns catalogued |
| JPA 1.0 (2006) | Standard Data Mapper, Unit of Work, Identity Map, Lazy Load |
| Spring Data (2011) | Repository interfaces with derived queries |
| Java 16 | Records: ideal DTOs and value objects |
| Jakarta Persistence 3.1 / Hibernate 6 | `jakarta.*` namespace; improved SQL generation |
| Spring Boot 3.x | Requires Jakarta EE 9+; records as projections |
| Jakarta Persistence 3.2 / Hibernate 7 | Records as embeddables; `EntityManager` improvements |

## 17. Expert Deep Dive

- **Aggregates set the unit of consistency.** In DDD, a repository per aggregate root and one aggregate per transaction keeps units of work small and avoids lock contention.
- **Persistence context lifecycle.** `persist`, `merge`, detached entities and flush modes explain most JPA surprises; `merge` copies state onto a managed instance and returns *that*, not the argument.
- **Read/write split.** Domain models are great for writes and poor for reads; CQRS-lite (projections for queries) removes most N+1 and DTO-mapping pain.
- **Optimistic vs pessimistic locking.** Optimistic for low-contention edits by people; pessimistic (`SELECT ... FOR UPDATE`) for hot counters, or better, an atomic `UPDATE ... SET stock = stock - ? WHERE stock >= ?`.
- **Outbox pattern.** Writing events to an outbox table in the same unit of work makes side effects reliable without distributed transactions.

> 📖 **War story: the 4-second order history**  
> FreshCart's order history page was fast in development (5 test orders) and slow in production. Loyal customers had 300+ orders; the page loaded orders, then each order's lines, then each line's product: 1 + 300 + 2,400 queries. "Open session in view" hid it by allowing lazy loads during JSON rendering. **Fixes:** `open-in-view: false` (which surfaced every hidden lazy load as an exception), a paged DTO projection query for the list, `@EntityGraph` for the detail screen, and a test that fails if the history endpoint runs more than 3 queries. P95 dropped from 4.1 s to 120 ms. **Lesson:** lazy loading is a default, not a plan; each screen needs a fetch plan.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's a Repository?** **Collection-like access to domain objects** that hides storage.
2. **What's a DTO?** An object **shaped for a boundary**, carrying only needed data.
3. **What does a Service Layer do?** Defines **use cases** and owns **transactions**.
4. **Active Record vs Data Mapper?** **Object saves itself** vs **separate mapper**.
5. **What's lazy loading?** Loading related data **on first access**.

**▸ 🟡 L3**

1. **What's the N+1 problem and how do you fix it?** **1 query + 1 per row**; fix with **join fetch, entity graphs, batch fetching, projections**.
2. **What's an Identity Map?** **One object per row per unit of work**; JPA's persistence context.
3. **How does JPA know what to update?** **Dirty checking** against snapshots at **flush**.
4. **Why not return entities from controllers?** **Lazy-loading errors, leaked fields, coupled API**.
5. **Transaction Script vs Domain Model?** **Procedures** for simple logic vs **objects with rules** for complex logic.

**▸ 🔴 L4–L5**

1. **Two users edit the same order; what happens and how do you handle it?** **Lost update**; use **`@Version`** and show a **conflict**, or merge.
2. **When would you bypass the ORM?** **Reports, bulk updates, hot paths**: jOOQ/JDBC or **atomic SQL**.
3. **How do you keep side effects consistent with the transaction?** **Outbox** or **after-commit events**.
4. **How big should a unit of work be?** **One use case, one aggregate** where possible; short-lived.
5. **Is `open-in-view` good?** Convenient but **hides N+1** and holds connections; **disable it** in APIs.

**▸ 💻 Hands-on**

**Task:** Add delete support to `OrderPersistenceDemo`'s unit of work: `remove(Shipment)` should produce `DELETE ... WHERE version = ?` at commit and fail on a stale version.

> ✅ **Solution**  
> ```java
> private final Set<String> removed = new LinkedHashSet<>();
> void remove(Shipment s) {
>     if (created.remove(s)) { identityMap.remove(s.id()); return; }   // never written: nothing to delete
>     removed.add(s.id());
> }
> // in commit(): pass deletes to the batch, built from snapshots so the version is the loaded one
> List<Row> deletes = removed.stream().map(snapshots::get).toList();
> // Database.executeBatch checks versions for updates and deletes before applying anything,
> // then: deletes.forEach(r -> { shipments.remove(r.id()); log.add("DELETE " + r.id() + " WHERE version=" + r.version()); });
> // and commit() must skip removed ids when computing updates.
> ```

## 19. Practice

- [ ] Rewrite `CheckoutServiceDemo` with Spring Data JPA and H2; keep the response DTOs unchanged.
    > 💡 **Hint**  
    > `Product` becomes an `@Entity`; `take` stays a domain method; the service gets `@Transactional`.

- [ ] Add a query-count assertion to a list endpoint using Hibernate's `Statistics`.
    > 💡 **Hint**  
    > `sessionFactory.getStatistics().getPrepareStatementCount()` before and after.

- [ ] Turn `spring.jpa.open-in-view` off in an existing app and fix every exception that appears.
    > 💡 **Hint**  
    > Each one is a missing fetch plan.

- [ ] Replace the reflective mapping in a project with MapStruct and compare startup time.
    > 💡 **Hint**  
    > MapStruct generates plain Java at compile time.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Rules in the **domain model**, use cases and transactions in the **service layer**.
> - **Repository** = collection of aggregates; **Data Mapper** = objects ↔ tables.
> - **Unit of Work** writes changes together; **Identity Map** = one object per row.
> - **Lazy Load** by default, explicit fetch plans per screen; beware **N+1**.
> - **DTOs** at every boundary; never expose entities.
> - **`@Version`** for concurrent edits.
>
> ```java
> @Transactional
> public OrderView place(PlaceOrderRequest req) {
>     Order order = Order.from(req.items(), catalog);   // rules in the domain
>     orders.save(order);                               // repository
>     return OrderAssembler.toView(order);              // DTO out
> }
> ```

## 21. Further Reading

- Martin Fowler, *Patterns of Enterprise Application Architecture* (2002), and the [online catalog](https://martinfowler.com/eaaCatalog/).
- Eric Evans, *Domain-Driven Design* (2003); Vaughn Vernon, *Implementing Domain-Driven Design* (2013).
- Vlad Mihalcea, *High-Performance Java Persistence* (2016) and [his blog](https://vladmihalcea.com/).
- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/).
- [Hibernate ORM user guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html).

---

[← Previous: Concurrency Patterns](concurrency-patterns.md) · [Next: UI and Mobile Patterns →](ui-and-mobile-patterns.md)
