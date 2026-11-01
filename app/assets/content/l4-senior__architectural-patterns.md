# Architectural Patterns

L4 🟠 Senior / Lead · ⏱ 45 min read · Prerequisites: [Enterprise Application Patterns](../l3-professional/enterprise-application-patterns.md), [Dependency Injection](../l2-practitioner/dependency-injection.md)

> 📌 **TL;DR**  
> Architectural patterns decide the **shape of a whole application**: which parts exist, which may depend on which, and how they communicate. **Layered** architecture is the familiar default. **Hexagonal** (ports and adapters) and **Clean/Onion** put the domain at the centre and push frameworks, databases and UIs to the edges, so they can be swapped and the core tested alone. A **modular monolith** gives microservice-like boundaries inside one deployable. **Microkernel** builds a small core extended by plugins. **Event-driven** architecture connects parts through published facts instead of direct calls. The senior skill isn't knowing the diagrams; it's choosing based on team size, change patterns and operational cost, and **enforcing** the chosen boundaries with automated checks.

## 1. What It Is

**Plain English.** Design patterns organise classes; architectural patterns organise the whole system. They answer questions like "can the checkout code talk to the database directly?", "how do we swap SMS providers?", and "should payments be a separate service?"

**Definition.**

| Pattern | Core rule | Typical fit |
|---------|-----------|-------------|
| Layered | Each layer depends only on the one below | CRUD-heavy business apps |
| Hexagonal (ports and adapters) | Core defines ports; adapters implement them | Apps with several integrations or UIs |
| Clean / Onion | Dependencies point inwards to entities and use cases | Long-lived domains |
| Modular monolith | Modules with public APIs in one deployable | Most products before (or instead of) microservices |
| Microkernel / plugin | Small core, features as plugins | Platforms, IDEs, payment methods |
| Event-driven | Components publish and react to events | Workflows across many components |

## 2. Why It Exists

Without an agreed shape, every developer chooses their own shortcuts: controllers query tables, domain classes call HTTP clients, modules read each other's data. Each shortcut is small; together they create a "big ball of mud" where any change can break anything. Architectural patterns trade some upfront structure for **changeability**: swapping a payment provider touches one adapter, a new UI reuses the same use cases, and teams can own parts independently.

## 3. Mental Model / Analogy

**Analogy.** A city. **Layered** is a building with floors: you only take the stairs to the next one. **Hexagonal** is a power plant with standard sockets: any appliance (adapter) fits a socket (port). **Clean/Onion** is a castle with walls: the keep (domain) never depends on the market outside. A **modular monolith** is a shopping mall: separate shops with front doors (APIs), one building. **Microkernel** is a phone with apps. **Event-driven** is a town notice board: people post news, and whoever cares reacts.

**Technical model.** An architecture is a set of **components**, **allowed dependencies** between them, and **communication styles** (calls, events). Its value comes from the dependencies it *forbids*.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Layered architecture

**Description.** The application is split into horizontal layers, typically presentation, application (services), domain, and infrastructure (data access). Each layer depends only on layers below it. It's simple, widely understood and the default in most Spring Boot projects.

**Advantages / features.** Clear separation of concerns; easy onboarding; works well for CRUD-centric apps.

**Before (a shortcut through the layers) and after (calls go layer by layer):**

**LayeredArchitectureDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Layered architecture: presentation -> application -> domain -> infrastructure, each layer calling only the one below. */
public class LayeredArchitectureDemo {

    // ---- Infrastructure layer ----
    static final class ProductTable {
        final Map<String, long[]> rows = new TreeMap<>(Map.of("MILK-1L", new long[] {6_800, 40}, "PANEER-200G", new long[] {9_500, 0}));
    }

    // ---- Domain layer ----
    record Product(String sku, long pricePaise, int stock) { boolean available() { return stock > 0; } }

    // ---- Application layer ----
    static final class CatalogService {
        private final ProductTable table;
        CatalogService(ProductTable table) { this.table = table; }
        List<Product> availableProducts() {
            return table.rows.entrySet().stream()
                    .map(e -> new Product(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
                    .filter(Product::available).toList();
        }
    }

    // ---- Presentation layer ----
    static final class CatalogController {
        private final CatalogService service;
        CatalogController(CatalogService service) { this.service = service; }
        String get() {
            return service.availableProducts().stream().map(p -> p.sku() + " Rs " + p.pricePaise() / 100).toList().toString();
        }
    }

    /** Before: a "shortcut" controller that reads the table directly and repeats (and forgets) a rule. */
    static final class ShortcutController {
        private final ProductTable table;
        ShortcutController(ProductTable table) { this.table = table; }
        String get() { return table.rows.keySet().toString(); }    // forgot the "in stock" rule
    }

    public static void main(String[] args) {
        ProductTable table = new ProductTable();
        System.out.println("layered:  " + new CatalogController(new CatalogService(table)).get());
        System.out.println("shortcut: " + new ShortcutController(table).get());
    }
}
```

1. The infrastructure layer holds storage details.
2. The controller only talks to the service, which applies the "in stock" rule.
3. A controller that skips the service also skips its rules: out-of-stock paneer appears.

**Run it:** `java LayeredArchitectureDemo.java`

```text
layered:  [MILK-1L Rs 68]
shortcut: [MILK-1L, PANEER-200G]
```

| Variant | Rule | Trade-off |
|---------|------|-----------|
| Strict layering | Only the layer directly below | Safe but adds pass-through code |
| Relaxed layering | Any lower layer | Less code, more coupling |
| Layers by feature (package by feature) | Layers inside each feature package | Better cohesion, same rules |

**Use** layered architecture for straightforward business apps. **Avoid** letting the domain depend on infrastructure (JPA entities as the domain, repositories returning rows); that's where layered apps rot.
**Real-world Java:** Spring Boot's `@Controller` → `@Service` → `@Repository` convention.
**Best practice:** package by feature first (`orders`, `catalog`), then by layer inside. **Gotcha:** in classic layering, the domain depends on the persistence layer below it; hexagonal and clean architecture invert that.

### Hexagonal (ports and adapters)

**Description.** Alistair Cockburn's hexagonal architecture puts the application core in the middle. The core defines **ports**: inbound ports (use cases) that drivers call, and outbound ports (interfaces) for things it needs, like payments or notifications. **Adapters** connect the outside world: REST controllers and CLIs drive the core; JDBC, HTTP and SMS adapters implement outbound ports. The core has no framework or I/O code.

**Advantages / features.** Swap infrastructure without touching the core; test use cases with fakes; several UIs share one core.

**HexagonalDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Hexagonal architecture: the core defines ports; adapters plug in on both sides and can be swapped. */
public class HexagonalDemo {

    // ---------- Core (no framework, no I/O) ----------
    interface PlaceOrderUseCase { String place(String customer, long amountPaise); }
    interface PaymentPort { boolean charge(String customer, long amountPaise); }
    interface NotificationPort { void send(String customer, String text); }

    static final class OrderService implements PlaceOrderUseCase {
        private final PaymentPort payments; private final NotificationPort notifications;
        private int seq = 1;
        OrderService(PaymentPort payments, NotificationPort notifications) { this.payments = payments; this.notifications = notifications; }
        public String place(String customer, long amount) {
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
            if (!payments.charge(customer, amount)) return "DECLINED";
            String id = "FC-" + seq++;
            notifications.send(customer, "Order " + id + " confirmed");
            return id;
        }
    }

    // ---------- Driven adapters (outbound) ----------
    static final class PayLinkAdapter implements PaymentPort {
        public boolean charge(String customer, long amount) { return amount < 500_000; }   // stands in for an HTTP call
    }
    static final class FakePayments implements PaymentPort {
        boolean approve = true; final List<String> calls = new ArrayList<>();
        public boolean charge(String customer, long amount) { calls.add(customer + ":" + amount); return approve; }
    }
    static final class SmsAdapter implements NotificationPort {
        final List<String> sent = new ArrayList<>();
        public void send(String customer, String text) { sent.add("SMS " + customer + ": " + text); }
    }
    static final class PushAdapter implements NotificationPort {
        final List<String> sent = new ArrayList<>();
        public void send(String customer, String text) { sent.add("PUSH " + customer + ": " + text); }
    }

    // ---------- Driving adapters (inbound) ----------
    static String httpPost(PlaceOrderUseCase useCase, String body) {                        // e.g. a REST controller
        String[] parts = body.split(",");
        return "201 " + useCase.place(parts[0], Long.parseLong(parts[1]));
    }
    static String cliCommand(PlaceOrderUseCase useCase, String[] argv) {                   // e.g. a support tool
        return "placed " + useCase.place(argv[1], Long.parseLong(argv[2]));
    }

    public static void main(String[] args) {
        SmsAdapter sms = new SmsAdapter();
        PlaceOrderUseCase production = new OrderService(new PayLinkAdapter(), sms);
        System.out.println(httpPost(production, "asha,51800"));
        System.out.println(cliCommand(production, new String[] {"place", "ravi", "12000"}));
        System.out.println(sms.sent);

        FakePayments fake = new FakePayments();
        fake.approve = false;
        PushAdapter push = new PushAdapter();
        PlaceOrderUseCase underTest = new OrderService(fake, push);
        System.out.println("with fakes: " + underTest.place("meera", 9_900) + ", payment calls " + fake.calls + ", notifications " + push.sent);
    }
}
```

1. The inbound port: what the application can do.
2. Outbound ports: what the application needs, defined by the core in its own terms.
3. Adapters implement ports; production uses PayLink and SMS, tests use fakes and push, and the core doesn't change.

**Run it:** `java HexagonalDemo.java`

```text
201 FC-1
placed FC-2
[SMS asha: Order FC-1 confirmed, SMS ravi: Order FC-2 confirmed]
with fakes: DECLINED, payment calls [meera:9900], notifications []
```

| Element | Owns | Depends on |
|---------|------|------------|
| Core (use cases + domain) | Business rules, port interfaces | Nothing external |
| Driving adapter | Protocol (HTTP, CLI, messages) | Inbound port |
| Driven adapter | Technology (JDBC, HTTP client, SMTP) | Outbound port, external library |
| Configuration | Wiring adapters to ports | Everything |

**Use** hexagonal when the app has several integrations, several entry points, or integrations likely to change. **Avoid** it for thin CRUD services; ports for every table add ceremony with no benefit.
**Real-world Java:** Spring apps with `domain`, `application` and `adapter` packages; jMolecules annotations (`@Port`, `@Adapter`); Quarkus and Micronaut examples.
**Best practice:** name ports after domain needs (`PaymentPort.charge`), not technologies (`PayLinkClient`). **Gotcha:** letting framework annotations (`@Entity`, `@Transactional`) into the core quietly undoes the pattern.

### Clean and onion architecture

**Description.** Robert C. Martin's Clean Architecture and Jeffrey Palermo's Onion Architecture refine the same idea as concentric rings: entities in the centre, then use cases, then interface adapters, then frameworks and drivers. The **dependency rule**: source code dependencies only point inwards. Inner rings define interfaces; outer rings implement them.

**Advantages / features.** Business rules independent of frameworks, UI and database; the rule is simple enough to check automatically.

**CleanOnionDemo.java**

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean / onion architecture's dependency rule: source code dependencies point inwards only. Entities know nothing
 * of use cases; use cases know nothing of adapters. This demo checks the rule with reflection, like ArchUnit does.
 */
public class CleanOnionDemo {

    enum Ring { ENTITIES, USE_CASES, ADAPTERS, FRAMEWORKS }
    @Retention(RetentionPolicy.RUNTIME) @interface Layer { Ring value(); }

    @Layer(Ring.ENTITIES) record Subscription(String customer, int litresPerDay) {}
    @Layer(Ring.USE_CASES) interface SubscriptionStore { void save(Subscription s); }
    @Layer(Ring.USE_CASES) static final class Subscribe {
        private final SubscriptionStore store;
        Subscribe(SubscriptionStore store) { this.store = store; }
        void run(String customer, int litres) { store.save(new Subscription(customer, litres)); }
    }
    @Layer(Ring.ADAPTERS) static final class JdbcSubscriptionStore implements SubscriptionStore {
        public void save(Subscription s) { }
    }
    @Layer(Ring.ADAPTERS) static final class SubscribeController {
        private final Subscribe useCase;
        SubscribeController(Subscribe useCase) { this.useCase = useCase; }
    }

    /** A rule breaker: a use case that depends on a concrete adapter. */
    @Layer(Ring.USE_CASES) static final class BadRenewal {
        private final JdbcSubscriptionStore store;
        BadRenewal(JdbcSubscriptionStore store) { this.store = store; }
    }

    static List<String> violations(Class<?>... classes) {
        List<String> out = new ArrayList<>();
        for (Class<?> c : classes) {
            Ring own = c.getAnnotation(Layer.class).value();
            List<Class<?>> deps = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) deps.add(f.getType());
            for (Constructor<?> k : c.getDeclaredConstructors()) deps.addAll(List.of(k.getParameterTypes()));
            for (Class<?> d : deps) {
                Layer l = d.getAnnotation(Layer.class);
                if (l != null && l.value().ordinal() > own.ordinal())
                    out.add(c.getSimpleName() + " (" + own + ") depends on " + d.getSimpleName() + " (" + l.value() + ")");
            }
        }
        return out.stream().distinct().toList();
    }

    public static void main(String[] args) {
        System.out.println("clean design: " + violations(Subscription.class, SubscriptionStore.class, Subscribe.class,
                JdbcSubscriptionStore.class, SubscribeController.class));
        System.out.println("with BadRenewal: " + violations(Subscribe.class, BadRenewal.class));
    }
}
```

1. Rings ordered from inside out; each class declares its ring.
2. A rule breaker: a use case depending on a concrete JDBC adapter.
3. A reflection-based check, like an ArchUnit test, finds dependencies pointing outwards.

**Run it:** `java CleanOnionDemo.java`

```text
clean design: []
with BadRenewal: [BadRenewal (USE_CASES) depends on JdbcSubscriptionStore (ADAPTERS)]
```

| | Hexagonal | Onion | Clean |
|-|-----------|-------|-------|
| Picture | Hexagon with ports | Concentric layers | Concentric circles |
| Centre | Application core | Domain model | Entities |
| Key rule | Core defines ports | Dependencies inward | Dependency rule |
| Extra | Driving vs driven sides | Domain services ring | Use-case interactors, presenters |

**Use** these when the domain is complex and long-lived. **Avoid** cargo-culting every ring: many teams need entities, use cases and adapters, not eight packages per feature.
**Real-world Java:** ArchUnit's `onionArchitecture()` and `layeredArchitecture()` rule builders.
**Best practice:** enforce the dependency rule with an ArchUnit test in CI. **Gotcha:** mapping between ring-specific models (entity, DTO, persistence model) can dominate the code; share models where the rule allows.

### Modular monolith

**Description.** One deployable application split into **modules** aligned with business capabilities (subscriptions, billing, delivery). Each module has a public API (interfaces, events) and private internals (its tables, classes). Modules communicate only through APIs or in-process events. You get strong boundaries without network calls, distributed transactions or many deployments.

**Advantages / features.** Simple operations; refactoring across modules is still easy; modules can later be extracted into services along clean seams.

**ModularMonolithDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modular monolith: one deployable, several modules with explicit public APIs and private internals, talking through
 * those APIs or in-process events. Boundaries are as strict as microservices without the network.
 */
public class ModularMonolithDemo {

    // ---------- In-process event bus shared by modules ----------
    static final class Events {
        private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
        <E> void on(Class<E> type, Consumer<E> h) { handlers.computeIfAbsent(type, t -> new ArrayList<>()).add(o -> h.accept(type.cast(o))); }
        void publish(Object e) { handlers.getOrDefault(e.getClass(), List.of()).forEach(h -> h.accept(e)); }
    }

    // ---------- Inventory module ----------
    interface InventoryApi { boolean reserve(String sku, int qty); int available(String sku); }
    static final class InventoryModule {
        private final Map<String, Integer> stock = new HashMap<>(Map.of("MILK-1L", 5));           // internal state
        final InventoryApi api = new InventoryApi() {
            public boolean reserve(String sku, int qty) {
                int left = stock.getOrDefault(sku, 0);
                if (left < qty) return false;
                stock.put(sku, left - qty);
                return true;
            }
            public int available(String sku) { return stock.getOrDefault(sku, 0); }
        };
    }

    // ---------- Orders module ----------
    record OrderPlaced(String orderId, String customer) {}
    static final class OrdersModule {
        private final InventoryApi inventory; private final Events events; private int seq = 1;
        OrdersModule(InventoryApi inventory, Events events) { this.inventory = inventory; this.events = events; }
        String place(String customer, String sku, int qty) {
            if (!inventory.reserve(sku, qty)) return "REJECTED (stock)";
            String id = "FC-" + seq++;
            events.publish(new OrderPlaced(id, customer));
            return id;
        }
    }

    // ---------- Notifications module: depends on nothing but the event ----------
    static final class NotificationsModule {
        final List<String> sent = new ArrayList<>();
        NotificationsModule(Events events) { events.on(OrderPlaced.class, e -> sent.add("SMS to " + e.customer() + ": " + e.orderId() + " placed")); }
    }

    public static void main(String[] args) {
        Events events = new Events();
        InventoryModule inventory = new InventoryModule();
        OrdersModule orders = new OrdersModule(inventory.api, events);
        NotificationsModule notifications = new NotificationsModule(events);

        System.out.println(orders.place("asha", "MILK-1L", 3));
        System.out.println(orders.place("ravi", "MILK-1L", 3));
        System.out.println("milk left: " + inventory.api.available("MILK-1L"));
        System.out.println(notifications.sent);
    }
}
```

1. The inventory module's public API; its stock map is private.
2. Orders publish a fact instead of calling notifications directly.
3. The notifications module depends only on the event, not on orders' internals.

**Run it:** `java ModularMonolithDemo.java`

```text
FC-1
REJECTED (stock)
milk left: 2
[SMS to asha: FC-1 placed]
```

| | Layered monolith | Modular monolith | Microservices |
|-|------------------|------------------|---------------|
| Boundaries | Technical layers | Business modules | Services |
| Deployment | One | One | Many |
| Communication | Method calls | API calls, in-process events | Network |
| Data | Shared schema | Schema per module (logically) | Database per service |
| Operational cost | Low | Low | High |

**Use** a modular monolith as the default for new products and teams under ~50 engineers. **Avoid** shared tables between modules; that's where monoliths become balls of mud.
**Real-world Java:** Spring Modulith (`ApplicationModules.verify()`, `@ApplicationModuleListener`), Java modules (JPMS), Shopify's Rails monolith (same ideas).
**Best practice:** give each module its own schema or table prefix and forbid cross-module joins. **Gotcha:** in-process events are synchronous by default; a slow listener slows the publisher, and failures can roll back the publisher's transaction unless listeners run after commit.

### Microkernel / plugin architecture

**Description.** A minimal core provides lifecycle, registration and shared services; features are **plugins** implementing a contract the core defines. The core discovers plugins (ServiceLoader, classpath scanning, configuration), checks compatibility, and isolates failures. New capabilities ship as new plugins without changing the core.

**Advantages / features.** Extensibility without core changes; third parties can add features; plugins can be enabled per market or customer.

**MicrokernelPluginDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Microkernel: a small core with a plugin contract; features arrive as plugins the core discovers and isolates. */
public class MicrokernelPluginDemo {

    interface PaymentPlugin {
        String id();
        boolean supports(String country);
        String pay(String orderId, long paise);
        default int apiVersion() { return 1; }
    }

    static final class Kernel {
        static final int SUPPORTED_API = 1;
        private final Map<String, PaymentPlugin> plugins = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        void register(PaymentPlugin p) {
            if (p.apiVersion() != SUPPORTED_API) { log.add("refused " + p.id() + ": api v" + p.apiVersion()); return; }
            plugins.put(p.id(), p);
            log.add("loaded " + p.id());
        }

        List<String> methodsFor(String country) { return plugins.values().stream().filter(p -> p.supports(country)).map(PaymentPlugin::id).toList(); }

        String pay(String pluginId, String orderId, long paise) {
            PaymentPlugin p = plugins.get(pluginId);
            if (p == null) return "unknown method " + pluginId;
            try {
                return p.pay(orderId, paise);
            } catch (RuntimeException e) {
                log.add(pluginId + " failed: " + e.getMessage());
                return "payment method unavailable, choose another";
            }
        }
    }

    record Upi() implements PaymentPlugin {
        public String id() { return "upi"; }
        public boolean supports(String c) { return c.equals("IN"); }
        public String pay(String o, long p) { return "UPI collect request for " + o; }
    }
    record Card() implements PaymentPlugin {
        public String id() { return "card"; }
        public boolean supports(String c) { return true; }
        public String pay(String o, long p) { throw new IllegalStateException("gateway timeout"); }
    }
    record CashOnDelivery() implements PaymentPlugin {
        public String id() { return "cod"; }
        public boolean supports(String c) { return c.equals("IN"); }
        public String pay(String o, long p) { return p > 200_000 ? "COD limit is Rs 2000" : "Pay rider on delivery for " + o; }
    }
    record FuturePlugin() implements PaymentPlugin {
        public String id() { return "wallet-v2"; }
        public boolean supports(String c) { return true; }
        public String pay(String o, long p) { return "?"; }
        public int apiVersion() { return 2; }
    }

    public static void main(String[] args) {
        Kernel kernel = new Kernel();
        for (PaymentPlugin p : List.of(new Upi(), new Card(), new CashOnDelivery(), new FuturePlugin())) kernel.register(p);
        System.out.println("methods in IN: " + kernel.methodsFor("IN") + ", in SG: " + kernel.methodsFor("SG"));
        System.out.println(kernel.pay("upi", "FC-1", 51_800));
        System.out.println(kernel.pay("card", "FC-2", 51_800));
        System.out.println(kernel.pay("cod", "FC-3", 300_000));
        System.out.println("kernel log: " + kernel.log);
    }
}
```

1. The plugin contract, including a version for compatibility checks.
2. The kernel refuses plugins built for an unsupported API version.
3. A failing plugin is contained: the customer is asked to choose another method, and the kernel keeps running.

**Run it:** `java MicrokernelPluginDemo.java`

```text
methods in IN: [upi, card, cod], in SG: [card]
UPI collect request for FC-1
payment method unavailable, choose another
COD limit is Rs 2000
kernel log: [loaded upi, loaded card, loaded cod, refused wallet-v2: api v2, card failed: gateway timeout]
```

| Discovery | Example |
|-----------|---------|
| `ServiceLoader` (`META-INF/services`) | JDBC drivers, `java.time` zone providers |
| Classpath scanning | Spring Boot auto-configuration |
| OSGi bundles | Eclipse IDE, Apache Karaf |
| Configuration / database | Payment methods enabled per country |

**Use** microkernel for platforms with a stable core and many optional features. **Avoid** it when features are tightly coupled; the plugin contract becomes a leaky, ever-changing API.
**Real-world Java:** Eclipse, IntelliJ, Jenkins, Gradle, Maven plugins, Spring Boot starters.
**Best practice:** version the plugin API and test plugins against the contract. **Gotcha:** plugins running in-process can still crash or slow the core (memory, threads); isolate risky ones with timeouts or separate processes.

### Event-driven architecture

**Description.** Components communicate by publishing **events** (facts that happened) to a broker; other components subscribe and react. **Event notification** sends a minimal event (an ID); consumers call back for details. **Event-carried state transfer** includes the data, so consumers keep their own copies and don't call back. Producers don't know their consumers.

**Advantages / features.** Loose coupling; new consumers without changing producers; natural fit for workflows and integration.

**EventDrivenArchitectureDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Event-driven architecture: services publish facts; others react. Event notification carries an ID (consumers
 * call back for details); event-carried state transfer carries the data (consumers keep their own copy).
 */
public class EventDrivenArchitectureDemo {

    /** Delivery is asynchronous: published events wait in a queue until consumers get to them. */
    static final class Broker {
        private final Map<String, List<Consumer<Object>>> topics = new HashMap<>();
        private final List<Object[]> queue = new ArrayList<>();
        void subscribe(String topic, Consumer<Object> c) { topics.computeIfAbsent(topic, t -> new ArrayList<>()).add(c); }
        void publish(String topic, Object event) { queue.add(new Object[] {topic, event}); }
        int drain() {
            int delivered = queue.size();
            for (Object[] m : queue) topics.getOrDefault((String) m[0], List.of()).forEach(c -> c.accept(m[1]));
            queue.clear();
            return delivered;
        }
    }

    record PriceChanged(String sku) {}
    record PriceChangedWithData(String sku, long newPricePaise) {}

    public static void main(String[] args) {
        Map<String, Long> catalogDb = new HashMap<>(Map.of("GHEE-1L", 65_000L));
        int[] callbacks = {0};
        Broker broker = new Broker();

        // Notification style: search index calls the catalogue back for the price.
        Map<String, Long> searchIndex = new HashMap<>();
        broker.subscribe("price.changed", e -> {
            String sku = ((PriceChanged) e).sku();
            callbacks[0]++;
            searchIndex.put(sku, catalogDb.get(sku));
        });

        // State transfer style: the cart service keeps its own copy, no callback needed.
        Map<String, Long> cartPrices = new HashMap<>();
        broker.subscribe("price.changed.v2", e -> {
            var p = (PriceChangedWithData) e;
            cartPrices.put(p.sku(), p.newPricePaise());
        });

        catalogDb.put("GHEE-1L", 62_000L);
        broker.publish("price.changed", new PriceChanged("GHEE-1L"));
        broker.publish("price.changed.v2", new PriceChangedWithData("GHEE-1L", 62_000));

        catalogDb.put("GHEE-1L", 64_000L);   // the catalogue changes again before consumers run
        System.out.println("delivered " + broker.drain() + " events");
        System.out.println("search index (read back current state): " + searchIndex + ", callbacks: " + callbacks[0]);
        System.out.println("cart copy (state carried in event):     " + cartPrices + ", callbacks: 0");
    }
}
```

1. A broker that queues events and delivers them later, as real brokers do.
2. Notification style: only the SKU; the consumer calls back for the price.
3. State-transfer style: the price is in the event, so the consumer needs no callback.

**Run it:** `java EventDrivenArchitectureDemo.java`

```text
delivered 2 events
search index (read back current state): {GHEE-1L=64000}, callbacks: 1
cart copy (state carried in event):     {GHEE-1L=62000}, callbacks: 0
```

| Style | Event carries | Consumer gets | Trade-off |
|-------|---------------|---------------|-----------|
| Event notification | ID | Current state (via callback) | Extra calls; producer must be up |
| Event-carried state transfer | Data | State at event time | Larger events; copies of data |
| Event sourcing | Every change | Full history | Complex; see [Distributed Data Patterns](distributed-data-patterns.md) |

The search index read the price *at delivery time* (Rs 640), not the value when the event was published. The cart's copy has the value from the event (Rs 620); the catalogue would publish another event for the second change.

**Use** events for reactions that don't need an immediate answer (notifications, projections, integrations). **Avoid** events for request/response flows that need a result now, and "event chains" so long that nobody can follow the workflow.
**Real-world Java:** Spring `ApplicationEventPublisher`, Kafka, RabbitMQ, Spring Cloud Stream, Axon.
**Best practice:** name events in the past tense (`OrderPlaced`), version their schemas, and make consumers idempotent. **Gotcha:** event-driven systems are eventually consistent; screens and support tools must handle "not updated yet".

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop's saved-address feature, written in three layers so validation lives in one place.

**ThreeLayerDemo.java**

```java
import java.util.HashMap;
import java.util.Map;

/** QuickDrop's address book in three layers: controller (input/output), service (rules), repository (storage). */
public class ThreeLayerDemo {

    static final class AddressRepository { final Map<String, String> rows = new HashMap<>(); }

    static final class AddressService {
        private final AddressRepository repo;
        AddressService(AddressRepository repo) { this.repo = repo; }
        void save(String customer, String pin) {
            if (!pin.matches("[1-9]\\d{5}")) throw new IllegalArgumentException("invalid PIN " + pin);
            repo.rows.put(customer, pin);
        }
        String pinOf(String customer) { return repo.rows.getOrDefault(customer, "none"); }
    }

    static final class AddressController {
        private final AddressService service;
        AddressController(AddressService service) { this.service = service; }
        String post(String customer, String pin) {
            try { service.save(customer, pin); return "200 saved"; }
            catch (IllegalArgumentException e) { return "400 " + e.getMessage(); }
        }
        String get(String customer) { return "200 " + service.pinOf(customer); }
    }

    public static void main(String[] args) {
        AddressController api = new AddressController(new AddressService(new AddressRepository()));
        System.out.println(api.post("asha", "560034"));
        System.out.println(api.post("ravi", "12"));
        System.out.println(api.get("asha") + " / " + api.get("ravi"));
    }
}
```

1. The service holds the rule (valid PIN) and uses the repository.
2. The controller translates between HTTP-like input/output and the service.

**Run it:** `java ThreeLayerDemo.java`

**Output:**

```text
200 saved
400 invalid PIN 12
200 560034 / 200 none
```

**What to notice:**

- Any new entry point (mobile API, batch import) reuses the service and gets the same validation.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart is moving delivery slots from its in-house scheduler to ParcelGo, city by city. Both must work at once, and the checkout code shouldn't care which is used. Define a port, write an adapter per provider, and run one contract test against both.

**SlotProviderPortDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * FreshCart is moving delivery slots from its own scheduler to ParcelGo. The slot logic was buried in a layered
 * service; introducing a port lets both providers plug in, and one contract test runs against every adapter.
 */
public class SlotProviderPortDemo {

    record Slot(String id, String window, int capacityLeft) {}

    interface SlotProvider {
        List<Slot> available(String pin, String date);
        boolean hold(String slotId, String orderId);
    }

    /** Adapter 1: FreshCart's in-house scheduler (in-memory here). */
    static final class InHouseScheduler implements SlotProvider {
        private final Map<String, Integer> capacity = new TreeMap<>(Map.of("S-07", 0, "S-09", 3, "S-18", 10));
        public List<Slot> available(String pin, String date) {
            List<Slot> out = new ArrayList<>();
            capacity.forEach((id, left) -> { if (left > 0) out.add(new Slot(id, id.substring(2) + ":00", left)); });
            return out;
        }
        public boolean hold(String slotId, String orderId) {
            Integer left = capacity.get(slotId);
            if (left == null || left == 0) return false;
            capacity.put(slotId, left - 1);
            return true;
        }
    }

    /** Adapter 2: translates ParcelGo's API model (minutes since midnight, "remaining" counts) into ours. */
    static final class ParcelGoAdapter implements SlotProvider {
        record ParcelGoWindow(String ref, int startMinute, int remaining) {}
        private final Map<String, ParcelGoWindow> remote = new TreeMap<>(Map.of(
                "PG-A", new ParcelGoWindow("PG-A", 540, 2), "PG-B", new ParcelGoWindow("PG-B", 1080, 0)));
        public List<Slot> available(String pin, String date) {
            return remote.values().stream().filter(w -> w.remaining() > 0)
                    .map(w -> new Slot(w.ref(), String.format("%02d:%02d", w.startMinute() / 60, w.startMinute() % 60), w.remaining())).toList();
        }
        public boolean hold(String slotId, String orderId) {
            ParcelGoWindow w = remote.get(slotId);
            if (w == null || w.remaining() == 0) return false;
            remote.put(slotId, new ParcelGoWindow(w.ref(), w.startMinute(), w.remaining() - 1));
            return true;
        }
    }

    /** One contract every adapter must satisfy. */
    static List<String> contract(SlotProvider p) {
        List<String> failures = new ArrayList<>();
        List<Slot> slots = p.available("560034", "2026-11-02");
        if (slots.isEmpty()) failures.add("no slots offered");
        if (slots.stream().anyMatch(s -> s.capacityLeft() <= 0)) failures.add("offered a full slot");
        Slot first = slots.get(0);
        for (int i = 0; i < first.capacityLeft(); i++) if (!p.hold(first.id(), "O" + i)) failures.add("hold " + i + " refused below capacity");
        if (p.hold(first.id(), "one-too-many")) failures.add("overbooked " + first.id());
        if (p.hold("NO-SUCH-SLOT", "x")) failures.add("held an unknown slot");
        return failures;
    }

    public static void main(String[] args) {
        for (SlotProvider p : List.of(new InHouseScheduler(), new ParcelGoAdapter())) {
            System.out.println(p.getClass().getSimpleName() + " offers " + p.available("560034", "2026-11-02"));
        }
        for (SlotProvider p : List.of(new InHouseScheduler(), new ParcelGoAdapter())) {
            List<String> failures = contract(p);
            System.out.println("contract " + p.getClass().getSimpleName() + ": " + (failures.isEmpty() ? "pass" : failures));
        }
    }
}
```

1. The port, in FreshCart's terms (slots, holds).
2. The ParcelGo adapter translates their model (minutes since midnight, "remaining") into ours.
3. A contract test every adapter must pass: no full slots offered, holds up to capacity, never overbook, unknown slots refused.

**Run it:** `java SlotProviderPortDemo.java`

**Output:**

```text
InHouseScheduler offers [Slot[id=S-09, window=09:00, capacityLeft=3], Slot[id=S-18, window=18:00, capacityLeft=10]]
ParcelGoAdapter offers [Slot[id=PG-A, window=09:00, capacityLeft=2]]
contract InHouseScheduler: pass
contract ParcelGoAdapter: pass
```

**What to notice:**

- Checkout depends on `SlotProvider` only; choosing the provider per city is configuration.
- The contract test catches an adapter that, for example, ignores ParcelGo's "remaining = 0" and overbooks.

### Example 3: Production-grade

**Scenario:** DairyDay runs a modular monolith with subscriptions, billing and delivery modules. Billing and delivery react to new subscriptions, but must never see a subscription that was rolled back. The team also wants CI to fail if anyone reaches into another module's internals, adds an undeclared dependency, or creates a cycle.

**ModularMonolithFitnessDemo.java**

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * DairyDay's modular monolith. Modules (subscriptions, billing, delivery) expose an API and keep internals private;
 * cross-module reactions use events published only after the business transaction commits; an architecture check
 * (a fitness function, like Spring Modulith's verify()) rejects undeclared dependencies, access to internals and cycles.
 */
public class ModularMonolithFitnessDemo {

    // ---------- Module metadata and the fitness function ----------
    record Module(String name, Set<String> allowedDependencies) {}
    record Access(String fromModule, String toModule, String toType) {}                   // what static analysis would extract

    static List<String> verify(List<Module> modules, List<Access> accesses) {
        Map<String, Module> byName = new LinkedHashMap<>();
        modules.forEach(m -> byName.put(m.name(), m));
        List<String> problems = new ArrayList<>();
        for (Access a : accesses) {
            if (a.fromModule().equals(a.toModule())) continue;
            if (!byName.get(a.fromModule()).allowedDependencies().contains(a.toModule()))
                problems.add(a.fromModule() + " -> " + a.toModule() + " is not a declared dependency");
            if (a.toType().contains(".internal."))
                problems.add(a.fromModule() + " uses internal type " + a.toType());
        }
        problems.addAll(cycles(modules));
        return problems;
    }

    static List<String> cycles(List<Module> moduleList) {
        Map<String, Module> modules = new LinkedHashMap<>();
        moduleList.forEach(m -> modules.put(m.name(), m));
        List<String> found = new ArrayList<>();
        for (String start : modules.keySet()) {
            Deque<List<String>> paths = new ArrayDeque<>();
            paths.push(List.of(start));
            while (!paths.isEmpty()) {
                List<String> path = paths.pop();
                for (String next : modules.get(path.get(path.size() - 1)).allowedDependencies()) {
                    if (next.equals(start)) {
                        List<String> cycle = new ArrayList<>(path); cycle.add(start);
                        String canonical = String.join(" -> ", cycle);
                        if (start.equals(path.stream().min(String::compareTo).orElseThrow())) found.add("cycle: " + canonical);
                    } else if (!path.contains(next) && modules.containsKey(next)) {
                        List<String> longer = new ArrayList<>(path); longer.add(next); paths.push(longer);
                    }
                }
            }
        }
        return found.stream().distinct().toList();
    }

    // ---------- Runtime: transactional event publication ----------
    static final class UnitOfWork {
        private final List<Object> pending = new ArrayList<>();
        private final EventBus bus;
        UnitOfWork(EventBus bus) { this.bus = bus; }
        void register(Object event) { pending.add(event); }
        void commit() { List<Object> toSend = List.copyOf(pending); pending.clear(); toSend.forEach(bus::publish); }
        void rollback() { pending.clear(); }
    }
    static final class EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
        <E> void on(Class<E> t, Consumer<E> h) { handlers.computeIfAbsent(t, k -> new ArrayList<>()).add(o -> h.accept(t.cast(o))); }
        void publish(Object e) { handlers.getOrDefault(e.getClass(), List.of()).forEach(h -> h.accept(e)); }
    }

    // ---------- Modules ----------
    record SubscriptionStarted(String customer, int litresPerDay, long pricePerLitrePaise) {}   // published API of "subscriptions"

    static final class Subscriptions {
        private final Map<String, Integer> active = new TreeMap<>();
        private final EventBus bus;
        Subscriptions(EventBus bus) { this.bus = bus; }
        String start(String customer, int litres, boolean failAfterSave) {
            UnitOfWork uow = new UnitOfWork(bus);
            try {
                if (litres < 1 || litres > 5) throw new IllegalArgumentException("1..5 litres per day");
                active.put(customer, litres);
                uow.register(new SubscriptionStarted(customer, litres, 6_800));
                if (failAfterSave) throw new IllegalStateException("database write failed");
                uow.commit();
                return "started " + customer;
            } catch (RuntimeException e) {
                active.remove(customer);
                uow.rollback();
                return "failed " + customer + ": " + e.getMessage();
            }
        }
    }
    static final class Billing {
        final Map<String, Long> monthlyPaise = new TreeMap<>();
        Billing(EventBus bus) { bus.on(SubscriptionStarted.class, e -> monthlyPaise.put(e.customer(), e.litresPerDay() * e.pricePerLitrePaise() * 30)); }
    }
    static final class Delivery {
        final Set<String> route = new LinkedHashSet<>();
        Delivery(EventBus bus) { bus.on(SubscriptionStarted.class, e -> route.add(e.customer())); }
    }

    public static void main(String[] args) {
        // 1. Architecture check, run in CI.
        List<Module> declared = List.of(
                new Module("subscriptions", Set.of()),
                new Module("billing", Set.of("subscriptions")),
                new Module("delivery", Set.of("subscriptions")));
        List<Access> clean = List.of(new Access("billing", "subscriptions", "dairyday.subscriptions.SubscriptionStarted"),
                new Access("delivery", "subscriptions", "dairyday.subscriptions.SubscriptionStarted"));
        System.out.println("check, clean build: " + verify(declared, clean));

        List<Access> sneaky = new ArrayList<>(clean);
        sneaky.add(new Access("delivery", "subscriptions", "dairyday.subscriptions.internal.SubscriptionRow"));
        sneaky.add(new Access("delivery", "billing", "dairyday.billing.Invoice"));
        System.out.println("check, sneaky build: " + verify(declared, sneaky));

        List<Module> cyclic = List.of(new Module("subscriptions", Set.of("billing")), new Module("billing", Set.of("subscriptions")),
                new Module("delivery", Set.of("subscriptions")));
        System.out.println("check, cyclic modules: " + cycles(cyclic));

        // 2. Runtime behaviour.
        EventBus bus = new EventBus();
        Subscriptions subscriptions = new Subscriptions(bus);
        Billing billing = new Billing(bus);
        Delivery delivery = new Delivery(bus);
        System.out.println(subscriptions.start("asha", 2, false));
        System.out.println(subscriptions.start("ravi", 1, true));
        System.out.println(subscriptions.start("meera", 9, false));
        System.out.println("billing: " + billing.monthlyPaise + ", delivery route: " + delivery.route);
    }
}
```

1. The architecture check: every cross-module access must be a declared dependency and must not touch `internal` packages.
2. Cycle detection over declared dependencies, reporting each cycle once.
3. A unit of work holds events until commit and discards them on rollback.
4. The subscription event is registered inside the transaction, not published immediately.
5. Billing and delivery depend only on the published event type.

**Run it:** `java ModularMonolithFitnessDemo.java`

**Output:**

```text
check, clean build: []
check, sneaky build: [delivery uses internal type dairyday.subscriptions.internal.SubscriptionRow, delivery -> billing is not a declared dependency]
check, cyclic modules: [cycle: billing -> subscriptions -> billing]
started asha
failed ravi: database write failed
failed meera: 1..5 litres per day
billing: {asha=408000}, delivery route: [asha]
```

**What to notice:**

- The "sneaky" build is rejected for two reasons: delivery used a subscriptions internal type, and depended on billing without declaring it.
- Ravi's subscription failed after saving; because events wait for commit, billing never billed him and delivery never routed him.
- Spring Modulith provides both parts for real: `ApplicationModules.of(App.class).verify()` and `@ApplicationModuleListener` with an event publication registry.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Layered | Simple, familiar | Domain often depends on persistence; pass-through code |
| Hexagonal | Swappable adapters, testable core | More interfaces and mapping |
| Clean / Onion | Framework-independent domain | Ceremony; model mapping between rings |
| Modular monolith | Strong boundaries, simple ops | Needs discipline and tooling to keep boundaries |
| Microkernel | Extensible, per-market features | Plugin API design is hard to change |
| Event-driven | Loose coupling, easy to add consumers | Eventual consistency, harder debugging |

## 8. When to Use / When NOT to Use

**Use them when:**

- the codebase will live for years and grow beyond one team;
- integrations are numerous or likely to change;
- different parts change at different rates.

**Don't overdo them when:**

- the app is a small CRUD service or prototype; a simple layered app is fine;
- the team can't yet enforce boundaries (start with packages and one ArchUnit rule);
- event-driven would hide a simple synchronous workflow.

## 9. Best Practices

1. **Choose the simplest architecture that supports the expected change.** *(Consensus.)*
2. **Organise by business capability first, technical layer second.** *(Consensus.)*
3. **Keep the domain free of framework and I/O code.** *(Consensus for complex domains.)*
4. **Enforce boundaries with tests (ArchUnit, Spring Modulith).** *(Consensus.)*
5. **Prefer a modular monolith before microservices.** *(Consensus; Fowler's "MonolithFirst".)*
6. **Record decisions in ADRs (architecture decision records).** *(Consensus.)*
7. **Publish events after commit; make consumers idempotent.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Events published before commit (production failure mode)**  
> **Symptom:** customers received "subscription started" SMS and first deliveries for subscriptions that showed as failed in the app.
> **Root cause:** the event was published inside the transaction; the transaction later rolled back, but listeners had already acted.
> **Fix:** publish after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`, Spring Modulith's event registry, or a transactional outbox).

> 🚨 **Boundaries only on the whiteboard**  
> **Symptom:** a "modular" monolith where every module imports every other; a change to billing breaks delivery.
> **Root cause:** no automated enforcement; one shortcut at a time.
> **Fix:** architecture tests in CI from day one; module APIs and `internal` packages.

> 🚨 **Architecture astronautics**  
> **Symptom:** a three-endpoint service with 40 interfaces, five model classes per entity and mappers between them.
> **Root cause:** Clean Architecture applied mechanically regardless of complexity.
> **Fix:** collapse rings where they add nothing; keep ports only where variation exists.

> 🚨 **Distributed monolith**  
> **Symptom:** microservices that must be deployed together and fail together.
> **Root cause:** splitting before boundaries were understood; shared database; synchronous call chains.
> **Fix:** merge back into a modular monolith; extract later along proven module boundaries.

## 11. Configuration & Tuning

| Setting / tool | Default | What it does | When to change it | Risk of changing |
|----------------|---------|--------------|-------------------|------------------|
| ArchUnit `layeredArchitecture()` | — | Fails build on layer violations | Add on day one | Initial violations in legacy code |
| Spring Modulith `verify()` | — | Checks module dependencies and cycles | Modular monoliths | Requires package conventions |
| `@ApplicationModuleListener` | — | Async, after-commit, transactional listener | Cross-module reactions | Eventual consistency |
| `spring.modulith.events.republish-outstanding-events-on-restart` | false | Retries unfinished event publications | Production | Duplicate deliveries (need idempotency) |
| JPMS `module-info.java` | none | Compiler-enforced module exports | Libraries, strict boundaries | Build complexity |
| ADR template | — | Records decisions and context | Every significant choice | None |

## 12. Security Considerations

- Adapters are the trust boundary: validate input at driving adapters, and treat data from driven adapters (third-party APIs) as untrusted.
- Authorisation belongs in the application core's use cases, so every entry point (REST, CLI, events) enforces it.
- Plugins run with the core's privileges; review, sign and version them.
- Events may carry personal data across many consumers; minimise payloads and classify topics.

## 13. Performance & Scalability

Layers and ports cost almost nothing at runtime (see [Performance Cost of Patterns](../l3-professional/performance-of-patterns.md)); the real costs are mapping between models and extra queries from strict layering. A modular monolith scales vertically and by running more identical instances; extract a module into a service only when it needs different scaling, availability or release cadence. Event-driven designs absorb spikes through queues but add latency.

## 14. Testing It

Architectures are tested at three levels: the core with fakes (hexagonal), adapters with contract tests, and the structure itself with fitness functions:

**ModularMonolithTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModularMonolithTest {

    static final List<ModularMonolithFitnessDemo.Module> MODULES = List.of(
            new ModularMonolithFitnessDemo.Module("subscriptions", Set.of()),
            new ModularMonolithFitnessDemo.Module("billing", Set.of("subscriptions")),
            new ModularMonolithFitnessDemo.Module("delivery", Set.of("subscriptions")));

    @Test
    void declaredPublicAccessIsAllowed() {
        assertEquals(List.of(), ModularMonolithFitnessDemo.verify(MODULES,
                List.of(new ModularMonolithFitnessDemo.Access("billing", "subscriptions", "x.subscriptions.SubscriptionStarted"))));
    }

    @Test
    void internalsAndUndeclaredDependenciesAreReported() {
        var problems = ModularMonolithFitnessDemo.verify(MODULES, List.of(
                new ModularMonolithFitnessDemo.Access("billing", "subscriptions", "x.subscriptions.internal.Row"),
                new ModularMonolithFitnessDemo.Access("billing", "delivery", "x.delivery.Route")));
        assertEquals(2, problems.size());
    }

    @Test
    void cyclesAreFoundOnceEach() {
        var cyclic = List.of(
                new ModularMonolithFitnessDemo.Module("a", Set.of("b")),
                new ModularMonolithFitnessDemo.Module("b", Set.of("c")),
                new ModularMonolithFitnessDemo.Module("c", Set.of("a")));
        assertEquals(List.of("cycle: a -> b -> c -> a"), ModularMonolithFitnessDemo.cycles(cyclic));
        assertEquals(List.of(), ModularMonolithFitnessDemo.cycles(MODULES));
    }

    @Test
    void eventsArePublishedOnlyAfterCommit() {
        var bus = new ModularMonolithFitnessDemo.EventBus();
        var subscriptions = new ModularMonolithFitnessDemo.Subscriptions(bus);
        var billing = new ModularMonolithFitnessDemo.Billing(bus);
        var delivery = new ModularMonolithFitnessDemo.Delivery(bus);
        subscriptions.start("ok", 1, false);
        subscriptions.start("rolled-back", 1, true);
        assertEquals(Set.of("ok"), billing.monthlyPaise.keySet());
        assertEquals(Set.of("ok"), delivery.route);
    }

    @Test
    void everySlotAdapterPassesTheSameContract() {
        assertTrue(SlotProviderPortDemo.contract(new SlotProviderPortDemo.InHouseScheduler()).isEmpty());
        assertTrue(SlotProviderPortDemo.contract(new SlotProviderPortDemo.ParcelGoAdapter()).isEmpty());
    }

    @Test
    void cleanArchitectureRuleCatchesInwardViolation() {
        assertEquals(1, CleanOnionDemo.violations(CleanOnionDemo.BadRenewal.class).size());
        assertEquals(0, CleanOnionDemo.violations(CleanOnionDemo.Subscribe.class).size());
    }
}
```

In real projects: ArchUnit rules, Spring Modulith's `ApplicationModules.verify()` and `@ApplicationModuleTest`, and Testcontainers for driven adapters.

## 15. Observability & Operations

Tag logs, metrics and traces with the module or bounded context (e.g., `module=billing`) so a monolith's dashboards look like a set of services. Track event publication lag and failed publications. Generate architecture documentation from code (Spring Modulith's `Documenter`, C4 diagrams via Structurizr) so diagrams don't drift.

## 16. Version Notes

| Year / version | Change |
|----------------|--------|
| 1990s | Layered architectures (POSA, 1996) |
| 2005 | Hexagonal architecture (Alistair Cockburn) |
| 2008 | Onion architecture (Jeffrey Palermo) |
| 2012 / 2017 | Clean Architecture (blog post / book, Robert C. Martin) |
| 2017 | Java 9 module system (JPMS) |
| 2018+ | ArchUnit widely adopted |
| 2023 | Spring Modulith 1.0 |

## 17. Expert Deep Dive

- **Fitness functions.** From *Building Evolutionary Architectures*: automated checks (dependency rules, latency budgets, coupling metrics) that protect architectural characteristics as the code evolves.
- **Conway's Law and the inverse Conway manoeuvre.** Architecture mirrors team communication; design team boundaries to match the module boundaries you want.
- **Ports granularity.** One port per external capability (payments, slots), not per method or per table; ports are designed from the core's needs.
- **Evolution path.** Layered → modular monolith → selective extraction is the most common successful path; each step is reversible until the network boundary.
- **Event publication registries.** Persisting outgoing events with the business transaction (outbox) and marking them complete when listeners succeed gives at-least-once delivery even across crashes.

> 📖 **War story: the subscription that billed a ghost**  
> DairyDay's first modular monolith used Spring's `ApplicationEventPublisher` inside `@Transactional` service methods, with plain `@EventListener`s in billing and delivery. One evening, a database failover made about 2% of subscription saves roll back after the event had been published. Billing created invoices and delivery added 340 customers to routes for subscriptions that didn't exist; riders knocked on doors with milk nobody had ordered. **Fixes:** listeners moved to `@ApplicationModuleListener` (after commit, asynchronous, with Spring Modulith's event publication registry so unfinished deliveries are retried after a crash); billing and delivery became idempotent by subscription ID; and an architecture test now fails if a module listens to another module's events with a plain synchronous listener. **Lesson:** module boundaries need transaction boundaries too.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's layered architecture?** **Layers** (presentation, service, data) each calling the one **below**.
2. **Why separate layers?** **Separation of concerns** and reuse.
3. **What's an adapter?** Code that **connects the app to external technology**.
4. **What's a monolith?** **One deployable** application.
5. **What's an event?** A **fact that happened**, published for others to react to.

**▸ 🟡 L3**

1. **Hexagonal vs layered?** Hexagonal: **core defines ports**, infrastructure depends on the core, not vice versa.
2. **What's the dependency rule?** **Dependencies point inwards** to the domain.
3. **What's a modular monolith?** **Business modules with APIs** inside one deployable.
4. **Event notification vs state transfer?** **ID only + callback** vs **data in the event**.
5. **How do you enforce boundaries?** **ArchUnit / Spring Modulith** tests in CI.

**▸ 🔴 L4–L5**

1. **Monolith or microservices for a new product with 8 engineers?** **Modular monolith**, extract later along proven seams.
2. **How do you keep events consistent with the database?** **After-commit publication or outbox**, idempotent consumers.
3. **When is Clean Architecture overkill?** **Simple CRUD, short-lived services, tiny teams.**
4. **How does team structure influence architecture?** **Conway's Law**; align modules with team ownership.
5. **How do you evolve a big ball of mud?** **Identify modules, add fitness functions, strangle incrementally.**

**▸ 💻 Hands-on**

**Task:** Extend the fitness check in `ModularMonolithFitnessDemo` to forbid any module from depending on more than two others.

> ✅ **Solution**  
> ```java
> static List<String> fanOut(List<Module> modules, int max) {
>     return modules.stream()
>             .filter(m -> m.allowedDependencies().size() > max)
>             .map(m -> m.name() + " depends on " + m.allowedDependencies().size() + " modules (max " + max + ")")
>             .toList();
> }
> // in verify(): problems.addAll(fanOut(modules, 2));
> ```

## 19. Practice

- [ ] Add an ArchUnit test to a Spring project: controllers must not access repositories.
    > 💡 **Hint**  
    > `noClasses().that().resideInAPackage("..web..").should().dependOnClassesThat().resideInAPackage("..repository..")`.

- [ ] Convert `ModularMonolithDemo` to Spring Modulith and run `ApplicationModules.verify()`.
    > 💡 **Hint**  
    > One top-level package per module under the application's package.

- [ ] Add a third slot adapter (a test double that fails randomly) and make the contract test catch it.
    > 💡 **Hint**  
    > Use a fixed `Random` seed so the failure is reproducible.

- [ ] Write an ADR choosing between hexagonal and layered for a new service.
    > 💡 **Hint**  
    > Context, decision, consequences; keep it to one page.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Layered: simple default; watch domain → persistence coupling.
> - Hexagonal / Clean / Onion: domain in the middle, dependencies inwards, adapters at the edges.
> - Modular monolith: business modules with APIs in one deployable; the best default.
> - Microkernel: small core + versioned plugin contract.
> - Event-driven: facts, not commands; publish after commit; idempotent consumers.
> - Enforce boundaries with automated fitness functions.
>
> ```java
> interface PaymentPort { boolean charge(String customer, long paise); }   // core defines it
> final class PayLinkAdapter implements PaymentPort                  // edge implements it
> ```

## 21. Further Reading

- Alistair Cockburn, ["Hexagonal Architecture"](https://alistair.cockburn.us/hexagonal-architecture/).
- Robert C. Martin, *Clean Architecture* (2017).
- Mark Richards and Neal Ford, *Fundamentals of Software Architecture* (2nd ed., 2025).
- Neal Ford et al., *Building Evolutionary Architectures* (2nd ed., 2022).
- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/) and [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html).

---

[← Previous: L4 Senior / Lead](index.md) · [Next: Domain-Driven Design: Tactical Patterns →](ddd-tactical.md)
