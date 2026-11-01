# Proxy

L2 🔵 Practitioner · ⏱ 40 min read · Prerequisites: [Decorator](../l1-foundations/decorator.md), [Dependency Injection](dependency-injection.md)

> 📌 **TL;DR**  
> A Proxy is a stand-in object with the same interface as the real one that **controls access** to it: creating it lazily, checking permissions, caching answers, or forwarding calls over the network. In Java you meet proxies every day: Spring's `@Transactional`, `@Cacheable` and `@PreAuthorize` work by wrapping your beans in generated proxies (JDK dynamic proxies for interfaces, ByteBuddy/CGLIB subclasses for classes). Know their one big trap: calling `this.method()` from inside the object skips the proxy.

## 1. What It Is

**Plain English.** A FreshCart store manager's assistant answers the phone for them. Callers think they're talking to "the manager's line". The assistant decides who gets through (protection), answers common questions without disturbing the manager (caching), and only fetches the manager when really needed (lazy). The caller's experience is the same; access is controlled.

**Definition (GoF).** "Provide a surrogate or placeholder for another object to control access to it." Parts:

- **Subject:** the interface clients use (`Refunds`).
- **Real subject:** the object doing the actual work (`RealRefunds`).
- **Proxy:** implements the same interface, holds or locates the real subject, and adds access control before or after delegating.

**Proxy vs Decorator.** Same structure, different intent. A [Decorator](../l1-foundations/decorator.md) **adds behaviour** and is usually stacked by the client; a Proxy **controls access** and often manages the real object's lifecycle (creates it, locates it remotely). In frameworks the line blurs; the word "proxy" is used for both.

## 2. Why It Exists

Some concerns have nothing to do with what an object does but everything to do with **when, whether and how** it's reached: an expensive object shouldn't be built until needed; a refund API shouldn't be callable by every agent; a slow supplier API shouldn't be hit 1,000 times for the same price; a remote service should look like a local call. Putting these concerns inside the real class mixes responsibilities and repeats them everywhere. A proxy keeps them outside, behind the same interface, so callers don't change.

## 3. Mental Model / Analogy

**Analogy.** A building's reception desk. Visitors ask for a person by name; the receptionist checks ID (protection), tells them "she's in a meeting, here's her standard answer" (caching), or calls her down only when needed (virtual). The visitor never needs to know where her office is (remote).

**Technical model.** A wrapper that implements the subject's interface and holds a reference (or a way to get one) to the real subject. Every call goes `client → proxy → (check / cache / create / serialise) → real subject`. Frameworks generate proxies at runtime, so one piece of code (an `InvocationHandler` or advice) can wrap thousands of methods.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Virtual, protection, remote and caching proxies

**Description.** The four classic kinds. A **virtual proxy** delays creating an expensive object until first use. A **protection proxy** checks permissions before delegating. A **caching proxy** stores results from a slow source. A **remote proxy** (a "stub") looks like a local object but sends each call over the network.

**Advantages / features.** Each kind adds one access concern without touching the real class or its callers.

**ProxyKindsDemo.java**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Four classic proxy kinds. Each implements the SAME interface as the real object and controls access to it. */
public class ProxyKindsDemo {

    // ===================== Virtual proxy: create the expensive object only when first used =====================
    interface Catalog { String find(String sku); }
    static final class FullCatalog implements Catalog {
        FullCatalog() { System.out.println("  (loading 80,000 products into memory...)"); }
        public String find(String sku) { return sku + " = Basmati rice 5kg"; }
    }
    static final class LazyCatalog implements Catalog {
        private Catalog real;
        public String find(String sku) { if (real == null) real = new FullCatalog(); return real.find(sku); }
    }

    // ===================== Protection proxy: check permission before delegating =====================
    interface Refunds { String refund(String orderId, long paise); }
    record RealRefunds() implements Refunds { public String refund(String id, long p) { return "refunded " + p + " on " + id; } }
    record GuardedRefunds(Refunds real, Set<String> roles) implements Refunds {
        public String refund(String id, long p) {
            if (p > 100_000 && !roles.contains("SUPERVISOR")) throw new SecurityException("refunds over Rs 1000 need a supervisor");
            return real.refund(id, p);
        }
    }

    // ===================== Caching proxy: remember answers from a slow source =====================
    interface Prices { long price(String sku); }
    static final class SupplierPrices implements Prices { int calls; public long price(String s) { calls++; return 4_200; } }
    record CachedPrices(Prices real, Map<String, Long> cache) implements Prices {
        public long price(String s) { return cache.computeIfAbsent(s, real::price); }
    }

    // ===================== Remote proxy: looks local, actually sends a message =====================
    interface StockService { int stock(String sku); }
    static final class StockStub implements StockService {
        public int stock(String sku) {
            String request = "GET /stock/" + sku;
            String response = fakeNetwork(request);
            return Integer.parseInt(response);
        }
        static String fakeNetwork(String req) { System.out.println("  -> wire: " + req); return "37"; }
    }

    public static void main(String[] args) {
        Catalog catalog = new LazyCatalog();
        System.out.println("virtual: proxy created, nothing loaded yet");
        System.out.println("virtual: " + catalog.find("R-5KG"));

        Refunds agent = new GuardedRefunds(new RealRefunds(), Set.of("AGENT"));
        System.out.println("protection: " + agent.refund("FC-1", 50_000));
        try { agent.refund("FC-2", 250_000); } catch (SecurityException e) { System.out.println("protection: denied, " + e.getMessage()); }

        SupplierPrices supplier = new SupplierPrices();
        Prices prices = new CachedPrices(supplier, new HashMap<>());
        for (int i = 0; i < 3; i++) prices.price("ATTA-10KG");
        System.out.println("caching: 3 lookups, " + supplier.calls + " supplier call");

        System.out.println("remote: stock = " + new StockStub().stock("MILK-1L"));
    }
}
```

1. Virtual: the real catalogue is built on the first `find`, not when the proxy is created.
2. Protection: the business rule (refunds over Rs 1000 need a supervisor) lives in front of the real service.
3. Caching: three lookups, one call to the supplier.
4. Remote: the caller sees a normal method; the stub turns it into a network request and parses the reply.

**Run it:** `java ProxyKindsDemo.java`

```text
virtual: proxy created, nothing loaded yet
  (loading 80,000 products into memory...)
virtual: R-5KG = Basmati rice 5kg
protection: refunded 50000 on FC-1
protection: denied, refunds over Rs 1000 need a supervisor
caching: 3 lookups, 1 supplier call
  -> wire: GET /stock/MILK-1L
remote: stock = 37
```

| Kind | Controls | Java examples | Watch out for |
|------|----------|---------------|---------------|
| Virtual | When the object is created | Hibernate lazy entities, Spring `@Lazy` | Lazy loading outside a session/transaction |
| Protection | Who may call | Spring Security `@PreAuthorize` | Checks bypassed by self-invocation |
| Caching | Whether the real call happens | Spring `@Cacheable` | Stale data, unbounded caches |
| Remote | Where the object lives | gRPC stubs, Feign clients, RMI | Network calls disguised as cheap local calls |

**Use** these when an access concern applies to many calls. **Avoid** remote proxies that hide latency from callers who need to know (put timeouts and retries in the client interface or its docs).
**Real-world Java:** Hibernate's lazy `@ManyToOne` proxies, Spring Cloud OpenFeign, gRPC generated stubs, `java.rmi`.
**Best practice:** name proxies by what they control (`CachingPrices`, `GuardedRefunds`). **Gotcha:** a virtual proxy that isn't thread-safe can create the real object twice; use a lock or a holder idiom if shared.

### Static proxies

**Description.** A static proxy is a hand-written class that implements the subject interface, holds the real object, and adds its behaviour around the delegated call. It's ordinary Java: the compiler checks it and you can debug it directly.

**Advantages / features.** Explicit, fast, type-checked; good when you have a few interfaces and a specific concern.

**Before (concerns mixed into the client) and after (proxy in front):**

**StaticProxyDemo.java**

```java
/** A static proxy: a hand-written class that implements the interface, adds behaviour, and delegates. */
public class StaticProxyDemo {

    interface SmsGateway { void send(String phone, String text); }

    // ===================== Before: cross-cutting code mixed into the real class =====================
    static final class TextBlastBefore implements SmsGateway {
        public void send(String phone, String text) {
            if (phone.equals("+919800000009")) { System.out.println("  blocked: opted out"); return; }   // compliance rule
            if (text.length() > 160) throw new IllegalArgumentException("too long");                  // validation
            System.out.println("  TextBlast API <- " + phone);                                         // the real work
        }   // switching to another SMS provider means copying the rules above
    }

    // ===================== After: the real class does only the real work =====================
    static final class TextBlastClient implements SmsGateway {
        public void send(String phone, String text) { System.out.println("  TextBlast API <- " + phone + ": " + text); }
    }

    static final class SmsGuardProxy implements SmsGateway {
        private final SmsGateway real; private final java.util.Set<String> optedOut; int blocked;
        SmsGuardProxy(SmsGateway real, java.util.Set<String> optedOut) { this.real = real; this.optedOut = optedOut; }
        public void send(String phone, String text) {
            if (optedOut.contains(phone)) { blocked++; System.out.println("  blocked: " + phone + " opted out"); return; }
            if (text.length() > 160) throw new IllegalArgumentException("SMS longer than 160 chars");
            real.send(phone, text);
        }
    }

    public static void main(String[] args) {
        System.out.println("before:");
        new TextBlastBefore().send("+919800000001", "Your order is on the way");
        System.out.println("after:");
        SmsGateway sms = new SmsGuardProxy(new TextBlastClient(), java.util.Set.of("+919800000009"));
        sms.send("+919800000001", "Your order is on the way");
        sms.send("+919800000009", "Big sale today!");
        System.out.println("caller only knows SmsGateway: " + (sms instanceof SmsGateway));
    }
}
```

1. The real client only talks to TextBlast; it knows nothing about opt-outs.
2. The proxy implements the same interface and wraps any `SmsGateway`, so a second provider gets the same rules.
3. The compliance check (customers who opted out of SMS) blocks the call before the real client is reached.

**Run it:** `java StaticProxyDemo.java`

```text
before:
  TextBlast API <- +919800000001
after:
  TextBlast API <- +919800000001: Your order is on the way
  blocked: +919800000009 opted out
caller only knows SmsGateway: true
```

| Pros | Cons |
|------|------|
| Plain code, compile-time checked | One proxy class per interface |
| Easy to debug and test | Every interface method must be delegated by hand |
| No reflection cost | Interface changes need proxy changes |

**Use** static proxies for one or two interfaces with a specific rule. **Avoid** them for the same concern across dozens of interfaces; that's what dynamic proxies or AOP are for.
**Real-world Java:** `Collections.unmodifiableList` and `Collections.synchronizedList` return hand-written wrappers that control access to the real list.
**Best practice:** keep the proxy's constructor taking the interface, not the concrete class, so proxies can stack. **Gotcha:** forgetting to delegate a newly added interface method (a `default` method especially) silently skips the real object.

### JDK dynamic proxies (java.lang.reflect.Proxy)

**Description.** `Proxy.newProxyInstance` generates a class at runtime that implements the interfaces you list and sends every call to one `InvocationHandler`. One handler (logging, timing, security) works for any interface. It only works with **interfaces**.

**Advantages / features.** One piece of code wraps any number of interfaces; no hand-written delegation; the basis of Spring AOP for interface beans, MyBatis mappers and Spring Data repositories.

**DynamicProxyDemo.java**

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** JDK dynamic proxies: ONE handler adds behaviour to ANY interface, generated at runtime. */
public class DynamicProxyDemo {

    interface OrderApi { String status(String id); String cancel(String id); }
    interface RiderApi { String locate(String riderId); }

    static final List<String> LOG = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static <T> T logged(Class<T> iface, T target) {
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return method.invoke(target, args);
            LOG.add(iface.getSimpleName() + "." + method.getName() + "(" + args[0] + ")");
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, h);
    }

    public static void main(String[] args) {
        OrderApi orders = logged(OrderApi.class, new OrderApi() {
            public String status(String id) { return id + ": out for delivery"; }
            public String cancel(String id) { throw new IllegalStateException(id + " already dispatched"); }
        });
        RiderApi riders = logged(RiderApi.class, id -> id + " at 12.97,77.59");

        System.out.println(orders.status("FC-3001"));
        System.out.println(riders.locate("R-42"));
        try { orders.cancel("FC-3001"); } catch (IllegalStateException e) { System.out.println("caller sees the real exception: " + e.getMessage()); }
        System.out.println("log: " + LOG);
        System.out.println("proxy class: " + orders.getClass().getSimpleName().replaceAll("\\d+", "N") + ", is proxy: " + Proxy.isProxyClass(orders.getClass()));
        try {
            Proxy.newProxyInstance(DynamicProxyDemo.class.getClassLoader(), new Class<?>[] {ArrayList.class}, (p, m, a) -> null);
        } catch (IllegalArgumentException e) { System.out.println("classes can't be proxied: " + e.getMessage()); }
    }
}
```

1. The handler receives the proxy, the `Method` being called and its arguments.
2. `toString`, `equals` and `hashCode` also go through the handler; decide what they should do.
3. `Method.invoke` wraps the target's exception in `InvocationTargetException`; rethrow the cause so callers see the real exception.
4. The JVM generates a class (`$Proxy0`, ...) implementing the listed interfaces.

**Run it:** `java DynamicProxyDemo.java`

```text
FC-3001: out for delivery
R-42 at 12.97,77.59
caller sees the real exception: FC-3001 already dispatched
log: [OrderApi.status(FC-3001), RiderApi.locate(R-42), OrderApi.cancel(FC-3001)]
proxy class: $ProxyN, is proxy: true
classes can't be proxied: java.util.ArrayList is not an interface
```

| Pros | Cons |
|------|------|
| One handler for any interface | Interfaces only |
| No generated source to maintain | Reflection: harder to debug, small per-call cost |
| Built into the JDK | Easy to get exceptions and `Object` methods wrong |

**Use** dynamic proxies for cross-cutting concerns over many interfaces, or to implement an interface from metadata (a repository from method names). **Avoid** them on hot paths where a static proxy is simple enough.
**Real-world Java:** Spring Data's repository interfaces, MyBatis mappers, Retrofit and Feign HTTP clients, Spring AOP for interface-based beans.
**Best practice:** unwrap `InvocationTargetException` and handle `Object` methods explicitly. **Gotcha:** a checked exception the interface doesn't declare, thrown from the handler, reaches the caller as `UndeclaredThrowableException`.

### Byte-code proxies (ByteBuddy, CGLIB)

**Description.** When there's no interface, libraries generate a **subclass** at runtime that overrides each method, adds behaviour, and calls the original. Spring uses this (CGLIB, repackaged) for `@Transactional` and `@Configuration` classes; Hibernate and Mockito use ByteBuddy. Because it's a subclass, `final` classes and `final` or `private` methods can't be intercepted.

**Advantages / features.** Works with plain classes; callers can depend on the concrete type.

This is the shape of what CGLIB or ByteBuddy generates, written by hand:

**SubclassProxyDemo.java**

```java
/**
 * What ByteBuddy and CGLIB generate when there is no interface: a SUBCLASS that overrides methods and adds behaviour.
 * Written by hand here so you can see it. Spring uses this for @Transactional/@Cacheable on classes.
 */
public class SubclassProxyDemo {

    static class InventoryService {
        public String reserve(String sku) { return "reserved " + sku; }
        public final String release(String sku) { return "released " + sku; }
    }

    static class InventoryService$$Proxy extends InventoryService {
        private final InventoryService target;
        InventoryService$$Proxy(InventoryService target) { this.target = target; }
        @Override public String reserve(String sku) {
            System.out.println("  [tx] begin");
            String r = target.reserve(sku);
            System.out.println("  [tx] commit");
            return r;
        }
        // release(...) is final: it can't be overridden, so it runs WITHOUT the transaction
    }

    public static void main(String[] args) {
        InventoryService svc = new InventoryService$$Proxy(new InventoryService());
        System.out.println(svc.reserve("MILK-1L"));
        System.out.println(svc.release("MILK-1L") + "  <- no [tx] lines: final method not intercepted");
        System.out.println("caller sees an InventoryService: " + (svc instanceof InventoryService) + ", class: " + svc.getClass().getSimpleName());
    }
}
```

1. A `final` method can't be overridden, so a subclass proxy can't intercept it.
2. The generated class extends the real class (Spring names them like `InventoryService$$SpringCGLIB$$0`).
3. The final method runs directly on the proxy instance, without the transaction and without the target's state.

**Run it:** `java SubclassProxyDemo.java`

```text
  [tx] begin
  [tx] commit
reserved MILK-1L
released MILK-1L  <- no [tx] lines: final method not intercepted
caller sees an InventoryService: true, class: InventoryService$$Proxy
```

| Aspect | JDK dynamic proxy | Subclass proxy (CGLIB/ByteBuddy) |
|--------|-------------------|----------------------------------|
| Needs an interface | Yes | No |
| Final classes/methods | n/a | Not intercepted |
| Constructor | Not called on target class | May need an accessible constructor (Objenesis avoids calling it) |
| Spring Boot default | Only if configured | Default since Boot 2.0 (`proxyTargetClass=true`) |

**Use** subclass proxies through your framework; you'll rarely generate them yourself. **Avoid** `final` on Spring beans and their public methods that need `@Transactional` or `@Cacheable`.
**Real-world Java:** Spring AOP class proxies, Hibernate lazy entity proxies, Mockito mocks (ByteBuddy), Kotlin's `all-open` compiler plugin exists exactly because Kotlin classes are final by default.
**Best practice:** check `AopUtils.isAopProxy(bean)` in a test when an annotation "doesn't work". **Gotcha:** Java records and `final` classes can't be subclass-proxied; make them interfaces' implementations or don't annotate them.

### Proxy pitfalls: self-invocation

**Description.** A proxy only sees calls that come **through the proxy**. When a method inside the real object calls another method on `this`, the call goes straight to the target, so annotations on the inner method (`@Transactional(REQUIRES_NEW)`, `@Cacheable`, `@Async`, `@PreAuthorize`) are silently ignored.

**Advantages / features (of knowing it).** This is the most common "Spring annotation doesn't work" bug; recognising it saves hours.

**SelfInvocationDemo.java**

```java
import java.lang.reflect.Proxy;

/** The classic proxy trap: a call from inside the object to itself (this.method()) never goes through the proxy. */
public class SelfInvocationDemo {

    interface Orders { void placeAll(String... ids); void place(String id); }

    static final class OrderService implements Orders {
        public void placeAll(String... ids) { for (String id : ids) this.place(id); }
        public void place(String id) { System.out.println("    place " + id); }
    }

    static Orders transactional(Orders target) {
        return (Orders) Proxy.newProxyInstance(Orders.class.getClassLoader(), new Class<?>[] {Orders.class}, (p, m, a) -> {
            System.out.println("  [tx begin] " + m.getName());
            Object r = m.invoke(target, a);
            System.out.println("  [tx commit] " + m.getName());
            return r;
        });
    }

    static final class FixedOrderService implements Orders {
        Orders self;
        public void placeAll(String... ids) { for (String id : ids) self.place(id); }
        public void place(String id) { System.out.println("    place " + id); }
    }

    public static void main(String[] args) {
        System.out.println("trap: one transaction around placeAll, none around each place():");
        transactional(new OrderService()).placeAll("FC-1", "FC-2");

        System.out.println("fix: call through the proxy (better: move place() to another bean):");
        FixedOrderService target = new FixedOrderService();
        Orders proxy = transactional(target);
        target.self = proxy;
        proxy.placeAll("FC-1", "FC-2");
    }
}
```

1. `this.place(id)` calls the target directly; the proxy never sees it.
2. The fix keeps a reference to the proxy (Spring: `@Lazy` self-injection or `AopContext.currentProxy()`).
3. Better fix: move `place()` to a separate bean, so the call naturally crosses a proxy.

**Run it:** `java SelfInvocationDemo.java`

```text
trap: one transaction around placeAll, none around each place():
  [tx begin] placeAll
    place FC-1
    place FC-2
  [tx commit] placeAll
fix: call through the proxy (better: move place() to another bean):
  [tx begin] placeAll
  [tx begin] place
    place FC-1
  [tx commit] place
  [tx begin] place
    place FC-2
  [tx commit] place
  [tx commit] placeAll
```

| Fix | Pros | Cons |
|-----|------|------|
| Move the method to another bean | Clean design, no tricks | One more class |
| Self-injection of the proxy | Small change | Circular reference; confusing |
| `AopContext.currentProxy()` | No new class | Ties code to Spring; needs `exposeProxy=true` |
| AspectJ weaving (compile/load time) | Intercepts self-calls too | Build complexity |

**Use** the "another bean" fix by default. **Avoid** relying on annotations on `private` methods or methods called only internally.
**Real-world Java:** affects Spring `@Transactional`, `@Cacheable`, `@Async`, `@Retryable`, `@PreAuthorize`; not AspectJ-woven code.
**Best practice:** write a test that asserts the inner behaviour happened (a new transaction, a cache hit). **Gotcha:** `@Transactional` on a `private` method is ignored entirely, with no warning at startup.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** the product list shows thumbnails; full-size images should download only when a customer opens a product.

**ProductImageProxyDemo.java**

```java
import java.util.List;

/** Minimal Proxy (virtual): a product list shows 20 products, but full-size images load only when a product is opened. */
public class ProductImageProxyDemo {

    interface Image { String show(); }

    static final class HighResImage implements Image {
        private final String file;
        HighResImage(String file) { this.file = file; System.out.println("  downloading " + file + " (2 MB)"); }
        public String show() { return "showing " + file; }
    }

    static final class ImageProxy implements Image {
        private final String file; private Image real;
        ImageProxy(String file) { this.file = file; }
        public String show() {
            if (real == null) real = new HighResImage(file);
            return real.show();
        }
    }

    public static void main(String[] args) {
        List<Image> images = List.of(new ImageProxy("mango.jpg"), new ImageProxy("apple.jpg"), new ImageProxy("grapes.jpg"));
        System.out.println("list screen ready with " + images.size() + " products, nothing downloaded");
        System.out.println(images.get(0).show());
        System.out.println(images.get(0).show());
    }
}
```

1. The proxy has the same interface as the real image and only remembers the file name.
2. The real image is downloaded once, on the first `show()`.

**Run it:** `java ProductImageProxyDemo.java`

**Output:**

```text
list screen ready with 3 products, nothing downloaded
  downloading mango.jpg (2 MB)
showing mango.jpg
showing mango.jpg
```

**What to notice:**

- Three proxies cost nothing; only the opened product downloaded its image, and only once.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** GreenValley, a produce supplier, exposes a slow, rate-limited price API. Pricing pages ask for the same SKUs constantly. A caching proxy with a 5-minute TTL sits in front of the client; nothing else changes.

**PartnerPriceCacheDemo.java**

```java
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** Realistic Proxy (caching): GreenValley's supplier price API is slow and rate-limited; a caching proxy with a TTL sits in front. */
public class PartnerPriceCacheDemo {

    interface SupplierPrices { long pricePaise(String sku); }

    static final class GreenValleyClient implements SupplierPrices {
        int calls;
        public long pricePaise(String sku) { calls++; return sku.hashCode() % 2 == 0 ? 6_400 : 5_900; }   // pretend: 300 ms HTTP call
    }

    static final class CachingPrices implements SupplierPrices {
        private record Entry(long price, Instant expires) { }
        private final SupplierPrices real; private final Duration ttl; private final Clock clock;
        private final Map<String, Entry> cache = new HashMap<>();
        int hits, misses;
        CachingPrices(SupplierPrices real, Duration ttl, Clock clock) { this.real = real; this.ttl = ttl; this.clock = clock; }

        public synchronized long pricePaise(String sku) {
            Entry e = cache.get(sku);
            if (e != null && clock.instant().isBefore(e.expires())) { hits++; return e.price(); }
            misses++;
            long p = real.pricePaise(sku);
            cache.put(sku, new Entry(p, clock.instant().plus(ttl)));
            return p;
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-02T09:00:00Z");
        public Instant instant() { return now; }
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
    }

    public static void main(String[] args) {
        GreenValleyClient client = new GreenValleyClient();
        MutableClock clock = new MutableClock();
        CachingPrices prices = new CachingPrices(client, Duration.ofMinutes(5), clock);

        for (int i = 0; i < 100; i++) prices.pricePaise(i % 2 == 0 ? "TOMATO-1KG" : "ONION-1KG");
        System.out.println("100 lookups at 09:00 -> supplier calls: " + client.calls + " (hits " + prices.hits + ")");
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        prices.pricePaise("TOMATO-1KG");
        System.out.println("after 6 minutes, TOMATO refreshed -> supplier calls: " + client.calls);
    }
}
```

1. The caching proxy implements `SupplierPrices`, so callers can't tell it apart from the real client.
2. Fresh entries are answered from memory.
3. Misses call the supplier and store the answer with an expiry time from the injected clock.

**Run it:** `java PartnerPriceCacheDemo.java`

**Output:**

```text
100 lookups at 09:00 -> supplier calls: 2 (hits 98)
after 6 minutes, TOMATO refreshed -> supplier calls: 3
```

**What to notice:**

- 100 lookups made only 2 supplier calls; after the TTL, the next lookup refreshed the price.
- An injected `Clock` made expiry testable without sleeping.

### Example 3: Production-grade

**Scenario:** the support back office calls an `OrderAdmin` service. Requirements: methods annotated `@RequiresRole` are checked against the current user; `@Cached` methods are cached per argument; every call is counted with its errors; real exceptions reach callers unchanged; `toString`/`equals` behave sensibly; failed calls are never cached. This is what Spring AOP does with `@PreAuthorize`, `@Cacheable` and `@Timed`.

**ProductionProxyDemo.java**

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Production-grade dynamic proxy: what Spring AOP does for @PreAuthorize, @Cacheable and @Timed, in ~80 lines.
 * - annotations on the INTERFACE drive behaviour (security check, per-argument cache, metrics)
 * - order is fixed: security -> cache -> metrics -> target (a denied call never fills the cache)
 * - real exceptions are rethrown unwrapped; Object methods (toString/equals/hashCode) pass through sensibly
 * - only interfaces are proxied; the factory refuses classes with a clear message
 */
public class ProductionProxyDemo {

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface RequiresRole { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Cached { }

    interface OrderAdmin {
        @Cached String orderSummary(String orderId);
        @RequiresRole("SUPERVISOR") String refund(String orderId, long paise);
        String ping();
    }

    static final class Metrics {
        final Map<String, long[]> calls = new TreeMap<>();                                    // method -> [count, errors]
        synchronized void record(String m, boolean error) { long[] c = calls.computeIfAbsent(m, k -> new long[2]); c[0]++; if (error) c[1]++; }
        public synchronized String toString() { StringBuilder sb = new StringBuilder(); calls.forEach((k, v) -> sb.append(k).append("=").append(v[0]).append(v[1] > 0 ? "(" + v[1] + " err) " : " ")); return sb.toString().trim(); }
    }

    static <T> T enhance(Class<T> iface, T target, Supplier<Set<String>> currentRoles, Metrics metrics) {
        if (!iface.isInterface()) throw new IllegalArgumentException(iface.getSimpleName() + " is not an interface; use composition or a subclass proxy");
        Map<List<Object>, Object> cache = new ConcurrentHashMap<>();
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Proxy[" + iface.getSimpleName() + " -> " + target + "]";
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> method.invoke(target, args);
                };
            }
            RequiresRole role = method.getAnnotation(RequiresRole.class);
            if (role != null && !currentRoles.get().contains(role.value())) {
                metrics.record(method.getName(), true);
                throw new SecurityException(method.getName() + " requires " + role.value());
            }
            List<Object> key = method.isAnnotationPresent(Cached.class) ? List.of(method.getName(), Arrays.asList(args)) : null;
            if (key != null && cache.containsKey(key)) { metrics.record(method.getName() + ".cacheHit", false); return cache.get(key); }
            try {
                Object result = method.invoke(target, args);
                metrics.record(method.getName(), false);
                if (key != null && result != null) cache.put(key, result);
                return result;
            } catch (InvocationTargetException e) {
                metrics.record(method.getName(), true);
                throw e.getCause();
            }
        });
        return iface.cast(proxy);
    }

    static final class RealOrderAdmin implements OrderAdmin {
        int summaryLoads;
        public String orderSummary(String id) { summaryLoads++; if (id.isBlank()) throw new IllegalArgumentException("order id is blank"); return id + ": 4 items, Rs 649"; }
        public String refund(String id, long p) { return "refunded Rs " + p / 100 + " on " + id; }
        public String ping() { return "pong"; }
        public String toString() { return "RealOrderAdmin"; }
    }

    public static void main(String[] args) {
        RealOrderAdmin real = new RealOrderAdmin();
        Metrics metrics = new Metrics();
        var roles = new java.util.concurrent.atomic.AtomicReference<Set<String>>(Set.of("AGENT"));
        OrderAdmin admin = enhance(OrderAdmin.class, real, roles::get, metrics);

        System.out.println(admin.orderSummary("FC-4001"));
        System.out.println(admin.orderSummary("FC-4001"));
        System.out.println("real summary loads: " + real.summaryLoads);
        try { admin.refund("FC-4001", 50_000); } catch (SecurityException e) { System.out.println("denied: " + e.getMessage()); }
        roles.set(Set.of("AGENT", "SUPERVISOR"));
        System.out.println(admin.refund("FC-4001", 50_000));
        try { admin.orderSummary(" "); } catch (IllegalArgumentException e) { System.out.println("real exception type kept: " + e.getMessage()); }
        System.out.println(admin + ", equals itself: " + admin.equals(admin));
        System.out.println("metrics: " + metrics);
        try { enhance(RealOrderAdmin.class, real, Set::of, metrics); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
```

1. JDK proxies need interfaces; fail clearly instead of with a confusing reflection error.
2. `Object` methods get deliberate behaviour (a readable `toString` is gold in logs).
3. Security runs first, so a denied call never reaches the cache or the target.
4. Only successful, non-null results are cached.
5. Unwrap `InvocationTargetException` so callers catch the real exception type.

**Run it:** `java ProductionProxyDemo.java`

**Output:**

```text
FC-4001: 4 items, Rs 649
FC-4001: 4 items, Rs 649
real summary loads: 1
denied: refund requires SUPERVISOR
refunded Rs 500 on FC-4001
real exception type kept: order id is blank
Proxy[OrderAdmin -> RealOrderAdmin], equals itself: true
metrics: orderSummary=2(1 err) orderSummary.cacheHit=1 refund=2(1 err)
RealOrderAdmin is not an interface; use composition or a subclass proxy
```

**What to notice:**

- The second summary came from the cache (`real summary loads: 1`).
- The refund was denied for an agent and allowed for a supervisor, through the same proxy.
- The blank order ID threw `IllegalArgumentException`, not `InvocationTargetException`, and was counted as an error.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Separation | Access concerns outside business code | Behaviour hidden from readers of the class |
| Reuse | One handler for many interfaces | Generated classes are hard to debug |
| Performance | Lazy creation, caching | Extra call layer; reflection on dynamic proxies |
| Security | Central permission checks | Bypassed by self-invocation and `new` |
| Flexibility | Swap remote/local transparently | Remote calls look cheap but aren't |
| Testability | Real subject tested alone | Proxy behaviour needs separate tests |

## 8. When to Use / When NOT to Use

**Use it when:**

- creating an object is expensive and often unnecessary (virtual);
- calls need permission checks, rate limits or audit (protection);
- results from a slow source can be reused (caching);
- a remote service should be called through a typed interface (remote).

**Don't use it when:**

- the "concern" is real business logic (put it in the service);
- callers need to know about latency, retries or failure modes that a proxy would hide;
- a simple method call would do; proxies add indirection.

## 9. Best Practices

1. **Program to interfaces** so proxies (static or JDK) can be inserted. *(Consensus.)*
2. **Unwrap `InvocationTargetException`** and handle `Object` methods in handlers. *(Consensus.)*
3. **Split methods into separate beans** when one needs different proxy behaviour from its caller. *(Consensus; Spring docs.)*
4. **Don't make proxied Spring beans or their advised methods `final` or `private`.** *(Consensus.)*
5. **Bound caching proxies** (size, TTL) and never cache failures. *(Consensus.)*
6. **Fix the order** of stacked concerns (security before cache before metrics) and test it. *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Missing transaction from self-invocation (production failure mode)**  
> **Symptom:** after a refactor, a nightly job that placed 2,000 replenishment orders rolled all of them back when order 1,837 failed, instead of keeping the 1,836 successful ones.
> **Root cause:** `placeAll()` called `this.place(id)`, which was annotated `@Transactional(propagation = REQUIRES_NEW)`; the call skipped the proxy, so everything ran in one outer transaction.
> **Fix:** move `place()` into a separate `ReplenishmentOrderPlacer` bean; add a test that a failure keeps earlier orders.

> 🚨 **Lazy proxy outside its session**  
> **Symptom:** `LazyInitializationException: could not initialize proxy - no Session` when rendering an order's customer.
> **Root cause:** a Hibernate virtual proxy was touched after the transaction closed.
> **Fix:** fetch what the view needs in the query (`JOIN FETCH` or a DTO projection); don't enable open-session-in-view as a cure.

> 🚨 **Proxy hiding a network call**  
> **Symptom:** a loop over 500 products took 40 seconds.
> **Root cause:** `product.getStock()` was a remote proxy making an HTTP call per item.
> **Fix:** expose a batch method (`stockFor(List<String> skus)`) and make remote interfaces look remote.

> 🚨 **Caching errors**  
> **Symptom:** a supplier outage of 30 seconds showed "price unavailable" for 5 minutes.
> **Root cause:** the caching proxy stored the error result like a normal value.
> **Fix:** cache only successful results; use short negative-cache TTLs deliberately if at all.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `spring.aop.proxy-target-class` | `true` (Boot 2.0+) | Subclass (CGLIB) proxies instead of JDK proxies | `false` to force interface proxies | Beans injected by class fail to inject |
| `@EnableAspectJAutoProxy(exposeProxy = true)` | `false` | Allows `AopContext.currentProxy()` | Legacy self-invocation fixes | Couples code to Spring internals |
| `@EnableTransactionManagement(mode = ASPECTJ)` | `PROXY` | Weave instead of proxy (self-calls work) | Many self-invocation cases | Build-time weaving complexity |
| Cache TTL / max size | library-specific | Freshness and memory of caching proxies | Per data volatility | Stale data or memory growth |
| `jdk.proxy.ProxyGenerator.saveGeneratedFiles` | `false` | Dumps generated proxy classes | Debugging proxy behaviour | Disk clutter |

## 12. Security Considerations

- A protection proxy is bypassed by anything that doesn't go through it: self-invocation, `new` on the real class, or a second bean of the real type. Enforce critical checks at more than one layer (for example in the service and at the API gateway).
- Keep the real subject's constructor package-private if only the proxy factory should create it.
- Caching proxies must include the user or tenant in the cache key when results differ per user; otherwise one customer can see another's data.
- Dynamic proxies that dispatch by method name must not let input choose the method (`getMethod(userInput)`).

## 13. Performance & Scalability

A static proxy is a normal virtual call; the JIT usually inlines it. JDK dynamic proxies add reflection (`Method.invoke`), which modern JVMs optimise well (since Java 18, core reflection uses method handles, JEP 416), but it's still slower than a direct call; it matters only on very hot paths. The big performance effects of proxies are the ones they're built for: virtual proxies avoid work, caching proxies avoid calls, and remote proxies can turn a loop into hundreds of network round-trips if the interface isn't designed for batching.

## 14. Testing It

Test proxy behaviour separately from the real subject: caching per argument, denial, unwrapped exceptions, no caching of failures:

**ProductionProxyTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductionProxyTest {

    private final ProductionProxyDemo.RealOrderAdmin real = new ProductionProxyDemo.RealOrderAdmin();
    private final ProductionProxyDemo.Metrics metrics = new ProductionProxyDemo.Metrics();

    private ProductionProxyDemo.OrderAdmin proxy(String... roles) {
        return ProductionProxyDemo.enhance(ProductionProxyDemo.OrderAdmin.class, real, () -> Set.of(roles), metrics);
    }

    @Test
    void cachesPerArgument() {
        var admin = proxy();
        admin.orderSummary("A");
        admin.orderSummary("A");
        admin.orderSummary("B");
        assertEquals(2, real.summaryLoads);
    }

    @Test
    void deniesWithoutRoleAndAllowsWithIt() {
        assertThrows(SecurityException.class, () -> proxy("AGENT").refund("A", 100));
        assertTrue(proxy("SUPERVISOR").refund("A", 10_000).startsWith("refunded"));
    }

    @Test
    void rethrowsTheTargetsOwnException() {
        assertThrows(IllegalArgumentException.class, () -> proxy().orderSummary(" "));
        assertTrue(metrics.toString().contains("err"));
    }

    @Test
    void failedCallsAreNotCached() {
        var admin = proxy();
        assertThrows(IllegalArgumentException.class, () -> admin.orderSummary(" "));
        assertThrows(IllegalArgumentException.class, () -> admin.orderSummary(" "));
        assertEquals(2, real.summaryLoads);
    }
}
```

In Spring, add one integration test per important annotation (a rollback test for `@Transactional`, a hit test for `@Cacheable`), because those break silently.

## 15. Observability & Operations

Proxies are where cross-cutting metrics live: Micrometer's `@Timed` and `@Counted`, cache hit ratios from Caffeine or Spring Cache, and denied-call counters from security checks. Make proxies' `toString` show the target (`Proxy[OrderAdmin -> RealOrderAdmin]`) so logs are readable. When debugging, stack traces that show `$Proxy`, `$$SpringCGLIB$$` or `CglibAopProxy` frames tell you a proxy was involved; their absence on a call you expected to be advised is the self-invocation clue.

## 16. Version Notes

| Version | Change relevant to Proxy |
|---------|--------------------------|
| Java 1.3 | `java.lang.reflect.Proxy` and `InvocationHandler` |
| Java 16 | `InvocationHandler.invokeDefault` for default methods |
| Java 18 | JEP 416: core reflection reimplemented with method handles (faster `Method.invoke`) |
| Spring Boot 2.0 | Class-based (CGLIB) proxies by default |
| Spring 6 | CGLIB proxies named `$$SpringCGLIB$$`; AOT generates proxies at build time for native images |

## 17. Expert Deep Dive

- **Proxies are how frameworks add behaviour without your code knowing.** Spring AOP, Hibernate lazy loading, Mockito mocks, Feign clients and Spring Data repositories are all proxies. Understanding proxy mechanics explains most "framework magic" bugs.
- **Proxy vs weaving.** Proxies intercept only external calls on public, non-final methods of container-managed objects. AspectJ weaving changes the bytecode itself, so self-calls, private methods and `new` objects are covered, at the cost of build complexity. Choose weaving only when proxy limits genuinely block you.
- **Remote proxies and the fallacies of distributed computing.** RMI and CORBA tried to make remote calls look local; it failed because latency, partial failure and versioning can't be hidden. Modern clients (gRPC, Feign) still generate proxies but expose deadlines, retries and errors explicitly; see [Resilience Patterns](../l4-senior/resilience-patterns.md).
- **Native images.** GraalVM can't generate classes at runtime; Spring AOT and Quarkus generate proxies at build time, and JDK dynamic proxies need configuration hints.

> 📖 **War story: the cache that leaked between stores**  
> A grocery app added `@Cacheable("deliverySlots")` to `slotsFor(LocalDate day)`. Slots differ per store, but the store came from a request-scoped bean inside the method, not from a parameter. The cache key was only the date, so the first store to ask on a given day filled the cache for everyone, and customers across the city saw another store's slots. Orders were placed for slots that didn't exist. **Fix:** the store ID became a method parameter (and therefore part of the cache key), a test checked two stores on the same day, and the team added a review rule: any `@Cacheable` method must be a pure function of its parameters. **Lesson:** a caching proxy only knows what's in the arguments.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is a Proxy?** A **stand-in with the same interface** that controls access to the real object.
2. **Name four kinds.** **Virtual, protection, caching, remote.**
3. **Proxy vs Decorator?** Same structure; Proxy **controls access**, Decorator **adds behaviour**.
4. **Give a JDK example.** **`java.lang.reflect.Proxy`**; also `Collections.unmodifiableList`.
5. **Give a Spring example.** **`@Transactional`** works through a proxy around your bean.

**▸ 🟡 L3**

1. **JDK dynamic proxy vs CGLIB?** JDK needs **interfaces**; CGLIB/ByteBuddy **subclass** classes and can't intercept `final` methods.
2. **Why doesn't `@Transactional` work on an internal call?** **Self-invocation** bypasses the proxy.
3. **Why rethrow `getCause()` in an `InvocationHandler`?** `Method.invoke` wraps exceptions in **`InvocationTargetException`**.
4. **What causes `LazyInitializationException`?** A Hibernate **lazy proxy** accessed **after the session closed**.
5. **How do you keep a caching proxy correct per user?** Put the **user or tenant in the key** and keep cached methods pure.

**▸ 🔴 L4–L5**

1. **Proxy-based AOP vs AspectJ weaving: trade-offs?** Proxies are simple but miss **self-calls, private, final, `new`**; weaving covers them with **build complexity**.
2. **Design a client for a remote pricing service.** Typed interface, **batch methods, deadlines, retries with budgets, circuit breaker**, cache with TTL.
3. **How do proxies affect GraalVM native images?** No runtime class generation: **AOT-generated proxies** or reflection/proxy hints.
4. **A security annotation was bypassed in production. How?** **Self-invocation, direct `new`, a non-proxied bean**, or an internal endpoint; defence in depth.
5. **When would you hand-write a proxy instead of using AOP?** When the concern is **specific, performance-sensitive**, or must be visible and explicit in code review.

**▸ 💻 Hands-on**

**Task:** Make `PartnerPriceCacheDemo`'s proxy evict the oldest entry when it holds more than 1,000 SKUs.

> ✅ **Solution**  
> ```java
> private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true) {
>     @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
>         return size() > 1_000;              // LRU eviction
>     }
> };
> ```

## 19. Practice

- [ ] Add a `@RateLimited(perMinute = 10)` annotation to `ProductionProxyDemo` and enforce it in the handler.
    > 💡 **Hint**  
    > Keep a counter and a window start per method name; use an injected clock.

- [ ] Make `LazyCatalog` in `ProxyKindsDemo` thread-safe.
    > 💡 **Hint**  
    > Double-checked locking with a `volatile` field, or a holder class.

- [ ] Find a self-invocation bug in a Spring codebase: search for `this.` calls to methods with `@Transactional` or `@Cacheable`.
    > 💡 **Hint**  
    > Also check calls without `this.`; they're the same.

- [ ] Write a dynamic proxy that implements a `SkuLookup` interface from a `Map`, with no implementing class.
    > 💡 **Hint**  
    > The handler reads `args[0]` and returns `map.get(args[0])`.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: a stand-in with the same interface that controls access (lazy, permission, cache, remote).
> - Static proxy: hand-written; JDK dynamic proxy: interfaces only; CGLIB/ByteBuddy: subclasses, not `final`.
> - Spring's `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize` are proxies.
> - Self-invocation skips the proxy: move the method to another bean.
> - In handlers: unwrap `InvocationTargetException`, handle `Object` methods, never cache failures.
>
> ```java
> T proxy = (T) Proxy.newProxyInstance(loader, new Class<?>[] {iface}, (p, m, args) -> {
>     // before
>     try { return m.invoke(target, args); }
>     catch (InvocationTargetException e) { throw e.getCause(); }
> });
> ```

## 21. Further Reading

- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994), "Proxy".
- [java.lang.reflect.Proxy API documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Proxy.html)
- [Spring Framework reference: Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [ByteBuddy tutorial](https://bytebuddy.net/#/tutorial)
- [JEP 416: Reimplement Core Reflection with Method Handles](https://openjdk.org/jeps/416)

---

[← Previous: Composite](composite.md) · [Next: Bridge →](bridge.md)
