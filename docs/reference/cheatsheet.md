---
tags: [reference]
---
# Cheat Sheet

!!! abstract "TL;DR"
    Every pattern on one page: intent, when to reach for it, and a minimal Java 21 skeleton in the FreshCart domain. Follow the link on each name for the full deep dive with runnable examples.

**How to read this page.** Each entry has a one-line **intent**, a **use when** hint, and the smallest code that shows the structure. Skeletons are trimmed for scanning; the linked pages have complete, runnable versions.

## Creational patterns

### [Singleton](../l1-foundations/singleton.md)
**Intent:** exactly one instance, globally reachable. **Use when:** a truly single resource (a registry or config); prefer DI-managed singletons.

```java
public enum PriceRegistry { INSTANCE; long priceOf(String sku) { return 6800; } }
```

### [Factory Method](../l1-foundations/factory-method.md)
**Intent:** let a method decide which subclass to create. **Use when:** creation logic varies by input or subclass.

```java
static Notifier notifierFor(Channel c) {
    return switch (c) { case SMS -> new SmsNotifier(); case PUSH -> new PushNotifier(); };
}
```

### [Abstract Factory](../l2-practitioner/abstract-factory.md)
**Intent:** create families of related objects without naming concrete classes. **Use when:** products must match (one partner's client + parser + signer).

```java
interface PartnerKit { DeliveryClient client(); LabelPrinter printer(); }
final class QuickDropKit implements PartnerKit { /* QuickDrop implementations */ }
```

### [Builder](../l1-foundations/builder.md)
**Intent:** construct complex objects step by step. **Use when:** many optional parameters, or validation across fields.

```java
Order order = Order.builder().customer("C-17").item("MILK-1L", 2).slot("18:00-20:00").build();
```

### [Prototype](../l2-practitioner/prototype.md)
**Intent:** create objects by copying a configured instance. **Use when:** construction is expensive or templates vary slightly.

```java
record BasketTemplate(String name, List<String> skus) { BasketTemplate withName(String n) { return new BasketTemplate(n, skus); } }
```

### [Object Pool](../l2-practitioner/object-pool.md)
**Intent:** reuse expensive objects instead of creating them. **Use when:** creation is truly expensive (connections); not for ordinary objects.

```java
HikariDataSource pool = new HikariDataSource(config);   // borrow with getConnection(), return with close()
```

## Structural patterns

### [Adapter](../l1-foundations/adapter.md)
**Intent:** make an incompatible interface fit the one you expect. **Use when:** integrating a partner or legacy API.

```java
final class ParcelGoAdapter implements DeliveryClient {
    private final ParcelGoApi api;
    public Quote quote(Address a) { return Quote.ofRupees(api.getRate(a.pincode())); }
}
```

### [Bridge](../l2-practitioner/bridge.md)
**Intent:** separate an abstraction from its implementation so both vary. **Use when:** two independent dimensions (notification type × channel).

```java
abstract class Alert { protected final Channel channel; abstract void send(String user); }
```

### [Composite](../l2-practitioner/composite.md)
**Intent:** treat single objects and groups uniformly. **Use when:** tree structures (bundles of products, category menus).

```java
sealed interface BasketItem permits Product, Bundle { long pricePaise(); }
record Bundle(List<BasketItem> items) implements BasketItem { public long pricePaise() { return items.stream().mapToLong(BasketItem::pricePaise).sum(); } }
```

### [Decorator](../l1-foundations/decorator.md)
**Intent:** add behaviour by wrapping, without subclassing. **Use when:** optional, stackable features (logging, caching, retries).

```java
PriceService prices = new CachingPriceService(new LoggingPriceService(new DbPriceService()));
```

### [Facade](../l1-foundations/facade.md)
**Intent:** a simple entry point to a complex subsystem. **Use when:** callers need one operation that spans many services.

```java
final class CheckoutFacade { Receipt checkout(Cart c) { stock.reserve(c); var p = payments.charge(c); return orders.place(c, p); } }
```

### [Flyweight](../l2-practitioner/flyweight.md)
**Intent:** share immutable state across many objects. **Use when:** millions of similar objects (catalogue units, icons).

```java
static final Map<String, Unit> UNITS = new ConcurrentHashMap<>();
static Unit unit(String code) { return UNITS.computeIfAbsent(code, Unit::new); }
```

### [Proxy](../l2-practitioner/proxy.md)
**Intent:** a stand-in that controls access to the real object. **Use when:** lazy loading, access control, remoting, caching.

```java
final class LazyImageProxy implements ProductImage { private ProductImage real; public byte[] bytes() { if (real == null) real = load(); return real.bytes(); } }
```

## Behavioural patterns

### [Strategy](../l1-foundations/strategy.md)
**Intent:** interchangeable algorithms behind one interface. **Use when:** pricing, fees or routing rules vary.

```java
interface DeliveryFee { long feePaise(long basketPaise); }
DeliveryFee standard = b -> b >= 50000 ? 0 : 2900;
```

### [Observer](../l1-foundations/observer.md)
**Intent:** notify dependents when state changes. **Use when:** one event, many independent reactions.

```java
orderEvents.subscribe(e -> sms.send(e.customer(), "Order " + e.id() + " placed"));
```

### [Template Method](../l1-foundations/template-method.md)
**Intent:** fixed algorithm skeleton, variable steps. **Use when:** several processes share structure.

```java
abstract class ReportJob { final void run() { var d = load(); write(format(d)); } abstract List<Row> load(); abstract String format(List<Row> d); }
```

### [Chain of Responsibility](../l2-practitioner/chain-of-responsibility.md)
**Intent:** pass a request along handlers until one handles it. **Use when:** validation or approval pipelines.

```java
interface CouponRule { Optional<String> reject(Order o); }
rules.stream().map(r -> r.reject(order)).flatMap(Optional::stream).findFirst();
```

### [Command](../l2-practitioner/command.md)
**Intent:** a request as an object. **Use when:** queueing, undo, retries, audit.

```java
record AddItem(String sku, int qty) implements CartCommand { public void apply(Cart c) { c.add(sku, qty); } }
```

### [Iterator](../l2-practitioner/iterator.md)
**Intent:** traverse without exposing internals. **Use when:** custom collections, paging through APIs.

```java
final class OrderPages implements Iterable<Order> { public Iterator<Order> iterator() { return new PageIterator(api); } }
```

### [Mediator](../l2-practitioner/mediator.md)
**Intent:** centralise interaction between objects. **Use when:** many components talk to each other (checkout screen widgets).

```java
final class CheckoutMediator { void slotChanged(Slot s) { fee.recalculate(s); summary.refresh(); } }
```

### [Memento](../l2-practitioner/memento.md)
**Intent:** capture and restore state without breaking encapsulation. **Use when:** undo, drafts.

```java
record CartSnapshot(Map<String, Integer> lines) {}
CartSnapshot s = cart.save(); cart.restore(s);
```

### [State](../l2-practitioner/state.md)
**Intent:** behaviour changes with internal state. **Use when:** order lifecycles with rules per state.

```java
sealed interface OrderState permits Placed, Packed, Delivered { OrderState next(); }
```

### [Visitor](../l2-practitioner/visitor.md)
**Intent:** add operations to a fixed type hierarchy. **Use when:** many operations over stable types; in Java 21 often a pattern-matching `switch`.

```java
long tax(BasketItem i) { return switch (i) { case Product p -> p.pricePaise() * 5 / 100; case Bundle b -> b.items().stream().mapToLong(this::tax).sum(); }; }
```

### [Interpreter](../l2-practitioner/interpreter.md)
**Intent:** evaluate sentences of a small language. **Use when:** rule or coupon expressions.

```java
sealed interface Expr permits MinBasket, And { boolean eval(Order o); }
```

## Other common patterns

| Pattern | Intent | Skeleton |
|---------|--------|----------|
| [Dependency Injection](../l2-practitioner/dependency-injection.md) | Supply dependencies from outside | `new CheckoutService(prices, payments)` |
| [Null Object](../l2-practitioner/null-object.md) | A do-nothing implementation instead of null | `Coupon.NONE` with `discount() == 0` |
| [Specification](../l2-practitioner/specification.md) | Combinable business rules | `isOrganic.and(inStock).test(product)` |
| [Testing Patterns](../l2-practitioner/testing-patterns.md) | Fakes, builders and object mothers | `anOrder().withItem("MILK-1L").build()` |

## Architecture and distributed patterns

| Pattern | One-line intent | Page |
|---------|-----------------|------|
| Layered, hexagonal, clean | Keep the domain independent of delivery and infrastructure | [Architectural Patterns](../l4-senior/architectural-patterns.md) |
| Bounded context, context map | Split a large model where language changes | [DDD Strategic](../l4-senior/ddd-strategic.md) |
| Aggregate, entity, value object, repository | Consistency boundaries inside a model | [DDD Tactical](../l4-senior/ddd-tactical.md) |
| API gateway, service discovery, strangler fig, sidecar | Build and evolve microservices | [Microservice Patterns](../l4-senior/microservice-patterns.md) |
| Timeout, retry, circuit breaker, bulkhead, rate limiter | Survive failing dependencies | [Resilience Patterns](../l4-senior/resilience-patterns.md) |
| Saga, outbox, CQRS, event sourcing, CDC | Consistency across services | [Distributed Data Patterns](../l4-senior/distributed-data-patterns.md) |
| Pub/sub, competing consumers, dead letter, router | Reliable messaging | [Messaging Patterns](../l4-senior/messaging-patterns.md) |
| Cache-aside, read/write-through, write-behind | Faster reads, controlled staleness | [Caching Patterns](../l4-senior/caching-patterns.md) |
| Versioning, pagination, idempotency key | APIs that evolve safely | [API Design Patterns](../l4-senior/api-design-patterns.md) |

## Principles in one line each

| Principle | One line |
|-----------|----------|
| SRP | One reason to change per class |
| OCP | Extend behaviour without editing tested code |
| LSP | Subtypes must keep the parent's promises |
| ISP | Small, client-specific interfaces |
| DIP | Depend on abstractions owned by the policy layer |
| DRY / KISS / YAGNI | Don't duplicate knowledge; keep it simple; don't build for imagined needs |
| Rule of three | Extract a pattern on the third real variation |

See [SOLID Principles](../l1-foundations/solid-principles.md) and [DRY, KISS, YAGNI and Friends](../l1-foundations/other-design-principles.md).

## Command line

| Task | Command |
|------|---------|
| Run a single-file example | `java StrategyDemo.java` |
| Run every example and compare output | `bash examples/run-all.sh` |
| Also run the JUnit tests | `JUNIT_JAR=/path/junit-platform-console-standalone.jar bash examples/run-all.sh` |
| Run a Maven example project | `mvn -q verify` in its folder |
| Serve the playbook locally | `mkdocs serve` |
