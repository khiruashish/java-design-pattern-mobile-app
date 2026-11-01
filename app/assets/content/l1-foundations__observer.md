# Observer

L1 🟢 Foundations · ⏱ 45 min read · Prerequisites: [Strategy](strategy.md), [SOLID Principles](solid-principles.md)

> 📌 **TL;DR**  
> Observer lets one object (the subject) notify any number of others (observers) when something happens, without knowing who they are. It's how "order placed" reaches inventory, email and loyalty without checkout calling each one. The pattern is easy; the production questions are hard: sync or async, what happens when an observer fails, and who unsubscribes.

## 1. What It Is

**Plain English.** When a FreshCart order is packed, several parts of the system care: the customer's SMS, analytics, the rider app. Instead of the order calling each of them by name, interested parties *subscribe* to the order, and the order tells all subscribers "I changed". New interested parties can join without changing the order's code.

**Precise definition (GoF intent).** "Define a one-to-many dependency between objects so that when one object changes state, all its dependents are notified and updated automatically." Roles:

- **Subject (observable):** keeps a list of observers and notifies them (`Order`).
- **Observer (listener, subscriber):** an interface with a notification method (`OrderObserver`).
- **Concrete observers:** SMS, analytics, inventory.

Also known as *listeners* (Swing, Spring `ApplicationListener`), *callbacks*, and, through a broker, *publish/subscribe*.

## 2. Why It Exists

Without Observer, the subject calls every dependent directly: `order.pack()` calls `sms.send()`, `analytics.track()`, `riderApp.notify()`. Every new dependent edits the order class, the order can't be tested without all of them, and modules depend on each other in a tangle. Observer inverts the dependency: dependents register themselves, and the subject knows only the observer interface.

## 3. Mental Model / Analogy

**Analogy.** A "notify me when back in stock" button. Customers sign up (subscribe); when stock arrives, the store messages everyone on the list (notify). The store doesn't need to know why each customer wants it, and customers can remove themselves (unsubscribe).

**Technical model.** A subject holds `List<Observer>`; a state change loops over the list calling one method. Everything else (threads, errors, ordering, memory) is a design decision you must make explicitly.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Subject and observer interfaces

**Description.** The subject offers `subscribe` and `unsubscribe`, stores observers in a thread-safe list, and calls them after its state has changed. The observer interface is small and named for the event (`onStatusChanged`).

**Advantages / features.** Subject and observers are decoupled; observers added at runtime; each observer tested alone.

**SubjectObserverDemo.java**

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Classic Observer: an order (subject) notifies interested parties (observers) when its status changes. */
public class SubjectObserverDemo {

    enum Status { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED }

    interface OrderObserver { void onStatusChanged(String orderId, Status from, Status to); }

    static final class Order {
        private final String id;
        private Status status = Status.PLACED;
        private final List<OrderObserver> observers = new CopyOnWriteArrayList<>();

        Order(String id) { this.id = id; }
        void subscribe(OrderObserver o) { observers.add(o); }
        void unsubscribe(OrderObserver o) { observers.remove(o); }

        void moveTo(Status next) {
            Status previous = status;
            status = next;
            observers.forEach(o -> o.onStatusChanged(id, previous, next));
        }
    }

    public static void main(String[] args) {
        Order order = new Order("FC-801");
        OrderObserver sms = (id, from, to) -> System.out.println("  sms: your order " + id + " is " + to.name().toLowerCase().replace('_', ' '));
        OrderObserver analytics = (id, from, to) -> System.out.println("  analytics: " + id + " " + from + "->" + to);
        order.subscribe(sms);
        order.subscribe(analytics);

        System.out.println("PACKED:");
        order.moveTo(Status.PACKED);
        order.unsubscribe(analytics);
        System.out.println("OUT_FOR_DELIVERY:");
        order.moveTo(Status.OUT_FOR_DELIVERY);
    }
}
```

1. The observer interface: one method, named after the event, with the data observers need.
2. `CopyOnWriteArrayList` lets observers subscribe or unsubscribe while a notification is running.
3. Change state *first*, then notify, so observers see a consistent subject.
4. Unsubscribed observers stop receiving events.

**Run it:** `java SubjectObserverDemo.java`

```text
PACKED:
  sms: your order FC-801 is packed
  analytics: FC-801 PLACED->PACKED
OUT_FOR_DELIVERY:
  sms: your order FC-801 is out for delivery
```

| Pros | Cons |
|------|------|
| Subject doesn't know concrete observers | Control flow is harder to follow ("who reacts to this?") |
| Observers added without changing the subject (OCP) | Notification order is usually unspecified |
| Each observer testable alone | Cascades: observers triggering more events |

**Use it** when several independent parts react to one change. **Avoid it** when there's exactly one dependent and it's always the same: call it directly.
**Real-world Java:** Swing `ActionListener`, `java.util.EventListener` family, Spring `ApplicationListener` and `@EventListener`, JPA entity listeners (`@PostPersist`).
**Best practice:** notify after the state change is complete and consistent. **Gotcha:** `java.util.Observable` is deprecated since Java 9; don't use it in new code.

### Push vs. pull model

**Description.** In the **push** model, the event carries the data observers need (`StockChanged(sku, old, new)`). In the **pull** model, observers are told "something changed" and query the subject themselves.

**Advantages / features.** Push: observers don't depend on the subject's API and get exact before/after values. Pull: events stay tiny and observers read only what they need.

**PushPullDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Push model (event carries the data) vs pull model (observer is told "something changed" and asks). */
public class PushPullDemo {

    // ===================== PUSH: the event carries what changed =====================
    record StockChanged(String sku, int oldQty, int newQty) { }

    static final class PushInventory {
        private final List<Consumer<StockChanged>> listeners = new ArrayList<>();
        void onChange(Consumer<StockChanged> l) { listeners.add(l); }
        void set(String sku, int oldQty, int newQty) { listeners.forEach(l -> l.accept(new StockChanged(sku, oldQty, newQty))); }
    }

    // ===================== PULL: the observer gets the subject and reads what it needs =====================
    interface InventoryView { int quantity(String sku); int lowStockThreshold(); }

    static final class PullInventory implements InventoryView {
        private final java.util.Map<String, Integer> stock = new java.util.HashMap<>();
        private final List<Consumer<InventoryView>> listeners = new ArrayList<>();
        void onChange(Consumer<InventoryView> l) { listeners.add(l); }
        void set(String sku, int qty) { stock.put(sku, qty); listeners.forEach(l -> l.accept(this)); }
        public int quantity(String sku) { return stock.getOrDefault(sku, 0); }
        public int lowStockThreshold() { return 5; }
    }

    public static void main(String[] args) {
        PushInventory push = new PushInventory();
        push.onChange(e -> { if (e.newQty() < 5) System.out.println("push: reorder " + e.sku() + " (" + e.oldQty() + " -> " + e.newQty() + ")"); });
        push.set("EGGS-12", 9, 3);

        PullInventory pull = new PullInventory();
        pull.onChange(view -> {
            int eggs = view.quantity("EGGS-12");
            if (eggs < view.lowStockThreshold()) System.out.println("pull: reorder EGGS-12, now " + eggs + ", threshold " + view.lowStockThreshold());
        });
        pull.set("EGGS-12", 3);
        pull.set("MILK-1L", 40);                                                          // pull observer re-checks eggs needlessly
    }
}
```

1. A push event: an immutable record with old and new values.
2. Pull: the subject passes itself (through a read-only view).
3. The pull observer queries what it cares about, even when the change was about something else.

**Run it:** `java PushPullDemo.java`

```text
push: reorder EGGS-12 (9 -> 3)
pull: reorder EGGS-12, now 3, threshold 5
pull: reorder EGGS-12, now 3, threshold 5
```

Notice the pull observer fired twice: it was told about the milk change too and re-checked eggs.

| | Push | Pull |
|---|---|---|
| Event size | Bigger, carries data | Tiny ("changed") |
| Observer coupling to subject | Low (only the event type) | Higher (needs the subject's API) |
| Old values available | Yes | No, unless the subject keeps history |
| Wasted work | Observers ignore irrelevant fields | Observers re-query even when irrelevant |
| Async / remote friendly | Yes, events can be serialised | No, the subject may have changed again |

**Use push** by default, and always for async or remote events. **Use pull** for UI models where observers re-render from current state.
**Real-world Java:** `PropertyChangeEvent` (push, with old and new values); JavaFX `InvalidationListener` (pull) vs `ChangeListener` (push).
**Best practice:** make push events immutable records. **Gotcha:** passing a *mutable* subject to observers lets them change it during notification.

### Listener management and memory leaks

**Description.** The subject holds strong references to its observers. If an observer (a screen, a session) is discarded but never unsubscribed, the subject keeps it, and everything it references, alive forever: the *lapsed listener* problem, one of the most common Java memory leaks.

**Advantages of a subscription handle.** `subscribe` returns an `AutoCloseable`; owners close it in `close()` or with try-with-resources, and forgetting becomes visible in code review.

**ListenerLeakDemo.java**

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** The lapsed-listener problem, and the fix: subscribe() returns a handle you close. */
public class ListenerLeakDemo {

    static final class PriceFeed {
        private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

        void addListener(Consumer<Long> l) { listeners.add(l); }

        AutoCloseable subscribe(Consumer<Long> l) {
            listeners.add(l);
            return () -> listeners.remove(l);
        }

        int listenerCount() { return listeners.size(); }
        void publish(long price) { listeners.forEach(l -> l.accept(price)); }
    }

    /** A product screen that only cares about prices while it's open. */
    static final class ProductScreen implements AutoCloseable {
        private final byte[] images = new byte[20_000];
        private final AutoCloseable subscription;
        ProductScreen(PriceFeed feed) { subscription = feed.subscribe(p -> render(p)); }
        void render(long p) { images[0] = (byte) p; }
        @Override public void close() throws Exception { subscription.close(); }
    }

    public static void main(String[] args) throws Exception {
        PriceFeed feed = new PriceFeed();

        for (int i = 0; i < 1_000; i++) {                                                 // user opens and leaves 1000 screens
            byte[] screenState = new byte[20_000];
            feed.addListener(p -> screenState[0] = (byte) (long) p);                      // nobody ever removes these
        }
        System.out.println("leaky: listeners still registered = " + feed.listenerCount() + " (~" + feed.listenerCount() * 20 / 1000 + " MB kept alive)");

        PriceFeed feed2 = new PriceFeed();
        for (int i = 0; i < 1_000; i++) {
            try (ProductScreen screen = new ProductScreen(feed2)) {
                feed2.publish(129);
            }
        }
        System.out.println("fixed: listeners still registered = " + feed2.listenerCount());
    }
}
```

1. The classic API: add, with a separate remove that callers forget.
2. A better API: subscribing returns the way to unsubscribe.
3. Each screen holds large state; a leaked listener keeps all of it alive.
4. try-with-resources unsubscribes even if an exception is thrown.

**Run it:** `java ListenerLeakDemo.java`

```text
leaky: listeners still registered = 1000 (~20 MB kept alive)
fixed: listeners still registered = 0
```

| Technique | Prevents leaks | Cost |
|-----------|----------------|------|
| Subscription handle (`AutoCloseable`) | Yes, if owners close it | Owners must hold the handle |
| Lifecycle-scoped subscriptions (Android lifecycle, Spring bean scope) | Yes, automatically | Framework-specific |
| Weak references to listeners | Sometimes | Listeners can vanish unexpectedly (lambdas!) |
| Explicit `removeListener` | Only with discipline | Easy to forget |

**Use** subscription handles in your own APIs. **Avoid** weak listener lists unless you fully understand GC timing; a lambda held only weakly can be collected immediately.
**Real-world Java:** Project Reactor's `Disposable`, RxJava's `Disposable`, Android's `LifecycleObserver`.
**Best practice:** tie subscriptions to the owner's lifecycle. **Gotcha:** a lambda capturing `this` keeps the whole enclosing object alive.

### Synchronous vs. asynchronous notification

**Description.** By default observers run **synchronously** on the publisher's thread: a slow observer slows the publisher and an exception can abort it. **Asynchronous** notification hands events to an executor, isolating the publisher, at the cost of ordering, transactions and error visibility.

**Advantages / features.** Sync: simple, ordered, inside the same transaction. Async: publisher stays fast and isolated from failures.

**SyncAsyncDemo.java**

```java
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Synchronous notification shares the publisher's thread and fate; asynchronous isolates it. */
public class SyncAsyncDemo {

    record OrderPlaced(String orderId) { }

    static void slowEmail(OrderPlaced e) { sleep(300); }
    static void brokenLoyalty(OrderPlaced e) { throw new IllegalStateException("loyalty service down"); }

    // ===================== Synchronous, naive =====================
    static String placeSyncNaive(List<Consumer<OrderPlaced>> observers) {
        long start = System.nanoTime();
        try {
            observers.forEach(o -> o.accept(new OrderPlaced("FC-901")));
            return "placed";
        } catch (RuntimeException ex) {
            return "checkout FAILED because an observer threw: " + ex.getMessage();
        } finally {
            System.out.println("  sync naive took >=300ms: " + (elapsedMs(start) >= 300));
        }
    }

    // ===================== Synchronous, isolated =====================
    static String placeSyncIsolated(List<Consumer<OrderPlaced>> observers, ConcurrentLinkedQueue<String> errors) {
        for (Consumer<OrderPlaced> o : observers) {
            try { o.accept(new OrderPlaced("FC-902")); }
            catch (RuntimeException ex) { errors.add("FC-902: " + ex.getMessage()); }
        }
        return "placed";
    }

    // ===================== Asynchronous =====================
    static String placeAsync(List<Consumer<OrderPlaced>> observers, ExecutorService pool, ConcurrentLinkedQueue<String> errors) {
        long start = System.nanoTime();
        for (Consumer<OrderPlaced> o : observers) {
            pool.submit(() -> {
                try { o.accept(new OrderPlaced("FC-903")); }
                catch (RuntimeException ex) { errors.add("FC-903: " + ex.getMessage()); }
            });
        }
        System.out.println("  async returned in <100ms: " + (elapsedMs(start) < 100));
        return "placed";
    }

    public static void main(String[] args) throws Exception {
        List<Consumer<OrderPlaced>> observers = List.of(SyncAsyncDemo::slowEmail, SyncAsyncDemo::brokenLoyalty);
        System.out.println("sync naive -> " + placeSyncNaive(observers));
        var errors = new ConcurrentLinkedQueue<String>();
        System.out.println("sync isolated -> " + placeSyncIsolated(observers, errors));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        System.out.println("async -> " + placeAsync(observers, pool, errors));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("errors recorded: " + errors.stream().sorted().toList());
    }

    static long elapsedMs(long start) { return (System.nanoTime() - start) / 1_000_000; }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
```

1. Naive sync: the publisher waits for every observer and fails if any throws.
2. Isolated sync: catch per observer, record the failure, keep going.
3. Async: each observer runs on the pool; the publisher returns immediately.
4. Wait for the pool before reading results (in a real service it keeps running).

**Run it:** `java SyncAsyncDemo.java`

```text
  sync naive took >=300ms: true
sync naive -> checkout FAILED because an observer threw: loyalty service down
sync isolated -> placed
  async returned in <100ms: true
async -> placed
errors recorded: [FC-902: loyalty service down, FC-903: loyalty service down]
```

| | Synchronous | Asynchronous |
|---|---|---|
| Publisher latency | Sum of all observers | Near zero |
| Failure isolation | Only if you catch per observer | Natural, but errors are easy to lose |
| Ordering | Deterministic | Not guaranteed across observers |
| Transactions | Same transaction as publisher | Outside it: may run before commit or after rollback |
| Back-pressure | Implicit (publisher waits) | Needs bounded queues |

**Use sync** for observers that must succeed with the publisher (stock reservation). **Use async** for side effects like email, analytics and notifications.
**Real-world Java:** Spring `@EventListener` is sync by default; add `@Async` for async, and `@TransactionalEventListener(phase = AFTER_COMMIT)` to run after the transaction commits.
**Best practice:** always catch and record per-observer failures. **Gotcha:** an async email for an order whose transaction then rolls back: the customer gets a confirmation for an order that doesn't exist.

### `java.beans.PropertyChangeSupport` and the Flow API

**Description.** The JDK ships two ready-made observer mechanisms. `PropertyChangeSupport` manages listeners per property and only fires when the value actually changes. `java.util.concurrent.Flow` (Java 9) defines Reactive Streams interfaces (`Publisher`, `Subscriber`, `Subscription`) with **back-pressure**: subscribers request how many items they're ready for. `SubmissionPublisher` is the JDK's implementation.

**Advantages / features.** No hand-written listener lists; built-in old/new values; Flow adds async delivery and back-pressure.

**PropertyChangeFlowDemo.java**

```java
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

/** Two JDK observer APIs: PropertyChangeSupport (sync, per-property) and Flow/SubmissionPublisher (async, back-pressure). */
public class PropertyChangeFlowDemo {

    // ===================== java.beans.PropertyChangeSupport =====================
    static final class Product {
        private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
        private long priceCents; private int stock;
        Product(long price, int stock) { this.priceCents = price; this.stock = stock; }
        void addListener(String property, PropertyChangeListener l) { changes.addPropertyChangeListener(property, l); }
        void setPrice(long p) { long old = priceCents; priceCents = p; changes.firePropertyChange("price", old, p); }
        void setStock(int s) { int old = stock; stock = s; changes.firePropertyChange("stock", old, s); }
    }

    // ===================== java.util.concurrent.Flow =====================
    static final class SlowWarehouseSubscriber implements Flow.Subscriber<String> {
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        private Flow.Subscription subscription;
        public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(2); }
        public void onNext(String pickList) {
            received.add(pickList);
            if (received.size() % 2 == 0) subscription.request(2);                       // ask for more only when ready
        }
        public void onError(Throwable t) { done.countDown(); }
        public void onComplete() { done.countDown(); }
    }

    public static void main(String[] args) throws Exception {
        Product milk = new Product(6_800, 40);
        milk.addListener("price", e -> System.out.println("price watcher: " + e.getOldValue() + " -> " + e.getNewValue()));
        milk.addListener("stock", e -> { if ((int) e.getNewValue() == 0) System.out.println("stock watcher: sold out"); });
        milk.setPrice(6_400);
        milk.setPrice(6_400);                                                             // same value: no event
        milk.setStock(0);

        SlowWarehouseSubscriber warehouse = new SlowWarehouseSubscriber();
        try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(warehouse);
            for (int i = 1; i <= 5; i++) publisher.submit("PL-" + i);
        }
        warehouse.done.await(5, TimeUnit.SECONDS);
        System.out.println("flow subscriber received in order: " + warehouse.received);
    }
}
```

1. A helper that stores listeners and fires `PropertyChangeEvent`s.
2. `firePropertyChange` skips the event when old and new values are equal.
3. Back-pressure: the subscriber asks for two items at a time.
4. `SubmissionPublisher` delivers asynchronously and buffers until subscribers request more.

**Run it:** `java PropertyChangeFlowDemo.java`

```text
price watcher: 6800 -> 6400
stock watcher: sold out
flow subscriber received in order: [PL-1, PL-2, PL-3, PL-4, PL-5]
```

| | PropertyChangeSupport | Flow / SubmissionPublisher |
|---|---|---|
| Threading | Synchronous | Asynchronous (ForkJoinPool by default) |
| Back-pressure | No | Yes (`request(n)`) |
| Granularity | Per property name | Per stream of items |
| Typical use | Models, UI binding, config | Streams of events, integration with Reactor/RxJava |

**Use** `PropertyChangeSupport` for simple model change notification; Flow when consumers can be slower than producers. **Avoid** writing your own Reactive Streams implementation; use Reactor or RxJava for complex pipelines.
**Real-world Java:** JavaBeans and Swing models; Flow interfaces are implemented by Reactor, RxJava and the JDK HTTP client's body publishers.
**Best practice:** use property name constants, not string literals scattered around. **Gotcha:** `PropertyChangeEvent` values are `Object`, so casts are needed and typos in property names fail silently.

### Observer vs. Pub/Sub

**Description.** In Observer, subscribers register *with the subject*; they know it exists. In **publish/subscribe**, publishers and subscribers only know a **broker** and a **topic name**; neither knows the other. Pub/Sub can be in-process (an event bus) or distributed (Kafka, RabbitMQ, Google Pub/Sub).

**Advantages of Pub/Sub.** Even looser coupling; many publishers per topic; with a distributed broker, durability, replay and cross-service delivery.

**ObserverVsPubSubDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Observer: subscribers know the subject. Pub/Sub: publishers and subscribers only know a broker and a topic. */
public class ObserverVsPubSubDemo {

    // ===================== Observer: direct reference to the subject =====================
    static final class Basket {
        private final List<Consumer<Integer>> observers = new ArrayList<>();
        void observe(Consumer<Integer> o) { observers.add(o); }
        void add(int itemCount) { observers.forEach(o -> o.accept(itemCount)); }
    }

    // ===================== Pub/Sub: a broker in the middle =====================
    static final class Broker {
        private final Map<String, List<Consumer<String>>> topics = new HashMap<>();
        void subscribe(String topic, Consumer<String> s) { topics.computeIfAbsent(topic, t -> new ArrayList<>()).add(s); }
        int publish(String topic, String message) {
            List<Consumer<String>> subs = topics.getOrDefault(topic, List.of());
            subs.forEach(s -> s.accept(message));
            return subs.size();
        }
    }

    public static void main(String[] args) {
        Basket basket = new Basket();
        basket.observe(n -> System.out.println("observer: basket badge shows " + n));
        basket.add(3);

        Broker broker = new Broker();
        broker.subscribe("orders.placed", m -> System.out.println("pubsub inventory got " + m));
        broker.subscribe("orders.placed", m -> System.out.println("pubsub loyalty got " + m));
        int delivered = broker.publish("orders.placed", "FC-1201");
        System.out.println("delivered to " + delivered + " subscribers; publisher doesn't know who");
        System.out.println("nobody listening to orders.cancelled: " + broker.publish("orders.cancelled", "FC-1202") + " deliveries");
    }
}
```

1. Observer: you need a reference to the basket to observe it.
2. Pub/Sub: a broker routes messages by topic.
3. The publisher only knows the topic; a topic with no subscribers silently drops messages here.

**Run it:** `java ObserverVsPubSubDemo.java`

```text
observer: basket badge shows 3
pubsub inventory got FC-1201
pubsub loyalty got FC-1201
delivered to 2 subscribers; publisher doesn't know who
nobody listening to orders.cancelled: 0 deliveries
```

| | Observer | Pub/Sub (in-process bus) | Pub/Sub (distributed broker) |
|---|---|---|---|
| Who knows whom | Observer knows subject | Both know only the bus | Both know only the broker |
| Delivery | Method call | Method call or executor | Network, at-least-once usually |
| Durability | None | None | Yes (persisted log or queue) |
| Crosses services | No | No | Yes |
| Debugging | Easy, stack traces | Medium | Hard: needs tracing |

**Use Observer** for objects in one module; **an in-process bus** for decoupled modules in one service; **a broker** across services. **Avoid** a distributed broker when a method call would do.
**Real-world Java:** Guava `EventBus` (in-process, now discouraged by its authors in favour of explicit approaches), Spring `ApplicationEventPublisher`, Kafka clients.
**Best practice:** treat event types as a public contract and version them. **Gotcha:** "fire and forget" to a topic nobody subscribes to loses data silently; monitor it.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L1)

**Scenario:** Customers ask to be alerted when a product's price drops to their target.

**PriceDropAlertDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Minimal Observer: customers watch a product and get told when its price drops below their target. */
public class PriceDropAlertDemo {

    interface PriceWatcher { void priceChanged(String sku, long newPriceCents); }

    record Customer(String name, long targetCents) implements PriceWatcher {
        public void priceChanged(String sku, long p) {
            if (p <= targetCents) System.out.println("  alert " + name + ": " + sku + " is now " + p + " (target " + targetCents + ")");
        }
    }

    static final class Product {
        private final String sku; private long priceCents;
        private final List<PriceWatcher> watchers = new ArrayList<>();
        Product(String sku, long price) { this.sku = sku; this.priceCents = price; }
        void watch(PriceWatcher w) { watchers.add(w); }
        void setPrice(long p) {
            if (p == priceCents) return;
            priceCents = p;
            watchers.forEach(w -> w.priceChanged(sku, p));
        }
    }

    public static void main(String[] args) {
        Product oil = new Product("OIL-SUN-1L", 18_900);
        oil.watch(new Customer("asha", 17_000));
        oil.watch(new Customer("vikram", 16_000));
        System.out.println("price -> 17500"); oil.setPrice(17_500);
        System.out.println("price -> 16900"); oil.setPrice(16_900);
        System.out.println("price -> 15900"); oil.setPrice(15_900);
    }
}
```

1. Each customer is an observer with their own target.
2. No event when the price didn't change, so customers aren't spammed.

**Run it:** `java PriceDropAlertDemo.java`

**Output:**

```text
price -> 17500
price -> 16900
  alert asha: OIL-SUN-1L is now 16900 (target 17000)
price -> 15900
  alert asha: OIL-SUN-1L is now 15900 (target 17000)
  alert vikram: OIL-SUN-1L is now 15900 (target 16000)
```

**What to notice:**

- The product doesn't know about targets; each observer decides whether to alert.
- At 16900 only Asha is alerted; at 15900 both are.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** Orders publish typed events: placed, cancelled, delivered. Inventory, loyalty and SLA monitoring each react to the events they care about.

**OrderEventsDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Realistic Observer: typed order events (sealed) with several independent listeners. */
public class OrderEventsDemo {

    sealed interface OrderEvent permits Placed, Cancelled, Delivered { String orderId(); }
    record Placed(String orderId, String customerId, long totalCents, List<String> skus) implements OrderEvent { }
    record Cancelled(String orderId, String reason) implements OrderEvent { }
    record Delivered(String orderId, String customerId, int minutesTaken) implements OrderEvent { }

    static final class OrderEvents {
        private final List<Consumer<OrderEvent>> listeners = new ArrayList<>();
        void register(Consumer<OrderEvent> l) { listeners.add(l); }
        void publish(OrderEvent e) { listeners.forEach(l -> l.accept(e)); }
    }

    /** Reserves and releases stock. */
    static void inventory(OrderEvent e) {
        switch (e) {
            case Placed p -> System.out.println("inventory: reserve " + p.skus());
            case Cancelled c -> System.out.println("inventory: release stock for " + c.orderId());
            case Delivered d -> { }                                                       // nothing to do
        }
    }

    /** Awards loyalty points on delivery; ignores the other events. */
    static void loyalty(OrderEvent e) {
        if (e instanceof Delivered d) System.out.println("loyalty: points for " + d.customerId());
    }

    /** Tracks late deliveries for operations. */
    static void slaMonitor(OrderEvent e) {
        if (e instanceof Delivered d && d.minutesTaken() > 30) System.out.println("sla: " + d.orderId() + " late by " + (d.minutesTaken() - 30) + " min");
    }

    public static void main(String[] args) {
        OrderEvents events = new OrderEvents();
        events.register(OrderEventsDemo::inventory);
        events.register(OrderEventsDemo::loyalty);
        events.register(OrderEventsDemo::slaMonitor);

        events.publish(new Placed("FC-1301", "cust-7", 84_000, List.of("RICE-5KG", "OIL-1L")));
        events.publish(new Delivered("FC-1301", "cust-7", 38));
        events.publish(new Cancelled("FC-1302", "customer request"));
    }
}
```

1. A sealed interface lists every event type, so the compiler knows the full set.
2. An exhaustive `switch`: adding a new event type forces inventory to decide what to do.
3. Observers that care about one event just use `instanceof`.
4. Method references register observers without extra classes.

**Run it:** `java OrderEventsDemo.java`

**Output:**

```text
inventory: reserve [RICE-5KG, OIL-1L]
loyalty: points for cust-7
sla: FC-1301 late by 8 min
inventory: release stock for FC-1302
```

**What to notice:**

- Checkout publishes one event; three teams' code reacts without checkout knowing them.
- The SLA monitor only cares about late deliveries (38 minutes, 8 over the 30-minute promise).

### Example 3: Production-grade

**Scenario:** An in-process event bus for FreshCart's order service. Stock reservation must succeed or fail with checkout (critical, synchronous). Email, loyalty and promotions run asynchronously. One failing subscriber must not affect others; failures are kept for retry; campaign subscriptions can be removed; everything is counted.

**EventBusDemo.java**

```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Production-grade in-process event bus for FreshCart domain events:
 * - type-safe subscriptions by event class
 * - "critical" subscribers run synchronously (in the publisher's transaction); others run async on a bounded pool
 * - one failing subscriber never affects the publisher or other subscribers; failures go to a dead-letter list
 * - subscribe() returns a handle; metrics per subscriber
 */
public class EventBusDemo {

    interface Subscription extends AutoCloseable { @Override void close(); }

    record Failure(String subscriber, Object event, String error) { }

    static final class EventBus implements AutoCloseable {
        private record Sub(String name, Class<?> type, Consumer<Object> handler, boolean critical) { }

        private final List<Sub> subs = new CopyOnWriteArrayList<>();
        private final ExecutorService async;
        final List<Failure> deadLetters = new CopyOnWriteArrayList<>();
        final Map<String, LongAdder> metrics = new ConcurrentHashMap<>();

        EventBus(ExecutorService async) { this.async = async; }

        <E> Subscription subscribe(String name, Class<E> type, Consumer<? super E> handler, boolean critical) {
            Sub s = new Sub(name, type, o -> handler.accept(type.cast(o)), critical);
            subs.add(s);
            return () -> subs.remove(s);
        }

        void publish(Object event) {
            for (Sub s : subs) {
                if (!s.type().isInstance(event)) continue;
                if (s.critical()) deliver(s, event);
                else async.execute(() -> deliver(s, event));
            }
        }

        private void deliver(Sub s, Object event) {
            try {
                s.handler().accept(event);
                count(s.name() + ".ok");
            } catch (RuntimeException e) {
                count(s.name() + ".failed");
                deadLetters.add(new Failure(s.name(), event, e.getMessage()));
                if (s.critical()) throw e;
            }
        }

        private void count(String key) { metrics.computeIfAbsent(key, k -> new LongAdder()).increment(); }

        @Override public void close() throws InterruptedException {
            async.shutdown();
            if (!async.awaitTermination(5, TimeUnit.SECONDS)) async.shutdownNow();
        }
    }

    record OrderPlaced(String orderId, long totalCents) { }
    record PaymentFailed(String orderId, String reason) { }

    public static void main(String[] args) throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        EventBus bus = new EventBus(Executors.newFixedThreadPool(4));

        bus.subscribe("stock", OrderPlaced.class, e -> log.add("stock reserved for " + e.orderId()), true);
        bus.subscribe("email", OrderPlaced.class, e -> { sleep(Duration.ofMillis(50)); log.add("email sent for " + e.orderId()); }, false);
        bus.subscribe("loyalty", OrderPlaced.class, e -> { if (e.totalCents() > 100_000) throw new IllegalStateException("loyalty API 503"); log.add("points for " + e.orderId()); }, false);
        Subscription promo = bus.subscribe("promo", OrderPlaced.class, e -> log.add("promo banner for " + e.orderId()), false);
        bus.subscribe("support", PaymentFailed.class, e -> log.add("support ticket for " + e.orderId() + ": " + e.reason()), false);

        bus.publish(new OrderPlaced("FC-1401", 45_000));
        promo.close();                                                                    // campaign ended
        bus.publish(new OrderPlaced("FC-1402", 150_000));
        bus.publish(new PaymentFailed("FC-1403", "card declined"));

        try {
            bus.subscribe("fraud", OrderPlaced.class, e -> { throw new IllegalStateException("fraud check timeout"); }, true);
            bus.publish(new OrderPlaced("FC-1404", 9_900));
        } catch (IllegalStateException e) {
            log.add("publisher saw critical failure: " + e.getMessage());
        }

        bus.close();
        new ArrayList<>(log).stream().sorted().forEach(System.out::println);
        System.out.println("dead letters: " + bus.deadLetters.stream().map(f -> f.subscriber() + "(" + f.error() + ")").sorted().toList());
        System.out.println(new TreeMap<>(bus.metrics));
    }

    static void sleep(Duration d) { try { Thread.sleep(d); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
```

1. Type-safe subscription by event class; returns a handle for unsubscribing.
2. Critical subscribers run in the publisher's thread; others go to a bounded pool.
3. Per-subscriber error isolation, with failures kept as dead letters for retry.
4. A critical failure is re-thrown so the publisher (checkout) can roll back.
5. Shutting down drains queued async events before the service stops.

**Run it:** `java EventBusDemo.java`

**Output:**

```text
email sent for FC-1401
email sent for FC-1402
email sent for FC-1404
points for FC-1401
points for FC-1404
promo banner for FC-1401
publisher saw critical failure: fraud check timeout
stock reserved for FC-1401
stock reserved for FC-1402
stock reserved for FC-1404
support ticket for FC-1403: card declined
dead letters: [fraud(fraud check timeout), loyalty(loyalty API 503)]
{email.ok=3, fraud.failed=1, loyalty.failed=1, loyalty.ok=2, promo.ok=1, stock.ok=3, support.ok=1}
```

**What to notice:**

- Loyalty failed for the big order, but email and stock still worked; the failure is in the dead letters.
- The promo subscriber stopped after its handle was closed (only FC-1401 got a banner).
- **The subtle bug:** an email was sent for FC-1404 even though the critical fraud check failed, because async subscribers were queued before fraud ran. In production you publish async events only *after* the transaction commits (Spring's `@TransactionalEventListener`, or the [Transactional Outbox](../l4-senior/distributed-data-patterns.md)).

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Simplicity | Subject code stays small | Hidden control flow: "what happens when X?" needs a search |
| Coupling | Subject depends only on an interface | Implicit coupling through event contents |
| Performance | Async observers keep publishers fast | Sync observers add latency; async needs queues and threads |
| Reliability | Failures can be isolated per observer | In-process events are lost on crash |
| Testability | Observers tested alone with sample events | Integration behaviour (order, timing) is harder to test |
| Operability | Metrics per subscriber show health | Event storms and cascades are hard to diagnose |

## 8. When to Use / When NOT to Use

**Use it when:**

- several independent parts must react to one change, and the list grows over time;
- the reacting code belongs to other modules or teams;
- UI or models must stay in sync with state.

**Don't use it when:**

- there's one fixed dependent: a direct call is clearer;
- the caller needs a result back from the reactor: that's a method call, not an event;
- delivery must survive crashes or cross services: use a durable broker and the [Outbox pattern](../l4-senior/distributed-data-patterns.md).

## 9. Best Practices

1. **Make events immutable records named in the past tense** (`OrderPlaced`). *(Consensus.)*
2. **Isolate failures per observer** and record them. *(Consensus.)*
3. **Return a subscription handle** and tie it to the owner's lifecycle. *(Consensus.)*
4. **Decide sync vs async per observer**, not globally. *(Opinion.)*
5. **Publish side-effect events after the transaction commits.** *(Consensus.)*
6. **Use bounded queues for async delivery**, and decide what happens when they're full. *(Consensus.)*
7. **Don't let observers modify the subject during notification.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Lapsed listener**  
> **Symptom:** heap grows with every screen opened; heap dumps show thousands of listeners.
> **Root cause:** observers registered and never removed.
> **Fix:** subscription handles closed by the owner's lifecycle.

> 🚨 **Confirmation for an order that never existed (production failure mode)**  
> **Symptom:** customers got "order confirmed" emails, but no order was in the database.
> **Root cause:** the email observer ran asynchronously before the order transaction rolled back (payment declined).
> **Fix:** after-commit listeners or a transactional outbox.

> 🚨 **Event cascade**  
> **Symptom:** one stock update causes hundreds of events and a `StackOverflowError`.
> **Root cause:** observers that change state and publish events that trigger each other.
> **Fix:** don't publish from inside observers, or guard against re-entry; model the flow explicitly.

> 🚨 **Observer that throws stops the rest**  
> **Symptom:** analytics outage also breaks SMS notifications.
> **Root cause:** sync notification loop without per-observer `try/catch`.
> **Fix:** isolate each observer (Example 3).

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Async pool size (Example 3) | 4 | Parallel async subscribers | Many slow subscribers | Too many threads; downstream overload |
| Queue capacity | Unbounded (demo) | Buffer for async events | Always bound it in production | Full queue: block, drop or fail; choose |
| Critical flag per subscriber | false | Runs in publisher's thread | Must succeed with the publisher | Adds latency and failure coupling |
| Shutdown wait | 5 s | Drain time for queued events | Long-running subscribers | Lost events if too short |

## 12. Security Considerations

- Events can carry personal data (addresses, phone numbers) to every subscriber; send IDs and let authorised subscribers look up details.
- In plugin systems, a malicious observer runs with the publisher's privileges; only register trusted code.
- Don't log full events in dead-letter queues if they contain personal or payment data; redact first.

## 13. Performance & Scalability

Synchronous notification costs the sum of observer times; one slow observer slows every publish. Asynchronous delivery moves that cost to a pool, where queue size and pool size decide throughput and memory. `CopyOnWriteArrayList` makes iteration cheap and copying on subscribe expensive, which suits "many notifications, few subscriptions". For high-volume streams use Flow-based libraries with back-pressure.

## 14. Testing It

Test the bus with a direct executor, so "async" delivery is deterministic:

**EventBusTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EventBusTest {

    /** Runs "async" tasks immediately on the calling thread, so tests are deterministic. */
    static final class DirectExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;
        public void execute(Runnable r) { r.run(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    private final EventBusDemo.EventBus bus = new EventBusDemo.EventBus(new DirectExecutor());
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Test
    void deliversOnlyMatchingEventTypes() {
        bus.subscribe("a", EventBusDemo.OrderPlaced.class, e -> seen.add("placed " + e.orderId()), false);
        bus.publish(new EventBusDemo.PaymentFailed("o1", "declined"));
        bus.publish(new EventBusDemo.OrderPlaced("o2", 100));
        assertEquals(List.of("placed o2"), seen);
    }

    @Test
    void closedSubscriptionsStopReceiving() {
        var sub = bus.subscribe("a", EventBusDemo.OrderPlaced.class, e -> seen.add(e.orderId()), false);
        bus.publish(new EventBusDemo.OrderPlaced("o1", 100));
        sub.close();
        bus.publish(new EventBusDemo.OrderPlaced("o2", 100));
        assertEquals(List.of("o1"), seen);
    }

    @Test
    void aFailingNonCriticalSubscriberIsIsolatedAndDeadLettered() {
        bus.subscribe("bad", EventBusDemo.OrderPlaced.class, e -> { throw new IllegalStateException("boom"); }, false);
        bus.subscribe("good", EventBusDemo.OrderPlaced.class, e -> seen.add(e.orderId()), false);
        bus.publish(new EventBusDemo.OrderPlaced("o1", 100));
        assertEquals(List.of("o1"), seen);
        assertEquals("bad", bus.deadLetters.get(0).subscriber());
    }

    @Test
    void aFailingCriticalSubscriberFailsThePublisher() {
        bus.subscribe("fraud", EventBusDemo.OrderPlaced.class, e -> { throw new IllegalStateException("timeout"); }, true);
        assertThrows(IllegalStateException.class, () -> bus.publish(new EventBusDemo.OrderPlaced("o1", 100)));
        assertEquals(1, bus.metrics.get("fraud.failed").sum());
    }
}
```

Test each observer separately by calling it with a sample event; you don't need the subject at all.

## 15. Observability & Operations

Count deliveries and failures per subscriber, track async queue depth and the age of the oldest queued event, and alert on dead-letter growth. Put the event ID and order ID in logs and traces of every subscriber, so you can follow one order through all its reactions.

## 16. Version Notes

| Java | Change relevant to Observer |
|------|-----------------------------|
| 1.1 | `java.util.EventListener`, JavaBeans `PropertyChangeSupport` |
| 5 | `CopyOnWriteArrayList` for listener lists |
| 8 | Lambdas and method references as observers |
| 9 | `java.util.Observable`/`Observer` deprecated; `java.util.concurrent.Flow` and `SubmissionPublisher` added |
| 17 | Sealed event hierarchies |
| 21 | Pattern matching for `switch` over sealed events; virtual threads for simple async delivery |

## 17. Expert Deep Dive

- **In-process events are not durable.** If the JVM dies after commit but before async observers run, those reactions are lost. Seniors pair domain events with a transactional outbox and a broker for anything that must happen.
- **Ordering is a design decision.** Per-aggregate ordering (all events for order FC-1 in order) is usually what's needed; global ordering is expensive. Partition by aggregate ID in brokers.
- **Event design.** Prefer events that state facts (`OrderPlaced` with IDs and key values) over commands in disguise (`SendEmailForOrder`). Facts let new consumers appear without changing publishers.
- **Choreography vs orchestration.** Many observers reacting to events (choreography) scales teams but hides the end-to-end flow. For multi-step business processes with compensation, an explicit orchestrator ([Saga](../l4-senior/distributed-data-patterns.md)) is often clearer.

> 📖 **War story: the loyalty points that paid out twice**  
> A grocery platform's loyalty service listened for `OrderDelivered` events. During a broker incident, events were redelivered (at-least-once delivery), and the listener wasn't idempotent: it added points again for each copy. Some customers got triple points before anyone noticed. The fix was to record processed event IDs and ignore duplicates. **Lesson:** once observers cross a network, every observer must be idempotent, because "exactly once" delivery is something you build, not something you get.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is Observer?** A **one-to-many** dependency: the **subject notifies observers** of changes.
2. **Give a Java example.** **Swing `ActionListener`**, Spring **`@EventListener`**, `PropertyChangeListener`.
3. **Why is `java.util.Observable` not used?** **Deprecated since Java 9**: a class not an interface, not thread-safe, limited event model.
4. **What is a lapsed listener?** An observer **never unsubscribed**, kept alive by the subject: a **memory leak**.
5. **Push vs pull?** Push sends **data in the event**; pull sends a notification and observers **query the subject**.

**▸ 🟡 L3**

1. **What happens if an observer throws?** In a naive loop, **the rest are skipped and the publisher fails**; isolate with per-observer `try/catch`.
2. **Sync vs async observers: trade-offs?** **Latency and isolation** vs **ordering, transactions and lost errors**.
3. **How does Spring avoid emails for rolled-back orders?** **`@TransactionalEventListener(phase = AFTER_COMMIT)`**.
4. **Observer vs Pub/Sub?** Observer knows the **subject**; Pub/Sub uses a **broker and topics**.
5. **What does Flow add?** **Back-pressure** via `Subscription.request(n)` and async delivery.

**▸ 🔴 L4–L5**

1. **Make order events reliable across services.** **Transactional outbox**, broker with **per-order partitioning**, **idempotent consumers**, dead-letter queues and replay.
2. **How do you keep event-driven flows understandable?** **Event catalogue**, tracing with **correlation IDs**, and orchestration for complex processes.
3. **How do you evolve event schemas?** **Additive changes**, versioned types, schema registry, consumers tolerant of unknown fields.
4. **A consumer falls behind by hours. What do you do?** Check **lag metrics**, scale consumers to partition count, apply **back-pressure** or shed non-critical work.
5. **Now 10x traffic and the in-process bus queue grows unbounded. Fix it.** **Bound the queue**, decide **block vs drop vs reject**, move heavy subscribers to a **broker**.

**▸ 💻 Hands-on**

**Task:** Make the loyalty subscriber idempotent so a duplicate `Delivered` event doesn't award points twice.

> ✅ **Solution**  
> ```java
> static final java.util.Set<String> processed = java.util.concurrent.ConcurrentHashMap.newKeySet();
>
> static void loyalty(OrderEvent e) {
>     if (e instanceof Delivered d && processed.add(d.orderId())) {   // add() is false for duplicates
>         System.out.println("loyalty: points for " + d.customerId());
>     }
> }
> ```
> In production the "processed" set lives in the loyalty database, updated in the same transaction as the points.

## 19. Practice

- [ ] Add an `OrderRefunded` event to `OrderEventsDemo` and see where the compiler forces changes.
    > 💡 **Hint**  
    > Add it to the `permits` list; the `switch` in `inventory` stops compiling until you handle it.

- [ ] Give `EventBusDemo` a bounded queue of 100 events that rejects with a counted error when full.
    > 💡 **Hint**  
    > `new ThreadPoolExecutor(4, 4, 0, SECONDS, new ArrayBlockingQueue<>(100))` with a `RejectedExecutionHandler`.

- [ ] Rewrite `PriceDropAlertDemo` with `PropertyChangeSupport`.
    > 💡 **Hint**  
    > Fire `"price"` with old and new values; each customer is a `PropertyChangeListener`.

- [ ] Find a class in your codebase with `addXListener` and check that every caller removes its listener.
    > 💡 **Hint**  
    > Search for `addListener` calls and look for the matching `remove` in close/destroy methods.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: one-to-many notification; subject knows only the observer interface.
> - Push events (immutable records) by default; pull for UI re-rendering.
> - Return subscription handles; unsubscribe with the owner's lifecycle.
> - Isolate failures per observer; choose sync or async per observer; async side effects after commit.
> - Observer (knows subject) vs Pub/Sub (knows only a broker and topic).
>
> ```java
> try (var sub = feed.subscribe(price -> render(price))) {
>     // screen open
> }   // unsubscribed automatically
> ```

## 21. Further Reading

- Gamma et al., *Design Patterns* (1994): Observer.
- [java.util.concurrent.Flow API documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html)
- [Spring Framework reference: Application Events and Listeners](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- Martin Kleppmann, *Designing Data-Intensive Applications* (2017), chapter 11 on stream processing and event delivery guarantees.

---

[← Previous: Strategy](strategy.md) · [Next: Template Method →](template-method.md)
