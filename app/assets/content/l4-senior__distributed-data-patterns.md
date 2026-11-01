# Distributed Data Patterns

L4 🟠 Senior / Lead · ⏱ 40 min read · Prerequisites: [Microservice Patterns](microservice-patterns.md), [Resilience Patterns](resilience-patterns.md)

> 📌 **TL;DR**  
> Once each service owns its data, there's no single ACID transaction across them. A **saga** runs a business transaction as a series of local transactions with **compensating actions** for undo. The **transactional outbox** writes the event in the same local transaction as the data, so "saved but never published" can't happen; a relay publishes afterwards, at least once. **CQRS** separates the write model (rules) from read models (shaped for queries), updated from events. **Event sourcing** stores events as the source of truth and derives state from them. **Idempotent consumers** make at-least-once delivery safe. **Change data capture** turns the database's own change log into events. Together they trade immediate consistency for availability and autonomy, so design every screen and rule for eventual consistency.

## 1. What It Is

**Plain English.** Placing a FreshCart order touches Inventory, Payments and Fulfilment, each with its own database. These patterns keep the data consistent anyway: by undoing steps when a later one fails, by never losing events, by building fast read views from events, by keeping a full history, by handling duplicate messages, and by streaming database changes to other systems.

**Definition.**

| Pattern | Purpose |
|---------|---------|
| Saga | Multi-service transaction as local steps plus compensations |
| Transactional outbox | Atomically save state and the event that announces it |
| CQRS | Separate models for commands (writes) and queries (reads) |
| Event sourcing | Events are the source of truth; state is derived |
| Idempotent consumer | Processing the same message twice has the effect of once |
| Change data capture (CDC) | Stream committed row changes from the database log |

## 2. Why It Exists

Two-phase commit (XA) across services couples their availability (all participants must be up), holds locks across the network, and isn't supported by most brokers and cloud databases. Meanwhile, the naive alternative, "write to my database, then publish an event", loses events on every crash between the two. These patterns come from the database and messaging communities (Garcia-Molina and Salem's sagas, 1987; Greg Young's CQRS and event sourcing, around 2010; Debezium-style CDC) and became mainstream with microservices.

## 3. Mental Model / Analogy

**Analogy.** Booking a trip with separate airline, hotel and car bookings. If the car isn't available, you cancel the hotel and the flight (saga with compensations). You write the booking in your notebook and the "send confirmation" note on the same page, so you can't forget one (outbox). Your accountant keeps every receipt (event sourcing) and also a monthly summary sheet (CQRS read model). If the hotel sends the same confirmation twice, you don't book twice (idempotent consumer).

**Technical model.** Each service commits locally; events flow through a broker with at-least-once delivery; consumers are idempotent and build their own models; long-running processes are explicit state machines persisted in a database.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Saga

**Description.** A saga is a sequence of local transactions, one per service. Each step has a **compensating action** that semantically undoes it (refund, release stock). If a step fails, the saga runs compensations for completed steps in reverse. **Orchestration** has a central coordinator issuing commands; **choreography** has services react to each other's events.

**Advantages / features.** No distributed locks; services stay available independently; business-level undo (a refund) instead of technical rollback.

**SagaDemo.java**

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Saga: a business transaction across services as a sequence of local transactions. If a step fails, the steps
 * already done are undone by compensating actions, in reverse order. This is an orchestrated saga.
 */
public class SagaDemo {

    record Step(String name, Supplier<Boolean> action, Runnable compensation) {}

    static boolean run(List<Step> steps) {
        Deque<Step> done = new ArrayDeque<>();
        for (Step s : steps) {
            if (s.action().get()) { System.out.println("  ok   " + s.name()); done.push(s); continue; }
            System.out.println("  FAIL " + s.name() + ", compensating");
            while (!done.isEmpty()) done.pop().compensation().run();
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        for (boolean riderAvailable : new boolean[] {true, false}) {
            System.out.println("order FC-1, rider available=" + riderAvailable);
            boolean placed = run(List.of(
                    new Step("reserve 2 x MILK-1L (Inventory)", () -> true, () -> System.out.println("  compensate: release 2 x MILK-1L")),
                    new Step("charge Rs 136 (Payments via PayLink)", () -> true, () -> System.out.println("  compensate: refund Rs 136")),
                    new Step("book rider (Fulfilment)", () -> riderAvailable, () -> {})));
            System.out.println("  order " + (placed ? "CONFIRMED" : "CANCELLED"));
        }
    }
}
```

1. Each step pairs an action with its compensation.
2. On failure, completed steps are compensated in reverse order.
3. A charge can't be rolled back, only refunded: compensation is a new business action.

**Run it:** `java SagaDemo.java`

```text
order FC-1, rider available=true
  ok   reserve 2 x MILK-1L (Inventory)
  ok   charge Rs 136 (Payments via PayLink)
  ok   book rider (Fulfilment)
  order CONFIRMED
order FC-1, rider available=false
  ok   reserve 2 x MILK-1L (Inventory)
  ok   charge Rs 136 (Payments via PayLink)
  FAIL book rider (Fulfilment), compensating
  compensate: refund Rs 136
  compensate: release 2 x MILK-1L
  order CANCELLED
```

| | Orchestration | Choreography |
|-|---------------|--------------|
| Control | Central coordinator | Each service reacts to events |
| Visibility | Saga state in one place | Spread across services |
| Coupling | Coordinator knows all steps | Services know each other's events |
| Good for | 4+ steps, complex rules | 2–3 steps, simple flows |

**Use** sagas for business processes spanning services. **Avoid** them inside one service: use a local ACID transaction.
**Real-world Java:** Axon Framework sagas, Eventuate Tram, Temporal and Camunda for orchestration, Spring State Machine.
**Best practice:** put irreversible steps (send SMS, ship) last, and make every step and compensation idempotent. **Gotcha:** sagas have no isolation: others can see intermediate states (stock reserved for an order that will be cancelled); use semantic locks such as a `PENDING` status.

### Transactional outbox

**Description.** In the same database transaction that changes business data, insert the event into an `outbox` table. A separate relay (polling or CDC) reads the outbox, publishes to the broker and marks rows as sent. Delivery is at least once: a crash after publishing but before marking means a duplicate.

**Advantages / features.** No lost or phantom events; works with any broker; preserves order per aggregate if published in order.

**TransactionalOutboxDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Transactional outbox: write the business row and the event row in the same local transaction; a relay publishes
 * outbox rows afterwards. No more "saved but never published" (or "published but never saved").
 */
public class TransactionalOutboxDemo {

    static final class Db {
        final List<String> orders = new ArrayList<>(), outbox = new ArrayList<>(), sent = new ArrayList<>();
        void inTransaction(Runnable work) {
            List<String> o = new ArrayList<>(orders), x = new ArrayList<>(outbox);
            try { work.run(); } catch (RuntimeException e) { orders.clear(); orders.addAll(o); outbox.clear(); outbox.addAll(x); throw e; }
        }
    }

    static final List<String> broker = new ArrayList<>();
    static boolean brokerUp = true;
    static void publish(String event) { if (!brokerUp) throw new IllegalStateException("broker unavailable"); broker.add(event); }

    public static void main(String[] args) {
        // Dual write: save, then publish. The broker is down at the wrong moment.
        Db db = new Db();
        brokerUp = false;
        db.orders.add("FC-1");
        try { publish("OrderPlaced FC-1"); } catch (IllegalStateException e) { System.out.println("dual write: FC-1 saved, event lost (" + e.getMessage() + ")"); }

        // Outbox: both rows commit together; publishing happens later and is retried.
        db.inTransaction(() -> { db.orders.add("FC-2"); db.outbox.add("OrderPlaced FC-2"); });
        relay(db);
        brokerUp = true;
        relay(db);
        // The relay crashes after publishing but before marking the row sent: the event is published again.
        db.inTransaction(() -> { db.orders.add("FC-3"); db.outbox.add("OrderPlaced FC-3"); });
        publish(db.outbox.get(db.outbox.size() - 1));
        System.out.println("relay crashed before marking FC-3 sent");
        relay(db);
        System.out.println("orders=" + db.orders + " broker=" + broker);
    }

    static void relay(Db db) {
        for (String event : new ArrayList<>(db.outbox)) {
            if (db.sent.contains(event)) continue;
            try { publish(event); db.sent.add(event); System.out.println("relay published " + event); }
            catch (IllegalStateException e) { System.out.println("relay will retry " + event + " (" + e.getMessage() + ")"); return; }
        }
    }
}
```

1. A stand-in for a local database transaction: all or nothing.
2. The order row and its event row commit together.
3. The relay publishes unsent rows, retries on failure and may publish twice after a crash.

**Run it:** `java TransactionalOutboxDemo.java`

```text
dual write: FC-1 saved, event lost (broker unavailable)
relay will retry OrderPlaced FC-2 (broker unavailable)
relay published OrderPlaced FC-2
relay crashed before marking FC-3 sent
relay published OrderPlaced FC-3
orders=[FC-1, FC-2, FC-3] broker=[OrderPlaced FC-2, OrderPlaced FC-3, OrderPlaced FC-3]
```

| Relay option | How | Trade-off |
|--------------|-----|-----------|
| Polling publisher | `SELECT ... FOR UPDATE SKIP LOCKED` every N ms | Simple; adds DB load and latency |
| CDC (Debezium outbox router) | Tail the outbox table's log | Low latency; more infrastructure |
| Listen/notify | Postgres `NOTIFY` wakes the relay | Faster polling; still need polling fallback |

**Use** whenever a state change must be announced. **Avoid** publishing to the broker inside the database transaction (a slow broker holds locks; rollback after publish leaves a phantom event).
**Real-world Java:** Spring Modulith event publication registry, Debezium outbox event router, Eventuate Tram.
**Best practice:** include an event ID and aggregate version in each outbox row; clean up sent rows. **Gotcha:** the outbox guarantees at-least-once, not exactly-once; consumers must be idempotent.

### CQRS

**Description.** Command Query Responsibility Segregation: commands are handled by a write model that enforces invariants; queries are served by one or more read models (projections) optimised for specific screens, kept up to date by events. Read models are eventually consistent.

**Advantages / features.** Reads and writes scale and evolve independently; queries become simple lookups; several read models for different needs (search, dashboards, mobile).

**CqrsDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * CQRS: commands go to a write model that enforces rules; queries read from a separate model shaped for the
 * screen, updated from events. The read side can lag behind the write side.
 */
public class CqrsDemo {

    record OrderPlaced(String orderId, String customerId, long totalPaise) {}
    record OrderCancelled(String orderId) {}

    // ----- write side: rules, normalised -----
    static final class OrderCommands {
        private final Map<String, String> status = new LinkedHashMap<>();
        final List<Object> events = new ArrayList<>();
        void place(String orderId, String customerId, long totalPaise) {
            if (totalPaise < 9900) throw new IllegalArgumentException("minimum order is Rs 99");
            status.put(orderId, "PLACED"); events.add(new OrderPlaced(orderId, customerId, totalPaise));
        }
        void cancel(String orderId) {
            if (!"PLACED".equals(status.get(orderId))) throw new IllegalStateException(orderId + " can't be cancelled");
            status.put(orderId, "CANCELLED"); events.add(new OrderCancelled(orderId));
        }
    }

    // ----- read side: denormalised for "My orders" -----
    record CustomerSummary(int activeOrders, long activeSpendPaise) {}
    static final class CustomerSummaryProjection {
        final Map<String, CustomerSummary> byCustomer = new TreeMap<>();
        private final Map<String, OrderPlaced> placed = new LinkedHashMap<>();
        int applied;
        void apply(Object e) {
            applied++;
            if (e instanceof OrderPlaced p) { placed.put(p.orderId(), p); adjust(p.customerId(), 1, p.totalPaise()); }
            if (e instanceof OrderCancelled c) { OrderPlaced p = placed.get(c.orderId()); adjust(p.customerId(), -1, -p.totalPaise()); }
        }
        private void adjust(String customer, int orders, long paise) {
            CustomerSummary s = byCustomer.getOrDefault(customer, new CustomerSummary(0, 0));
            byCustomer.put(customer, new CustomerSummary(s.activeOrders() + orders, s.activeSpendPaise() + paise));
        }
    }

    public static void main(String[] args) {
        OrderCommands commands = new OrderCommands();
        CustomerSummaryProjection view = new CustomerSummaryProjection();
        commands.place("FC-1", "C-1", 24_000);
        commands.place("FC-2", "C-1", 13_600);
        commands.place("FC-3", "C-2", 9_900);
        try { commands.place("FC-4", "C-2", 5_000); } catch (IllegalArgumentException e) { System.out.println("command rejected: " + e.getMessage()); }
        commands.cancel("FC-2");

        for (int i = 0; i < 3; i++) view.apply(commands.events.get(i));
        System.out.println("read model after 3 of " + commands.events.size() + " events: " + view.byCustomer);
        for (int i = 3; i < commands.events.size(); i++) view.apply(commands.events.get(i));
        System.out.println("read model caught up:           " + view.byCustomer);
    }
}
```

1. The write side enforces rules and records events.
2. The read side is a denormalised view per customer, built from events.
3. Until the projection catches up, the view shows the old total.

**Run it:** `java CqrsDemo.java`

```text
command rejected: minimum order is Rs 99
read model after 3 of 4 events: {C-1=CustomerSummary[activeOrders=2, activeSpendPaise=37600], C-2=CustomerSummary[activeOrders=1, activeSpendPaise=9900]}
read model caught up:           {C-1=CustomerSummary[activeOrders=1, activeSpendPaise=24000], C-2=CustomerSummary[activeOrders=1, activeSpendPaise=9900]}
```

| Level | What's separated |
|-------|------------------|
| Method level | Commands return nothing; queries change nothing (CQS) |
| Model level | Separate classes/DTOs for reads, same database |
| Store level | Read models in separate tables or stores (Elasticsearch, Redis) |
| With event sourcing | Write side is an event store; all reads are projections |

**Use** when read and write shapes or loads differ greatly. **Avoid** for simple CRUD screens; it doubles the moving parts.
**Real-world Java:** Axon Framework, Spring Data projections, Kafka Streams materialised views, Elasticsearch read models.
**Best practice:** make projections rebuildable from the event history. **Gotcha:** "read your own writes": after placing an order, the user may not see it yet; return the result from the command or poll with the new version.

### Event sourcing

**Description.** Instead of storing current state, store the sequence of events that happened to an aggregate in an append-only event store. Current (or past) state is computed by replaying events. Appends use optimistic concurrency: "append if the stream is still at version N".

**Advantages / features.** Complete audit trail; time travel ("balance on day 3"); new read models from history; natural fit with CQRS and events.

**EventSourcingDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Event sourcing: store the events that happened, not the current state. State is a fold over the events; history,
 * audits and "what was the balance on the 3rd?" come for free. Appends check the expected version.
 */
public class EventSourcingDemo {

    sealed interface WalletEvent permits Credited, Debited {}
    record Credited(int day, long paise, String reason) implements WalletEvent {}
    record Debited(int day, long paise, String orderId) implements WalletEvent {}

    static final class EventStore {
        private final List<WalletEvent> stream = new ArrayList<>();
        int version() { return stream.size(); }
        void append(int expectedVersion, WalletEvent e) {
            if (expectedVersion != stream.size())
                throw new IllegalStateException("concurrency conflict: expected v" + expectedVersion + " but stream is at v" + stream.size());
            stream.add(e);
        }
        List<WalletEvent> read() { return List.copyOf(stream); }
    }

    static long balance(List<WalletEvent> events, int upToDay) {
        long b = 0;
        for (WalletEvent e : events) {
            switch (e) {
                case Credited c when c.day() <= upToDay -> b += c.paise();
                case Debited d when d.day() <= upToDay -> b -= d.paise();
                default -> { }
            }
        }
        return b;
    }

    static void debit(EventStore store, int day, long paise, String orderId) {
        int version = store.version();
        if (balance(store.read(), Integer.MAX_VALUE) < paise) throw new IllegalStateException("insufficient balance for " + orderId);
        store.append(version, new Debited(day, paise, orderId));
    }

    public static void main(String[] args) {
        EventStore wallet = new EventStore();
        wallet.append(0, new Credited(1, 50_000, "top-up"));
        debit(wallet, 2, 24_000, "FC-1");
        wallet.append(2, new Credited(3, 2_400, "cashback FC-1"));
        debit(wallet, 5, 13_600, "FC-2");

        System.out.println("events: " + wallet.read().size() + ", balance now: Rs " + balance(wallet.read(), 99) / 100);
        System.out.println("balance at end of day 3: Rs " + balance(wallet.read(), 3) / 100);
        try { debit(wallet, 6, 20_000, "FC-3"); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        int staleVersion = 2;
        try { wallet.append(staleVersion, new Credited(6, 100, "duplicate cashback")); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        System.out.println("audit trail:");
        wallet.read().forEach(e -> System.out.println("  " + e));
    }
}
```

1. Appends check the expected version, so concurrent writers can't both succeed on stale state.
2. State is a fold over events, optionally only up to a point in time.
3. A command loads the version, checks the rule against derived state and appends.

**Run it:** `java EventSourcingDemo.java`

```text
events: 4, balance now: Rs 148
balance at end of day 3: Rs 284
insufficient balance for FC-3
concurrency conflict: expected v2 but stream is at v4
audit trail:
  Credited[day=1, paise=50000, reason=top-up]
  Debited[day=2, paise=24000, orderId=FC-1]
  Credited[day=3, paise=2400, reason=cashback FC-1]
  Debited[day=5, paise=13600, orderId=FC-2]
```

| Concern | Technique |
|---------|-----------|
| Long streams | Snapshots every N events |
| Schema evolution | Upcasters, versioned events (never edit history) |
| Personal data and erasure | Crypto-shredding (delete the key) or store PII outside events |
| Queries | Projections (CQRS) |
| Concurrency | Expected-version appends |

**Use** where history is the business (ledgers, wallets, compliance, logistics tracking). **Avoid** as a default persistence style; it adds complexity to every change.
**Real-world Java:** Axon Server, EventStoreDB Java client, Kafka as a log (with care), Postgres append-only tables.
**Best practice:** design events in business language and keep them small and immutable. **Gotcha:** fixing a bug in history means appending correcting events, not editing old ones.

### Idempotent consumer

**Description.** Brokers deliver at least once: after a consumer crash or rebalance, messages are redelivered. An idempotent consumer records each processed message ID (or uses a natural key) in the same transaction as its side effect, and ignores messages it has already processed.

**Advantages / features.** Exactly-once *effect* on top of at-least-once delivery; safe retries and replays.

**IdempotentConsumerDemo.java**

```java
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Idempotent consumer: brokers deliver at least once, so the same message can arrive twice. Record processed
 * message IDs in the same transaction as the side effect, and skip duplicates.
 */
public class IdempotentConsumerDemo {

    record Message(String id, String customerId, int points) {}

    static final class NaiveConsumer {
        int points;
        void handle(Message m) { points += m.points(); }
    }

    static final class IdempotentConsumer {
        int points; final Set<String> processed = new HashSet<>();
        String handle(Message m) {
            if (!processed.add(m.id())) return "skip duplicate " + m.id();
            points += m.points();
            return "credited " + m.points() + " for " + m.id();
        }
    }

    public static void main(String[] args) {
        Message earn = new Message("msg-81", "C-1", 24);
        Message earn2 = new Message("msg-82", "C-1", 13);
        List<Message> delivered = List.of(earn, earn2, earn);   // msg-81 redelivered after a consumer restart

        NaiveConsumer naive = new NaiveConsumer();
        delivered.forEach(naive::handle);
        System.out.println("naive points: " + naive.points + " (should be 37)");

        IdempotentConsumer safe = new IdempotentConsumer();
        delivered.forEach(m -> System.out.println("  " + safe.handle(m)));
        System.out.println("idempotent points: " + safe.points);
    }
}
```

1. A processed-messages table; in production a database table with a unique key.
2. A duplicate is recognised and skipped.
3. The side effect and the ID record must commit in the same transaction.

**Run it:** `java IdempotentConsumerDemo.java`

```text
naive points: 61 (should be 37)
  credited 24 for msg-81
  credited 13 for msg-82
  skip duplicate msg-81
idempotent points: 37
```

| Technique | Example |
|-----------|---------|
| Processed-message table | `INSERT INTO processed(id)` with a unique constraint |
| Natural idempotency | `SET status = 'PAID'` instead of `balance += x` |
| Version check | Apply only if `event.version == current + 1` |
| Upsert by business key | `INSERT ... ON CONFLICT DO NOTHING` |

**Use** for every consumer of at-least-once messaging. **Avoid** relying on "exactly-once" broker settings alone; they rarely cover your database side effects.
**Real-world Java:** Spring Kafka with a JDBC dedupe table, Spring Integration `IdempotentReceiverInterceptor`.
**Best practice:** keep processed IDs at least as long as the broker's redelivery window. **Gotcha:** checking for the ID in one transaction and writing the effect in another reopens the race.

### Change data capture

**Description.** CDC reads the database's replication log (Postgres WAL, MySQL binlog) and emits an event for each committed insert, update or delete, with before and after images. The connector stores its log position (offset) to resume after restarts. It can feed caches, search indexes and analytics, or relay an outbox table.

**Advantages / features.** No application changes; captures every committed change including manual fixes; ordered per table/key; low latency.

**ChangeDataCaptureDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Change data capture: read the database's own change log (Postgres WAL, MySQL binlog) and turn each committed row
 * change into an event, without touching application code. The reader stores its log position to resume.
 */
public class ChangeDataCaptureDemo {

    record LogEntry(long lsn, String table, String op, Map<String, String> before, Map<String, String> after) {}

    static final class Database {
        final Map<String, Map<String, String>> products = new LinkedHashMap<>();
        final List<LogEntry> wal = new ArrayList<>();
        void upsert(String sku, String price) {
            Map<String, String> before = products.get(sku);
            Map<String, String> after = Map.of("sku", sku, "price", price);
            products.put(sku, after);
            wal.add(new LogEntry(wal.size() + 1, "products", before == null ? "c" : "u", before, after));
        }
        void delete(String sku) { wal.add(new LogEntry(wal.size() + 1, "products", "d", products.remove(sku), null)); }
    }

    static final class CdcConnector {
        long committedLsn;
        List<String> poll(Database db, int max) {
            List<String> out = new ArrayList<>();
            for (LogEntry e : db.wal) {
                if (e.lsn() <= committedLsn || out.size() == max) continue;
                out.add("lsn=" + e.lsn() + " op=" + e.op() + " before=" + price(e.before()) + " after=" + price(e.after()));
                committedLsn = e.lsn();
            }
            return out;
        }
        static String price(Map<String, String> row) { return row == null ? "null" : row.get("sku") + "@" + row.get("price"); }
    }

    public static void main(String[] args) {
        Database db = new Database();
        CdcConnector connector = new CdcConnector();
        db.upsert("MILK-1L", "68");
        db.upsert("CURD-500G", "45");
        connector.poll(db, 10).forEach(System.out::println);
        db.upsert("MILK-1L", "70");
        db.delete("CURD-500G");
        db.upsert("PANEER-200G", "95");
        System.out.println("-- connector restarts, resumes after lsn " + connector.committedLsn);
        connector.poll(db, 2).forEach(System.out::println);
        connector.poll(db, 2).forEach(System.out::println);
    }
}
```

1. Every committed change is written to the log with a sequence number (LSN).
2. The connector stores the last LSN it has published.
3. Each change event has an operation and before/after images, like Debezium's envelope.

**Run it:** `java ChangeDataCaptureDemo.java`

```text
lsn=1 op=c before=null after=MILK-1L@68
lsn=2 op=c before=null after=CURD-500G@45
-- connector restarts, resumes after lsn 2
lsn=3 op=u before=MILK-1L@68 after=MILK-1L@70
lsn=4 op=d before=CURD-500G@45 after=null
lsn=5 op=c before=null after=PANEER-200G@95
```

| CDC source | Use for | Caution |
|------------|---------|---------|
| Raw table changes | Search index, cache invalidation, analytics | Leaks internal schema to consumers |
| Outbox table | Domain events | Needs the outbox discipline |
| Initial snapshot | Bootstrapping a new consumer | Large tables take time |

**Use** for integrating legacy systems, keeping derived stores in sync, and as an outbox relay. **Avoid** exposing raw table changes as your public event contract.
**Real-world Java:** Debezium (Kafka Connect or embedded engine), AWS DMS, Flink CDC.
**Best practice:** route raw CDC through a translation step (or use outbox events) before other teams consume it. **Gotcha:** a stuck connector stops the database from discarding WAL (Postgres replication slots), which can fill the disk.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** GreenValley's warehouse used to overwrite a `stock` column and could never explain mismatches. Now each movement is recorded.

**StockLedgerDemo.java**

```java
import java.util.List;

/** Minimal: the stock of one SKU as a ledger of movements; the count on hand is derived, never overwritten. */
public class StockLedgerDemo {

    record Movement(String type, int units) {}

    static int onHand(List<Movement> ledger) {
        return ledger.stream().mapToInt(m -> m.type().equals("received") ? m.units() : -m.units()).sum();
    }

    public static void main(String[] args) {
        List<Movement> milk = List.of(
                new Movement("received", 40), new Movement("sold", 12), new Movement("damaged", 2), new Movement("sold", 9));
        System.out.println("MILK-1L on hand: " + onHand(milk));
        System.out.println("after the first two movements: " + onHand(milk.subList(0, 2)));
        System.out.println("written off as damaged: " + milk.stream().filter(m -> m.type().equals("damaged")).mapToInt(Movement::units).sum());
    }
}
```

1. Stock on hand is computed from movements.
2. Movements are appended, never edited.

**Run it:** `java StockLedgerDemo.java`

**Output:**

```text
MILK-1L on hand: 17
after the first two movements: 28
written off as damaged: 2
```

**What to notice:**

- "How many were damaged?" is a simple query on history; with an overwritten column it's unanswerable.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the "My orders" screen reads a projection fed by order events. Events arrive duplicated and out of order.

**OrderHistoryProjectionDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: FreshCart's "My orders" screen is a read model fed by order events. The broker delivers at least once
 * and doesn't guarantee order across partitions, so the projection handles duplicates, stale and early events.
 */
public class OrderHistoryProjectionDemo {

    record OrderEvent(String orderId, int version, String status) {}

    static final class OrderHistoryProjection {
        final Map<String, String> statusByOrder = new TreeMap<>();
        private final Map<String, Integer> appliedVersion = new TreeMap<>();
        private final Map<String, List<OrderEvent>> parked = new TreeMap<>();
        final List<String> log = new ArrayList<>();

        void on(OrderEvent e) {
            int current = appliedVersion.getOrDefault(e.orderId(), 0);
            if (e.version() <= current) { log.add("skip v" + e.version() + " of " + e.orderId() + " (already at v" + current + ")"); return; }
            if (e.version() > current + 1) {
                parked.computeIfAbsent(e.orderId(), k -> new ArrayList<>()).add(e);
                log.add("park v" + e.version() + " of " + e.orderId() + " (waiting for v" + (current + 1) + ")");
                return;
            }
            statusByOrder.put(e.orderId(), e.status());
            appliedVersion.put(e.orderId(), e.version());
            log.add("apply v" + e.version() + " of " + e.orderId() + " -> " + e.status());
            List<OrderEvent> waiting = parked.getOrDefault(e.orderId(), new ArrayList<>());
            waiting.stream().filter(p -> p.version() == e.version() + 1).findFirst().ifPresent(next -> {
                waiting.remove(next);
                on(next);
            });
        }
    }

    public static void main(String[] args) {
        OrderHistoryProjection view = new OrderHistoryProjection();
        List<OrderEvent> delivered = List.of(
                new OrderEvent("FC-1", 1, "PLACED"),
                new OrderEvent("FC-1", 3, "OUT_FOR_DELIVERY"),
                new OrderEvent("FC-1", 2, "PACKED"),
                new OrderEvent("FC-1", 2, "PACKED"),
                new OrderEvent("FC-2", 1, "PLACED"),
                new OrderEvent("FC-1", 4, "DELIVERED"));
        delivered.forEach(view::on);
        view.log.forEach(l -> System.out.println("  " + l));
        System.out.println("My orders: " + view.statusByOrder);
    }
}
```

1. Events at or below the applied version are duplicates or stale: skip.
2. An event from the future is parked until the missing version arrives.
3. After applying, any parked successor is applied too.

**Run it:** `java OrderHistoryProjectionDemo.java`

**Output:**

```text
  apply v1 of FC-1 -> PLACED
  park v3 of FC-1 (waiting for v2)
  apply v2 of FC-1 -> PACKED
  apply v3 of FC-1 -> OUT_FOR_DELIVERY
  skip v2 of FC-1 (already at v3)
  apply v1 of FC-2 -> PLACED
  apply v4 of FC-1 -> DELIVERED
My orders: {FC-1=DELIVERED, FC-2=PLACED}
```

**What to notice:**

- FC-1 never showed `OUT_FOR_DELIVERY` before `PACKED`, and never went backwards.
- Partitioning the topic by order ID keeps per-order order in Kafka, but versions still protect against replays and redeliveries.

### Example 3: Production-grade

**Scenario:** FreshCart's fulfilment saga coordinates Inventory, Payments and Fulfilment. Orchestrator instances crash and get replaced, replies are redelivered, and ParcelGo sometimes takes minutes to confirm a rider.

**FulfilmentSagaDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Production-grade: an orchestrated order-fulfilment saga whose state survives restarts. Commands go out through
 * an outbox, replies are deduplicated by step, a step that takes too long counts as failed, and completed steps
 * are compensated in reverse order.
 */
public class FulfilmentSagaDemo {

    enum Step { RESERVE_STOCK, CHARGE_PAYMENT, BOOK_RIDER }
    static final Map<Step, String> COMPENSATION = Map.of(
            Step.RESERVE_STOCK, "ReleaseStock", Step.CHARGE_PAYMENT, "RefundPayment", Step.BOOK_RIDER, "CancelRider");

    record SagaState(String orderId, Step awaiting, long stepStartedAt, List<Step> completed, String status) {}
    record Reply(String orderId, Step step, boolean success) {}

    /** Stands in for a database table; survives orchestrator restarts. */
    static final class SagaStore { final Map<String, SagaState> rows = new TreeMap<>(); }
    static final class Outbox { final List<String> commands = new ArrayList<>(); }

    static final class Orchestrator {
        static final long STEP_TIMEOUT_MS = 60_000;
        private final SagaStore store; private final Outbox outbox;
        Orchestrator(SagaStore store, Outbox outbox) { this.store = store; this.outbox = outbox; }

        void start(String orderId, long now) { begin(new SagaState(orderId, null, now, List.of(), "RUNNING"), Step.RESERVE_STOCK, now); }

        String onReply(Reply r, long now) {
            SagaState s = store.rows.get(r.orderId());
            if (s != null && "CANCELLED".equals(s.status()) && r.success() && !s.completed().contains(r.step())) {
                outbox.commands.add(COMPENSATION.get(r.step()) + " " + r.orderId());      // it succeeded after we gave up: undo it
                return "late " + r.step() + " success for cancelled " + r.orderId() + ", compensating";
            }
            if (s == null || !"RUNNING".equals(s.status()) || s.awaiting() != r.step())
                return "ignored " + r.step() + " reply for " + r.orderId() + " (saga " + (s == null ? "unknown" : s.status() + ", awaiting " + s.awaiting()) + ")";
            if (!r.success()) return compensate(s, r.step() + " failed");
            List<Step> done = new ArrayList<>(s.completed()); done.add(r.step());
            SagaState progressed = new SagaState(s.orderId(), null, now, List.copyOf(done), "RUNNING");
            if (r.step() == Step.BOOK_RIDER) { save(new SagaState(s.orderId(), null, now, progressed.completed(), "COMPLETED")); return r.orderId() + " COMPLETED"; }
            begin(progressed, Step.values()[r.step().ordinal() + 1], now);
            return r.orderId() + " " + r.step() + " done";
        }

        List<String> checkTimeouts(long now) {
            List<String> out = new ArrayList<>();
            for (SagaState s : List.copyOf(store.rows.values()))
                if ("RUNNING".equals(s.status()) && now - s.stepStartedAt() > STEP_TIMEOUT_MS)
                    out.add(compensate(s, s.awaiting() + " timed out"));
            return out;
        }

        private void begin(SagaState s, Step step, long now) {
            save(new SagaState(s.orderId(), step, now, s.completed(), "RUNNING"));
            outbox.commands.add(step + " " + s.orderId());
        }

        private String compensate(SagaState s, String reason) {
            List<Step> done = new ArrayList<>(s.completed());
            java.util.Collections.reverse(done);
            for (Step step : done) outbox.commands.add(COMPENSATION.get(step) + " " + s.orderId());
            save(new SagaState(s.orderId(), null, s.stepStartedAt(), s.completed(), "CANCELLED"));
            return s.orderId() + " CANCELLED: " + reason;
        }

        private void save(SagaState s) { store.rows.put(s.orderId(), s); }
    }

    public static void main(String[] args) {
        SagaStore store = new SagaStore();
        Outbox outbox = new Outbox();
        Orchestrator node1 = new Orchestrator(store, outbox);

        node1.start("FC-1", 0);
        System.out.println(node1.onReply(new Reply("FC-1", Step.RESERVE_STOCK, true), 1_000));
        System.out.println(node1.onReply(new Reply("FC-1", Step.RESERVE_STOCK, true), 1_500));   // redelivered reply
        System.out.println("-- node1 crashes; node2 takes over from the store");
        Orchestrator node2 = new Orchestrator(store, outbox);
        System.out.println(node2.onReply(new Reply("FC-1", Step.CHARGE_PAYMENT, true), 2_000));
        System.out.println(node2.onReply(new Reply("FC-1", Step.BOOK_RIDER, true), 3_000));

        node2.start("FC-2", 10_000);
        System.out.println(node2.onReply(new Reply("FC-2", Step.RESERVE_STOCK, true), 11_000));
        System.out.println(node2.onReply(new Reply("FC-2", Step.CHARGE_PAYMENT, true), 12_000));
        node2.checkTimeouts(60_000).forEach(System.out::println);
        node2.checkTimeouts(80_000).forEach(System.out::println);
        System.out.println(node2.onReply(new Reply("FC-2", Step.BOOK_RIDER, true), 90_000));      // the rider was booked after all

        System.out.println("outbox commands:");
        outbox.commands.forEach(c -> System.out.println("  " + c));
        System.out.println("saga rows: " + store.rows.values().stream().map(s -> s.orderId() + "=" + s.status() + s.completed()).toList());
    }
}
```

1. A reply only counts if the saga is running and waiting for exactly that step; duplicates are ignored.
2. A step that takes longer than its timeout is treated as failed.
3. Commands go into the outbox (in the same transaction as the saga state, in a real database).
4. Compensations for completed steps are queued in reverse order.
5. Saga state is persisted after every transition, so any instance can continue it.

**Run it:** `java FulfilmentSagaDemo.java`

**Output:**

```text
FC-1 RESERVE_STOCK done
ignored RESERVE_STOCK reply for FC-1 (saga RUNNING, awaiting CHARGE_PAYMENT)
-- node1 crashes; node2 takes over from the store
FC-1 CHARGE_PAYMENT done
FC-1 COMPLETED
FC-2 RESERVE_STOCK done
FC-2 CHARGE_PAYMENT done
FC-2 CANCELLED: BOOK_RIDER timed out
late BOOK_RIDER success for cancelled FC-2, compensating
outbox commands:
  RESERVE_STOCK FC-1
  CHARGE_PAYMENT FC-1
  BOOK_RIDER FC-1
  RESERVE_STOCK FC-2
  CHARGE_PAYMENT FC-2
  BOOK_RIDER FC-2
  RefundPayment FC-2
  ReleaseStock FC-2
  CancelRider FC-2
saga rows: [FC-1=COMPLETED[RESERVE_STOCK, CHARGE_PAYMENT, BOOK_RIDER], FC-2=CANCELLED[RESERVE_STOCK, CHARGE_PAYMENT]]
```

**What to notice:**

- node2 continued FC-1 from the stored state; nothing was held in memory.
- FC-2's rider booking timed out, so payment was refunded and stock released.
- The rider booking then succeeded late, and the saga compensated that too (`CancelRider`). Timeouts don't mean the step didn't happen.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Saga | No distributed locks, independent availability | No isolation; compensations to design |
| Outbox | No lost events | Extra table and relay; duplicates |
| CQRS | Fast, tailored reads | Eventual consistency; more code |
| Event sourcing | Full history, time travel | Complexity, schema evolution, learning curve |
| Idempotent consumer | Safe redelivery | Dedupe storage and cleanup |
| CDC | No app changes, complete capture | Infrastructure; schema coupling if raw |

## 8. When to Use / When NOT to Use

**Use them when:**

- data is split across services and business processes span them;
- events must reliably follow state changes;
- reads need very different shapes or scale from writes.

**Don't when:**

- one database and one transaction can do the job;
- the team hasn't operated message brokers before and the domain is simple;
- strong consistency is a hard requirement (keep that data in one service).

## 9. Best Practices

1. **Use the outbox (or CDC) for every event that announces a state change.** *(Consensus.)*
2. **Make every consumer idempotent.** *(Consensus.)*
3. **Persist saga state; make steps and compensations idempotent.** *(Consensus.)*
4. **Put irreversible steps last in a saga.** *(Consensus.)*
5. **Version events and keep projections rebuildable.** *(Consensus.)*
6. **Use event sourcing only where history is a business need.** *(Consensus.)*
7. **Design UIs for eventual consistency (pending states, optimistic updates).** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Dual write loses events (production failure mode)**  
> **Symptom:** a few orders a day were paid but never packed; the warehouse never heard of them.
> **Root cause:** `orderRepository.save()` then `kafkaTemplate.send()`; pod restarts and broker blips between the two lost events.
> **Fix:** transactional outbox with a relay; reconciliation job comparing orders and fulfilment records.

> 🚨 **Non-idempotent consumer**  
> **Symptom:** loyalty points doubled after a consumer rebalance.
> **Root cause:** `points += n` with no dedupe; redelivered messages were applied again.
> **Fix:** processed-message table in the same transaction.

> 🚨 **Treating a timeout as "didn't happen"**  
> **Symptom:** customers were refunded but riders still arrived.
> **Root cause:** the saga compensated on timeout but ignored the late success reply.
> **Fix:** handle late replies for cancelled sagas by compensating them (Example 3).

> 🚨 **Event sourcing everything**  
> **Symptom:** simple settings screens took weeks; queries needed projections for everything.
> **Root cause:** event sourcing adopted as the default persistence style.
> **Fix:** use it only for aggregates where history matters; CRUD elsewhere.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Kafka producer `enable.idempotence` | true (3.0+) | No duplicates from producer retries | Keep on | Off: duplicates on retry |
| Kafka consumer `enable.auto.commit` | true | Commits offsets in background | false; commit after processing | Auto: lost or duplicate processing |
| Kafka `isolation.level` | read_uncommitted | Visibility of transactional messages | read_committed with transactions | Slight latency |
| Outbox poll interval | — | Relay latency vs DB load | 100 ms – 1 s typical | Too low: DB load |
| Debezium `snapshot.mode` | initial | Snapshot before streaming | `never`/`when_needed` for big tables | Missing history |
| Postgres `max_slot_wal_keep_size` | -1 (unlimited) | Cap WAL kept for slots | Set to protect disk | Slot invalidated if exceeded |

## 12. Security Considerations

- Events often carry personal data; event stores and topics need the same protection and retention rules as databases.
- Event sourcing and GDPR erasure conflict: keep PII outside events or encrypt per subject and delete the key.
- CDC streams expose raw tables, including columns you didn't mean to publish; filter columns at the connector.
- Compensations (refunds) are financially sensitive: authorise and audit them like the original action.

## 13. Performance & Scalability

Outbox adds one insert per transaction; polling adds load proportional to frequency. Projections scale reads independently and can be rebuilt in parallel per partition. Event-sourced aggregates with long streams need snapshots. Saga throughput is limited by the slowest step; persist state compactly. CDC latency is typically sub-second; watch connector lag.

## 14. Testing It

Test sagas as state machines: happy path, failures at each step, duplicates, timeouts, late replies and restarts:

**FulfilmentSagaTest.java**

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfilmentSagaTest {

    FulfilmentSagaDemo.SagaStore store;
    FulfilmentSagaDemo.Outbox outbox;
    FulfilmentSagaDemo.Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        store = new FulfilmentSagaDemo.SagaStore();
        outbox = new FulfilmentSagaDemo.Outbox();
        orchestrator = new FulfilmentSagaDemo.Orchestrator(store, outbox);
    }

    FulfilmentSagaDemo.Reply ok(String id, FulfilmentSagaDemo.Step step) { return new FulfilmentSagaDemo.Reply(id, step, true); }

    @Test
    void happyPathCompletesAndSendsEachCommandOnce() {
        orchestrator.start("FC-1", 0);
        for (var step : FulfilmentSagaDemo.Step.values()) orchestrator.onReply(ok("FC-1", step), 1);
        assertEquals("COMPLETED", store.rows.get("FC-1").status());
        assertEquals(List.of("RESERVE_STOCK FC-1", "CHARGE_PAYMENT FC-1", "BOOK_RIDER FC-1"), outbox.commands);
    }

    @Test
    void duplicateReplyDoesNotAdvanceTwice() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 1);
        String second = orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 2);
        assertTrue(second.startsWith("ignored"));
        assertEquals(2, outbox.commands.size());
    }

    @Test
    void failureCompensatesCompletedStepsInReverse() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 1);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.CHARGE_PAYMENT), 2);
        orchestrator.onReply(new FulfilmentSagaDemo.Reply("FC-1", FulfilmentSagaDemo.Step.BOOK_RIDER, false), 3);
        assertEquals("CANCELLED", store.rows.get("FC-1").status());
        assertEquals(List.of("RefundPayment FC-1", "ReleaseStock FC-1"), outbox.commands.subList(3, 5));
    }

    @Test
    void failureOnFirstStepNeedsNoCompensation() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(new FulfilmentSagaDemo.Reply("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK, false), 1);
        assertEquals(List.of("RESERVE_STOCK FC-1"), outbox.commands);
    }

    @Test
    void stepTimeoutCancelsAndLateSuccessIsCompensated() {
        orchestrator.start("FC-1", 0);
        assertTrue(orchestrator.checkTimeouts(60_000).isEmpty());
        assertEquals(1, orchestrator.checkTimeouts(60_001).size());
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 70_000);
        assertEquals("ReleaseStock FC-1", outbox.commands.get(outbox.commands.size() - 1));
    }

    @Test
    void anotherOrchestratorInstanceContinuesFromTheStore() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 1);
        var replacement = new FulfilmentSagaDemo.Orchestrator(store, outbox);
        replacement.onReply(ok("FC-1", FulfilmentSagaDemo.Step.CHARGE_PAYMENT), 2);
        replacement.onReply(ok("FC-1", FulfilmentSagaDemo.Step.BOOK_RIDER), 3);
        assertEquals("COMPLETED", store.rows.get("FC-1").status());
    }
}
```

Integration-test outbox relays and consumers with Testcontainers (Postgres, Kafka), including killing the consumer mid-batch.

## 15. Observability & Operations

Track outbox backlog and age of the oldest unsent row, consumer lag, duplicates skipped, parked out-of-order events, projection lag (now minus last event time), saga counts by state and age, compensations executed, and CDC connector lag and replication slot size. Alert on stuck sagas (running longer than any step timeout) and on growing outbox backlog. Keep a reconciliation report for money and stock.

## 16. Version Notes

| Year | Change |
|------|--------|
| 1987 | Garcia-Molina and Salem, "Sagas" |
| 2005 | Fowler describes Event Sourcing |
| 2010 | Greg Young popularises CQRS |
| 2016 | Debezium 0.x for CDC |
| 2017 | Kafka 0.11 adds idempotent producers and transactions |
| 2021 | Kafka 3.0 enables producer idempotence by default |
| 2023 | Spring Modulith event publication registry (an outbox) |

## 17. Expert Deep Dive

- **Pivot transactions.** In a saga, steps before the pivot are compensatable, the pivot is the point of no return, and steps after it must be retriable until they succeed.
- **Semantic locks and countermeasures.** `PENDING` states, commutative updates and re-reading values reduce anomalies from the lack of isolation.
- **Durable execution engines.** Temporal and similar engines persist workflow state and replay code deterministically, removing hand-written saga state machines.
- **Exactly-once in Kafka.** Transactions give exactly-once within Kafka (read-process-write); external side effects still need idempotency.
- **Event-carried state transfer vs notification.** Fat events reduce callbacks but couple consumers to more fields; thin events need a follow-up read.

> 📖 **War story: the riders who came anyway**  
> FreshCart's first fulfilment saga cancelled orders when ParcelGo didn't confirm a rider within 60 seconds and refunded the customer. On busy evenings ParcelGo's confirmations took up to 3 minutes, and the saga ignored replies for cancelled orders. Riders turned up at doors with orders that had been refunded, and the dark store had already put the items back on the shelf. **Fixes:** late successful replies for cancelled sagas trigger compensation (`CancelRider`); the rider step's timeout was raised based on measured p99; a reconciliation job compares ParcelGo bookings with active orders nightly. **Lesson:** a timeout tells you that you stopped waiting, not that the other side stopped working.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Why not one transaction across services?** **Each owns its database**; 2PC couples availability.
2. **What's a compensating action?** A **business undo**, like a refund.
3. **What's the outbox?** A **table written in the same transaction** as the data, published later.
4. **What's CQRS?** **Separate models for writes and reads.**
5. **What does idempotent mean?** **Doing it twice has the same effect as once.**

**▸ 🟡 L3**

1. **Orchestration vs choreography?** **Central coordinator** vs **services reacting to events**.
2. **Why is outbox delivery at least once?** The relay can **crash after publishing, before marking**.
3. **How does a projection handle out-of-order events?** **Versions: skip old, park future.**
4. **Event sourcing concurrency?** **Expected-version appends.**
5. **What is CDC?** **Streaming committed changes from the database log.**

**▸ 🔴 L4–L5**

1. **How do you handle saga isolation anomalies?** **Semantic locks, pending states, commutative updates, re-reads.**
2. **Where do you place irreversible steps?** **After the pivot, at the end**, and make them retriable.
3. **Event sourcing and GDPR?** **PII outside events or crypto-shredding.**
4. **Exactly-once processing?** **At-least-once delivery + idempotent effects**; Kafka EOS only inside Kafka.
5. **Raw CDC vs outbox events?** **Outbox is a designed contract**; raw CDC leaks schema.

**▸ 💻 Hands-on**

**Task:** Make `TransactionalOutboxDemo` deliver each event at most once to a consumer even though the relay can publish twice.

> ✅ **Solution**  
> ```java
> Set<String> processed = new HashSet<>();
> for (String event : broker) {
>     if (!processed.add(event)) continue;   // the event ID (here the text) is the dedupe key
>     System.out.println("consumer handled " + event);
> }
> // in production: an event ID column in the outbox, and a processed-messages table on the consumer side
> ```

## 19. Practice

- [ ] Model FreshCart's return-and-refund flow as a saga; mark the pivot step.
    > 💡 **Hint**  
    > Collecting the item from the customer is hard to undo.

- [ ] Implement a polling outbox relay with `FOR UPDATE SKIP LOCKED` against Postgres in Testcontainers.
    > 💡 **Hint**  
    > Run two relay instances and check no row is published by both at once.

- [ ] Add snapshots to `EventSourcingDemo` every 3 events.
    > 💡 **Hint**  
    > Store (version, balance) and replay only later events.

- [ ] Run Debezium against a local Postgres and watch change events for an `UPDATE`.
    > 💡 **Hint**  
    > Look for `op`, `before` and `after` in the payload.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - No cross-service ACID: sagas with compensations; irreversible steps last.
> - Outbox: state + event in one local tx; relay publishes at least once.
> - Consumers are idempotent: processed IDs or versions in the same tx.
> - CQRS: write model enforces rules; read models are projections, eventually consistent.
> - Event sourcing: append-only events, state = fold, expected-version appends.
> - CDC: database log to events; don't expose raw schemas.
>
> ```java
> if (e.version() <= current) return;            // duplicate or stale
> if (e.version() > current + 1) { park(e); return; }
> apply(e);
> ```

## 21. Further Reading

- Chris Richardson, *Microservices Patterns* (2018), chapters on sagas, outbox and CQRS; [microservices.io](https://microservices.io/patterns/data/saga.html).
- Martin Kleppmann, *Designing Data-Intensive Applications* (2017), chapters 11–12.
- Hector Garcia-Molina and Kenneth Salem, "Sagas" (SIGMOD 1987).
- [Debezium documentation](https://debezium.io/documentation/), including the outbox event router.

---

[← Previous: Resilience Patterns](resilience-patterns.md) · [Next: Messaging and Integration Patterns →](messaging-patterns.md)
