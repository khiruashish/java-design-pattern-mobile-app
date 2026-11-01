# JVM Internals Behind Patterns

L5 🔴 Staff / Principal · ⏱ 40 min read · Prerequisites: [Singleton](../l1-foundations/singleton.md), [Proxy](../l2-practitioner/proxy.md), [Concurrency Patterns](../l3-professional/concurrency-patterns.md)

> 📌 **TL;DR**  
> Many patterns rely on JVM guarantees you rarely see. **Class initialisation** runs once per class loader under a lock, which makes the holder idiom and enum singletons safe and lazy, and also means two class loaders mean two singletons. **Dynamic proxies** and bytecode generation (CGLIB, ByteBuddy) power Spring AOP, transactions and mocks, with limits: no self-invocation interception and no final methods. **invokedynamic** with `LambdaMetafactory` turns lambdas into hidden classes at first use, caching non-capturing ones. **MethodHandles** and **VarHandles** are the fast, typed successors to reflection and `Unsafe`. The **Java Memory Model** defines when writes become visible across threads; safe publication (final fields, volatile, locks, static init, concurrent collections) is what makes immutable objects and lazy singletons correct.

## 1. What It Is

**Plain English.** When you write a Singleton, a Proxy, a lambda-based Strategy or an immutable value object, the JVM does a lot of work to make it correct and fast. This page opens that box: how classes get loaded and initialised, how proxies are generated, how lambdas are linked, how reflective access works, and what the memory model promises between threads.

**Definition.**

| Mechanism | Patterns that depend on it |
|-----------|----------------------------|
| Class loading and initialisation | Singleton (holder, enum), Registry, plugin systems |
| Dynamic proxies and bytecode generation | Proxy, Decorator, AOP, mocks, ORMs (lazy loading) |
| invokedynamic and LambdaMetafactory | Strategy/Command as lambdas, Stream pipelines |
| Reflection, MethodHandles, VarHandles | DI containers, serializers, atomics, frameworks |
| Java Memory Model, safe publication | Immutable objects, lazy init, Observer across threads |

## 2. Why It Exists

At staff level you debug the cases where patterns break: a "singleton" created twice in an app server, a `@Transactional` method that silently isn't transactional, a cache that publishes half-built objects, a lambda allocation in a hot loop, a framework that fails on Java 17 because of strong encapsulation. Understanding the mechanism turns these from mysteries into predictable consequences, and lets you design libraries that work with the JVM rather than against it.

## 3. Mental Model / Analogy

**Analogy.** A theatre. Class loaders are separate stages; an actor (class) on one stage is a different actor from the same-named one on another. A proxy is an understudy who stands in for the actor and can do extra things before and after each line, but if the actor talks to themselves, the understudy isn't involved. invokedynamic is a script line that says "the director will decide how to perform this on opening night", and the decision is then fixed for every performance. The memory model is the rule about when the audience in the back row is guaranteed to see what happened on stage.

**Technical model.** The JVM links symbolic references lazily, initialises classes on first active use, generates classes at runtime (proxies, lambda forms, hidden classes), and executes under a memory model defined by happens-before relationships.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Class loading and singleton guarantees

**Description.** A class is initialised on its first active use (static method call, static field access, instantiation), exactly once per class loader, and the JVM holds an initialisation lock while it runs (JLS 12.4.2). The **holder idiom** puts the instance in a nested class, so it's created only when `get()` first touches the holder. Enum singletons rely on the same mechanism. A class's identity is its name **plus** its defining class loader.

**Advantages / features.** Lazy, thread-safe initialisation without explicit locking; no synchronisation cost after initialisation.

**ClassLoadingSingletonDemo.java**

```java
import java.io.IOException;
import java.io.InputStream;

/**
 * Class loading and singleton guarantees: a class is initialised lazily, exactly once per class loader, under a
 * JVM lock (JLS 12.4.2). The holder idiom uses that for free lazy, thread-safe singletons. But "once" is per
 * class loader: two loaders give two "singletons".
 */
public class ClassLoadingSingletonDemo {

    static final StringBuilder events = new StringBuilder();

    static final class PriceTable {
        static { events.append("[PriceTable initialised]"); }
        private PriceTable() { events.append("[instance created]"); }
        private static final class Holder { static final PriceTable INSTANCE = new PriceTable(); }
        static PriceTable get() { return Holder.INSTANCE; }
        static String version() { return "v1"; }
    }

    public static void main(String[] args) throws Exception {
        events.append("[main started]");
        System.out.println("version: " + PriceTable.version());
        events.append("[before get]");
        PriceTable a = PriceTable.get();
        PriceTable b = PriceTable.get();
        System.out.println("events: " + events);
        System.out.println("same instance in one loader: " + (a == b));

        ClassLoader isolated = new ClassLoader(null) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                String path = name.replace('.', '/') + ".class";
                try (InputStream in = ClassLoadingSingletonDemo.class.getClassLoader().getResourceAsStream(path)) {
                    if (in == null) throw new ClassNotFoundException(name);
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) { throw new ClassNotFoundException(name, e); }
            }
        };
        Class<?> other = isolated.loadClass(PriceTable.class.getName());
        System.out.println("same class name, same Class object? " + (other == PriceTable.class)
                + " (loaders: app vs " + (other.getClassLoader() == isolated ? "isolated" : "?") + ")");
    }
}
```

1. The holder class is initialised only when `get()` first reads `Holder.INSTANCE`.
2. Calling another static method initialised `PriceTable`, but the instance was created only at `get()`.
3. A second class loader defines a separate `PriceTable` class, with its own statics and its own "singleton".

**Run it:** `java ClassLoadingSingletonDemo.java`

```text
version: v1
events: [main started][PriceTable initialised][before get][instance created]
same instance in one loader: true
same class name, same Class object? false (loaders: app vs isolated)
```

| Trigger | Initialises the class? |
|---------|------------------------|
| `new`, static method call, non-constant static field | Yes |
| Reading a compile-time constant (`static final int X = 5`) | No (inlined) |
| `Class.forName(name)` | Yes (by default) |
| `ClassLoader.loadClass(name)` | No (loads only) |
| Subclass initialisation | Superclass first |

**Use** the holder idiom or an enum for lazy singletons. **Avoid** relying on singletons across class loaders (app servers, plugins, hot reload).
**Real-world Java:** servlet containers and OSGi give each app or bundle its own loader; Spring Boot devtools uses a restart class loader.
**Best practice:** keep static initialisers small and side-effect free. **Gotcha:** an exception in a static initialiser leaves the class unusable (`NoClassDefFoundError` on every later use).

### Dynamic proxies and bytecode generation

**Description.** `java.lang.reflect.Proxy` generates, at runtime, a class implementing given interfaces whose methods all delegate to an `InvocationHandler`. Class-based proxies (CGLIB, ByteBuddy) generate a **subclass** and override methods. Spring uses these for `@Transactional`, `@Cacheable`, `@Async` and security; Hibernate for lazy loading; Mockito for mocks.

**Advantages / features.** Cross-cutting behaviour without changing classes; one handler for many methods and interfaces.

**DynamicProxyDemo.java**

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic proxies: the JDK generates a class at runtime implementing given interfaces and routes every call to an
 * InvocationHandler. This is how Spring AOP (interface-based), Mockito-style mocks and many decorators work.
 * Class-based proxies (CGLIB, ByteBuddy) subclass instead, which is why final classes and methods can't be proxied.
 */
public class DynamicProxyDemo {

    interface PricingService { long price(String sku); }
    static final class RealPricing implements PricingService { public long price(String sku) { return sku.startsWith("MILK") ? 6800 : 4500; } }

    @SuppressWarnings("unchecked")
    static <T> T timed(T target, Class<T> type, List<String> log) {
        InvocationHandler handler = (proxy, method, args) -> {
            log.add("before " + method.getName() + "(" + args[0] + ")");
            Object result = method.invoke(target, args);
            log.add("after " + method.getName() + " -> " + result);
            return result;
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        PricingService pricing = timed(new RealPricing(), PricingService.class, log);
        System.out.println("price: " + pricing.price("MILK-1L"));
        System.out.println("log: " + log);
        System.out.println("proxy class is generated: " + Proxy.isProxyClass(pricing.getClass())
                + ", implements PricingService: " + (pricing instanceof PricingService)
                + ", is a RealPricing: " + (pricing instanceof RealPricing));
    }
}
```

1. The handler sees every call: method, arguments, and decides what to do around it.
2. The JDK generates the proxy class at runtime for the requested interfaces.
3. The proxy implements the interface but isn't an instance of the target class.

**Run it:** `java DynamicProxyDemo.java`

```text
price: 6800
log: [before price(MILK-1L), after price -> 6800]
proxy class is generated: true, implements PricingService: true, is a RealPricing: false
```

| | JDK dynamic proxy | Subclass proxy (CGLIB/ByteBuddy) |
|-|-------------------|----------------------------------|
| Requires | An interface | Non-final class, non-final methods |
| Type | Implements interfaces only | `instanceof` target class |
| Spring default | When `proxyTargetClass=false` and interfaces exist | Spring Boot default (`proxyTargetClass=true`) |
| Self-invocation | Not intercepted | Not intercepted |
| Final / private methods | n/a | Not intercepted |

**Use** proxies for cross-cutting concerns with clear boundaries. **Avoid** depending on interception for calls inside the same object.
**Real-world Java:** Spring AOP, Spring Data repository interfaces, Retrofit and Feign clients, Mockito (ByteBuddy).
**Best practice:** put intercepted methods on a separate bean when self-invocation would be needed. **Gotcha:** `@Transactional` on a private method, or called from the same class, silently does nothing.

### invokedynamic and lambda metafactory

**Description.** For a lambda, javac compiles the body into a private method and emits an `invokedynamic` instruction. On first execution, the bootstrap method `LambdaMetafactory.metafactory` generates a **hidden class** implementing the functional interface and links the call site permanently. Non-capturing lambdas typically link to a constant (one shared instance); capturing lambdas create a new instance with the captured values each time. String concatenation (`StringConcatFactory`, Java 9+), records' `toString/equals/hashCode` (`ObjectMethods`) and pattern-matching `switch` (`SwitchBootstraps`, Java 21) also use invokedynamic.

**Advantages / features.** No class file per lambda; linking strategy can improve in new JDKs without recompiling; non-capturing lambdas are effectively free.

**LambdaMetafactoryDemo.java**

```java
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * invokedynamic and the lambda metafactory: a lambda isn't an anonymous inner class file. javac emits an
 * invokedynamic instruction; on first execution LambdaMetafactory spins a hidden class implementing the functional
 * interface. Non-capturing lambdas are typically cached as a single instance; capturing ones allocate per evaluation.
 */
public class LambdaMetafactoryDemo {

    static Supplier<String> nonCapturing() { return () -> "FreshCart"; }
    static Function<Long, Long> capturing(long percent) { return price -> price * (100 - percent) / 100; }

    public static void main(String[] args) {
        Supplier<String> a = nonCapturing(), b = nonCapturing();
        Function<Long, Long> c = capturing(10), d = capturing(10);
        System.out.println("non-capturing lambda reused: " + (a == b));
        System.out.println("capturing lambda reused: " + (c == d));
        Class<?> k = a.getClass();
        System.out.println("hidden class: " + k.isHidden() + ", synthetic: " + k.isSynthetic()
                + ", anonymous inner class: " + k.isAnonymousClass());
        System.out.println("10% off Rs 136: Rs " + c.apply(13_600L) / 100);
    }
}
```

1. A non-capturing lambda: the same instance is returned every time.
2. A capturing lambda: each evaluation creates an instance holding the captured `percent`.
3. The implementation is a hidden class, not an anonymous inner class.

**Run it:** `java LambdaMetafactoryDemo.java`

```text
non-capturing lambda reused: true
capturing lambda reused: false
hidden class: true, synthetic: true, anonymous inner class: false
10% off Rs 136: Rs 122
```

| Construct | Bootstrap |
|-----------|-----------|
| Lambda, method reference | `LambdaMetafactory` |
| `"a" + b` (Java 9+) | `StringConcatFactory` |
| Record `equals/hashCode/toString` | `ObjectMethods` |
| Pattern `switch` on types (Java 21) | `SwitchBootstraps` |
| Dynamic languages on the JVM | Their own bootstraps |

**Use** lambdas freely for Strategy and Command; non-capturing ones cost nothing after linking. **Avoid** relying on lambda identity (`==`) or on capturing-lambda allocation in extremely hot loops without measuring (escape analysis often removes it).
**Real-world Java:** every `Stream` pipeline, `Comparator.comparing`, Spring's functional bean registration.
**Best practice:** prefer method references and non-capturing lambdas for constants (`static final Comparator`). **Gotcha:** the first call of each lambda call site pays a linking cost; it shows up in startup time, which is why CDS and AOT caches (Java 24+) help.

### Reflection, MethodHandles and VarHandles

**Description.** **Reflection** (`Method.invoke`) looks up members by name and checks access per call (since JDK 18 it's implemented on method handles internally). A **MethodHandle** is a typed reference to a method, constructor or field, access-checked once at lookup; `invokeExact` calls with an exact signature and can be inlined by the JIT when the handle is constant. A **VarHandle** (Java 9) gives field and array access with explicit memory ordering: plain, opaque, acquire/release, volatile, compare-and-set; it replaced most uses of `sun.misc.Unsafe`.

**Advantages / features.** Frameworks get fast dynamic invocation; atomics without `Unsafe`; works with strong encapsulation via `MethodHandles.privateLookupIn` when modules allow it.

**MethodHandlesDemo.java**

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reflection, MethodHandles and VarHandles: reflection looks members up by name and checks access on every call;
 * a MethodHandle is a typed, directly executable reference checked once at lookup, which the JIT can inline;
 * a VarHandle gives atomic and memory-ordered access to fields (what AtomicXxx classes use internally).
 */
public class MethodHandlesDemo {

    static final class Stock {
        volatile int units = 10;
        int reserve(int n) { units -= n; return units; }
    }

    static final VarHandle UNITS;
    static { try { UNITS = MethodHandles.lookup().findVarHandle(Stock.class, "units", int.class); } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); } }

    public static void main(String[] args) throws Throwable {
        Stock s = new Stock();
        Method reflective = Stock.class.getDeclaredMethod("reserve", int.class);
        System.out.println("reflection: " + reflective.invoke(s, 2));

        MethodHandle handle = MethodHandles.lookup().findVirtual(Stock.class, "reserve", MethodType.methodType(int.class, int.class));
        int left = (int) handle.invokeExact(s, 3);
        System.out.println("method handle: " + left + ", type " + handle.type());

        List<Thread> threads = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();
        Stock shelf = new Stock();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> {
                int current;
                do {
                    current = (int) UNITS.getVolatile(shelf);
                    if (current == 0) return;                                                       // sold out
                } while (!UNITS.compareAndSet(shelf, current, current - 1));
                succeeded.incrementAndGet();
            });
            threads.add(t); t.start();
        }
        for (Thread t : threads) t.join();
        System.out.println("20 buyers, 10 units: " + succeeded.get() + " reservations, " + shelf.units + " left (never negative)");
    }
}
```

1. Reflection: look up by name, invoke with boxing and access checks.
2. A method handle: exact type `(Stock,int)int`, checked once at lookup.
3. A VarHandle compare-and-set loop: 20 concurrent buyers never oversell 10 units.

**Run it:** `java MethodHandlesDemo.java`

```text
reflection: 8
method handle: 5, type (Stock,int)int
20 buyers, 10 units: 10 reservations, 0 left (never negative)
```

| API | Strength | Cost |
|-----|----------|------|
| Reflection | Simple, discoverable | Boxing, per-call checks (less since JDK 18) |
| MethodHandle | Typed, inlinable when constant | More ceremony |
| VarHandle | Memory-ordering modes, CAS | Low-level; easy to misuse |
| Generated code (annotation processors) | Fastest, no reflection | Build-time complexity |

**Use** MethodHandles in frameworks and libraries; VarHandles for lock-free structures. **Avoid** reflection in hot paths, and `setAccessible(true)` on JDK internals (blocked since Java 16/17).
**Real-world Java:** Jackson and Hibernate use method handles or generated accessors; `AtomicInteger`, `ConcurrentHashMap` and `ForkJoinPool` use VarHandles or `Unsafe` internally.
**Best practice:** store method handles in `static final` fields so the JIT treats them as constants. **Gotcha:** `invokeExact` requires the exact static type at the call site, including the return cast; mismatches throw `WrongMethodTypeException`.

### Java Memory Model and safe publication

**Description.** The JMM defines when a write by one thread is guaranteed visible to a read in another: only when a **happens-before** relationship connects them. Without one, another thread can see a non-null reference to an object whose fields still look unset. **Safe publication** idioms: initialise from a static initialiser; store into a `volatile` field or `AtomicReference`; store under a lock that readers also take; put it into a concurrent collection; or make the object **immutable with final fields** (JLS 17.5 guarantees their values are visible once the constructor completes, if `this` didn't escape).

**Advantages / features.** Correct lock-free reads of shared configuration and caches; immutable objects can be shared freely.

**SafePublicationDemo.java**

```java
import java.util.List;
import java.util.Map;

/**
 * Java Memory Model and safe publication: without a happens-before edge, another thread may see a reference to an
 * object before seeing its fields' values. Final fields, volatile, locks, static initialisers and concurrent
 * collections create those edges. Double-checked locking is only correct with volatile.
 */
public class SafePublicationDemo {

    /** Immutable: final fields are guaranteed visible once the constructor finishes (JLS 17.5). */
    record PriceList(Map<String, Long> prices) {
        PriceList { prices = Map.copyOf(prices); }
    }

    static final class PriceListHolder {
        private volatile PriceList current;
        private final Object lock = new Object();
        PriceList get() {
            PriceList p = current;                       // one volatile read on the fast path
            if (p == null) {
                synchronized (lock) {
                    p = current;
                    if (p == null) current = p = new PriceList(Map.of("MILK-1L", 6800L));
                }
            }
            return p;
        }
        void replace(PriceList next) { current = next; }  // readers see either the old or the new list, never a half-built one
    }

    public static void main(String[] args) throws InterruptedException {
        PriceListHolder holder = new PriceListHolder();
        long[] seen = new long[4];
        List<Thread> readers = List.of(0, 1, 2, 3).stream().map(i -> new Thread(() -> seen[i] = holder.get().prices().get("MILK-1L"))).toList();
        readers.forEach(Thread::start);
        for (Thread t : readers) t.join();
        System.out.println("4 threads read: " + java.util.Arrays.toString(seen) + ", same instance: " + (holder.get() == holder.get()));
        holder.replace(new PriceList(Map.of("MILK-1L", 7000L)));
        System.out.println("after replace: " + holder.get().prices().get("MILK-1L"));
        System.out.println("rules: final fields, volatile, synchronized, static init, java.util.concurrent all publish safely");
    }
}
```

1. An immutable value with final fields and a defensive copy: safe to share once constructed.
2. `volatile` makes publishing the reference a happens-before edge for readers.
3. Double-checked locking is correct only because the field is `volatile`.

**Run it:** `java SafePublicationDemo.java`

```text
4 threads read: [6800, 6800, 6800, 6800], same instance: true
after replace: 7000
rules: final fields, volatile, synchronized, static init, java.util.concurrent all publish safely
```

| Happens-before source | Example |
|-----------------------|---------|
| Program order within a thread | Statements in sequence |
| Monitor unlock → later lock | `synchronized` blocks |
| Volatile write → later read | `volatile` fields, `AtomicReference` |
| Thread start / join | `t.start()`, `t.join()` |
| Class initialisation | Static initialisers, holder idiom |
| Final field freeze | Immutable objects |

**Use** immutable snapshots published via volatile or atomics for shared, read-mostly state. **Avoid** double-checked locking without volatile, and leaking `this` from constructors (listener registration in constructors).
**Real-world Java:** `String`, records and `Map.copyOf` rely on final-field semantics; Spring singletons are published safely by the container's locking during startup.
**Best practice:** make shared objects immutable; publish new versions by replacing the reference. **Gotcha:** "it works on my machine" means little: x86 hides many reorderings that ARM (Graviton, Apple silicon) exposes.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's registry must exist exactly once, even if some code tries reflection.

**EnumSingletonDemo.java**

```java
import java.lang.reflect.Constructor;

/** Minimal: an enum singleton can't be duplicated by reflection; a private-constructor singleton can. */
public class EnumSingletonDemo {

    static final class ClassicRegistry {
        static final ClassicRegistry INSTANCE = new ClassicRegistry();
        private ClassicRegistry() {}
    }

    enum EnumRegistry { INSTANCE }

    public static void main(String[] args) throws Exception {
        Constructor<ClassicRegistry> c = ClassicRegistry.class.getDeclaredConstructor();
        c.setAccessible(true);
        System.out.println("classic: second instance created = " + (c.newInstance() != ClassicRegistry.INSTANCE));
        try {
            Constructor<EnumRegistry> e = EnumRegistry.class.getDeclaredConstructor(String.class, int.class);
            e.setAccessible(true);
            e.newInstance("COPY", 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("enum: " + ex.getMessage());
        }
    }
}
```

1. The enum singleton: one constant, created during class initialisation.
2. The JVM refuses to construct enum instances reflectively.

**Run it:** `java EnumSingletonDemo.java`

**Output:**

```text
classic: second instance created = true
enum: Cannot reflectively create enum objects
```

**What to notice:**

- Enums are also safe against serialization creating copies; a classic singleton needs `readResolve` for that.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** every admin action on prices must be audited with the operator's name. The team wraps admin services in a dynamic proxy instead of editing each method.

**AuditProxyDemo.java**

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Realistic: FreshCart wraps admin services in an audit proxy so every admin action is logged with the operator,
 * without touching the services. The proxy also shows the classic limit of proxy-based AOP: calls a service makes
 * to itself don't go through the proxy.
 */
public class AuditProxyDemo {

    interface PriceAdmin {
        void setPrice(String sku, long paise);
        void bulkDiscount(List<String> skus, int percent);
    }

    static final class PriceAdminService implements PriceAdmin {
        final java.util.Map<String, Long> prices = new java.util.TreeMap<>(java.util.Map.of("MILK-1L", 6800L, "CURD-500G", 4500L));
        public void setPrice(String sku, long paise) {
            if (paise <= 0) throw new IllegalArgumentException("price must be positive");
            prices.put(sku, paise);
        }
        public void bulkDiscount(List<String> skus, int percent) {
            for (String s : skus) setPrice(s, prices.get(s) * (100 - percent) / 100);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T audited(T target, Class<T> api, String operator, List<String> audit) {
        return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            String call = operator + " " + method.getName() + java.util.Arrays.toString(args);
            try {
                Object result = method.invoke(target, args);
                audit.add("OK   " + call);
                return result;
            } catch (InvocationTargetException e) {
                audit.add("FAIL " + call + ": " + e.getCause().getMessage());
                throw e.getCause();
            }
        });
    }

    public static void main(String[] args) {
        List<String> audit = new ArrayList<>();
        PriceAdminService service = new PriceAdminService();
        PriceAdmin admin = audited(service, PriceAdmin.class, "ops:meena", audit);
        admin.setPrice("MILK-1L", 7000);
        try { admin.setPrice("CURD-500G", 0); } catch (IllegalArgumentException e) { System.out.println("caller saw: " + e.getMessage()); }
        admin.bulkDiscount(List.of("MILK-1L", "CURD-500G"), 10);
        audit.forEach(System.out::println);
        System.out.println("prices: " + service.prices + " (the two setPrice calls inside bulkDiscount were not audited)");
    }
}
```

1. `bulkDiscount` calls `setPrice` on `this`, not on the proxy.
2. Reflection wraps the target's exceptions in `InvocationTargetException`.
3. The proxy unwraps and rethrows the original exception so callers see the real error.

**Run it:** `java AuditProxyDemo.java`

**Output:**

```text
caller saw: price must be positive
OK   ops:meena setPrice[MILK-1L, 7000]
FAIL ops:meena setPrice[CURD-500G, 0]: price must be positive
OK   ops:meena bulkDiscount[[MILK-1L, CURD-500G], 10]
prices: {CURD-500G=4050, MILK-1L=6300} (the two setPrice calls inside bulkDiscount were not audited)
```

**What to notice:**

- The bulk discount changed two prices, but only the outer call was audited. This is the same reason `@Transactional` doesn't apply to self-invoked methods in Spring.
- Without unwrapping, callers would get an `UndeclaredThrowableException` instead of the `IllegalArgumentException`.

### Example 3: Production-grade

**Scenario:** FreshCart's pricing service reloads its configuration every few minutes while handling thousands of requests per second. A request must never combine the milk price from one version with the delivery fee from another.

**ConfigReloadDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Production-grade: FreshCart's pricing service reloads its price configuration while serving traffic. Each config
 * is an immutable, validated snapshot published through an AtomicReference; readers take one snapshot per request,
 * so they never mix values from two versions. A stress run checks that invariant across threads.
 */
public class ConfigReloadDemo {

    record PricingConfig(int version, Map<String, Long> prices, int deliveryFeePaise) {
        PricingConfig {
            prices = Map.copyOf(prices);
            if (prices.values().stream().anyMatch(p -> p <= 0)) throw new IllegalArgumentException("non-positive price in v" + version);
        }
    }

    static final class ConfigStore {
        private final AtomicReference<PricingConfig> current;
        private final List<Consumer<PricingConfig>> listeners = new ArrayList<>();
        ConfigStore(PricingConfig initial) { current = new AtomicReference<>(initial); }
        PricingConfig snapshot() { return current.get(); }
        void onChange(Consumer<PricingConfig> l) { listeners.add(l); }

        String reload(int version, Map<String, Long> prices, int fee) {
            PricingConfig next;
            try { next = new PricingConfig(version, prices, fee); }
            catch (IllegalArgumentException e) { return "rejected: " + e.getMessage() + " (still serving v" + current.get().version() + ")"; }
            PricingConfig prev = current.get();
            if (next.version() <= prev.version()) return "ignored stale v" + version;
            if (!current.compareAndSet(prev, next)) return "lost race, retry";
            listeners.forEach(l -> l.accept(next));
            return "now serving v" + version;
        }
    }

    /** One request: read a snapshot once and use only it. */
    static long basketTotal(ConfigStore store) {
        PricingConfig c = store.snapshot();
        return c.prices().get("MILK-1L") * 2 + c.deliveryFeePaise();
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigStore store = new ConfigStore(new PricingConfig(1, Map.of("MILK-1L", 6801L), 2499));   // every version v: milk 6800+v, fee 2500-v
        List<String> notified = new ArrayList<>();
        store.onChange(c -> notified.add("v" + c.version()));
        System.out.println(store.reload(2, Map.of("MILK-1L", -1L), 2500));
        System.out.println(store.reload(1, Map.of("MILK-1L", 6900L), 2500));

        // stress: readers compute totals while versions 2..200 are published; every total must match some single version
        AtomicInteger inconsistent = new AtomicInteger(), reads = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        List<Thread> readers = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            Thread t = new Thread(() -> {
                while (done.getCount() > 0) {
                    PricingConfig c = store.snapshot();
                    long total = c.prices().get("MILK-1L") * 2 + c.deliveryFeePaise();
                    if (total != 2 * (6800L + c.version()) + (2500 - c.version())) inconsistent.incrementAndGet();
                    reads.incrementAndGet();
                }
            });
            readers.add(t); t.start();
        }
        for (int v = 2; v <= 200; v++) store.reload(v, Map.of("MILK-1L", 6800L + v), 2500 - v);
        done.countDown();
        for (Thread t : readers) t.join();
        System.err.println("reads during reloads: " + reads.get());
        System.out.println("stress: inconsistent totals = " + inconsistent.get() + ", final " + store.snapshot().version()
                + ", listeners notified " + notified.size() + " times");
        System.out.println("basket now: Rs " + basketTotal(store) / 100.0);
    }
}
```

1. Each configuration is an immutable record with validation in its constructor.
2. An `AtomicReference` publishes snapshots safely (volatile semantics).
3. Invalid configurations are rejected before publication; the old one keeps serving.
4. Compare-and-set prevents two concurrent reloads from overwriting each other out of order.
5. A request reads the reference once and uses only that snapshot.

**Run it:** `java ConfigReloadDemo.java`

**Output:**

```text
rejected: non-positive price in v2 (still serving v1)
ignored stale v1
stress: inconsistent totals = 0, final 200, listeners notified 199 times
basket now: Rs 163.0
```

**What to notice:**

- Four reader threads kept reading (the read count goes to stderr) while 199 versions were published, and no total mixed two versions.
- Readers take no locks: one volatile read per request.
- If the code had kept two separate volatile fields (price map and fee), readers could combine values from different versions.

## 7. Pros, Cons & Trade-offs

| Mechanism | Pros | Cons |
|-----------|------|------|
| Class-init singletons | Lazy, thread-safe, lock-free afterwards | Per class loader; init failures are permanent |
| Dynamic proxies | Cross-cutting without code changes | Self-invocation gap; harder debugging |
| invokedynamic lambdas | Compact, fast after linking | First-call linking cost |
| MethodHandles / VarHandles | Fast, typed, precise memory ordering | Complex APIs, easy to misuse |
| Immutable + safe publication | Simple, lock-free sharing | Copying on change |

## 8. When to Use / When NOT to Use

**Go this deep when:**

- writing frameworks, libraries or concurrency utilities;
- debugging proxies, class loaders or visibility bugs;
- tuning startup or hot paths with evidence.

**Don't when:**

- application code works with standard idioms (enum singletons, immutable records, `java.util.concurrent`);
- a higher-level API exists (`AtomicInteger` instead of a VarHandle);
- you'd be guessing without measurement.

## 9. Best Practices

1. **Use enums or the holder idiom for singletons; avoid hand-rolled locking.** *(Consensus.)*
2. **Share immutable objects; publish replacements through volatile or atomics.** *(Consensus.)*
3. **Don't leak `this` from constructors.** *(Consensus.)*
4. **Keep intercepted methods public and called through the proxy.** *(Consensus.)*
5. **Store MethodHandles and VarHandles in static final fields.** *(Consensus.)*
6. **Prefer `java.util.concurrent` over low-level primitives.** *(Consensus.)*
7. **Test concurrency on ARM as well as x86, and with stress tools (jcstress).** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Half-built object seen by another thread (production failure mode)**  
> **Symptom:** rare `NullPointerException`s reading prices right after a config reload, only on the new ARM instances.
> **Root cause:** a mutable `PriceConfig` was filled after being assigned to a non-volatile shared field; readers saw the reference before the map contents.
> **Fix:** immutable snapshot built fully, then published through an `AtomicReference` (Example 3).

> 🚨 **Transactional method that isn't**  
> **Symptom:** partial writes after failures, despite `@Transactional`.
> **Root cause:** the annotated method was called from another method in the same class, bypassing the proxy.
> **Fix:** move it to another bean, or use `TransactionTemplate` explicitly.

> 🚨 **Two singletons in one JVM**  
> **Symptom:** a "global" rate limiter allowed twice the limit.
> **Root cause:** two web apps in one container each loaded the class with their own class loader.
> **Fix:** move shared state out of statics (a shared service, or a parent-loader library deliberately).

> 🚨 **Broken double-checked locking**  
> **Symptom:** occasional use of a partly initialised object.
> **Root cause:** DCL with a non-volatile field.
> **Fix:** volatile field, or the holder idiom.

## 11. Configuration & Tuning

| Option | Default | What it does | When to change it | Risk of changing |
|--------|---------|--------------|-------------------|------------------|
| `-Xlog:class+load` | off | Logs class loading with source | Debugging duplicates and loaders | Verbose |
| `-Djdk.proxy.debug` / ByteBuddy dump | off | Inspect generated proxies | Debugging AOP | Disk output |
| `-Djdk.invoke.MethodHandle.dumpMethodHandleInternals` | off | Dumps lambda forms | JVM-level debugging only | Very verbose |
| `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=app.jsa` | off | CDS archive of loaded classes | Faster startup | Archive per build |
| `--add-opens module/package=ALL-UNNAMED` | none | Allows deep reflection | Legacy frameworks | Breaks encapsulation |
| `spring.aop.proxy-target-class` | true (Boot) | Subclass vs interface proxies | Rarely | Final classes fail |

## 12. Security Considerations

- Strong encapsulation (Java 16/17) blocks reflective access to JDK internals; `--add-opens` reopens it and should be justified and tracked.
- Custom class loaders loading untrusted code don't sandbox it (the Security Manager is deprecated for removal since Java 17); isolate untrusted code in separate processes.
- Deserialization can instantiate classes without constructors; prefer enum singletons and serialization filters (`jdk.serialFilter`).
- Proxies and generated code are still code: audit what frameworks generate around security-sensitive methods.

## 13. Performance & Scalability

Class loading and lambda linking cost startup time; CDS/AppCDS and the Java 24+ AOT cache (JEP 483) reduce it. Proxies add a small per-call overhead that the JIT usually inlines for monomorphic sites. Reflection is slower than method handles in hot paths; generated accessors are fastest. Volatile reads are cheap on x86 (a plain load) and slightly more expensive on ARM; contended CAS loops degrade under heavy contention (consider `LongAdder`).

## 14. Testing It

Test the behaviour that depends on JVM guarantees, including concurrent invariants:

**ConfigReloadTest.java**

```java
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReloadTest {

    static ConfigReloadDemo.ConfigStore store() {
        return new ConfigReloadDemo.ConfigStore(new ConfigReloadDemo.PricingConfig(1, Map.of("MILK-1L", 6800L), 2500));
    }

    @Test
    void validConfigIsPublishedAndListenersNotified() {
        var s = store();
        List<Integer> seen = new ArrayList<>();
        s.onChange(c -> seen.add(c.version()));
        assertEquals("now serving v2", s.reload(2, Map.of("MILK-1L", 7000L), 2000));
        assertEquals(2, s.snapshot().version());
        assertEquals(List.of(2), seen);
    }

    @Test
    void invalidConfigIsRejectedAndOldOneKept() {
        var s = store();
        assertTrue(s.reload(2, Map.of("MILK-1L", 0L), 2500).startsWith("rejected"));
        assertEquals(1, s.snapshot().version());
    }

    @Test
    void staleVersionsAreIgnored() {
        var s = store();
        s.reload(3, Map.of("MILK-1L", 7000L), 2500);
        assertEquals("ignored stale v2", s.reload(2, Map.of("MILK-1L", 7100L), 2500));
    }

    @Test
    void snapshotIsImmutableEvenIfCallerMutatesItsMap() {
        var s = store();
        Map<String, Long> input = new HashMap<>(Map.of("MILK-1L", 7000L));
        s.reload(2, input, 2500);
        input.put("MILK-1L", 1L);
        assertEquals(7000L, s.snapshot().prices().get("MILK-1L"));
        assertThrows(UnsupportedOperationException.class, () -> s.snapshot().prices().put("X", 1L));
    }

    @Test
    void requestUsesOneSnapshot() {
        var s = store();
        assertEquals(6800L * 2 + 2500, ConfigReloadDemo.basketTotal(s));
    }
}
```

For real memory-model testing, use OpenJDK's jcstress, which runs millions of racing iterations and reports observed outcomes.

## 15. Observability & Operations

Use `jcmd <pid> VM.classloaders` and `VM.class_hierarchy` to find duplicate classes and loader leaks; JFR events for class loading and safepoints; heap dumps to find which loader holds a "singleton". For proxies, log bean types at startup (`AopUtils.isAopProxy`). Metaspace growth after redeploys usually means a class loader leak.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2000 | Java 1.3: `java.lang.reflect.Proxy` |
| 2004 | Java 5: JSR-133 memory model (fixes final and volatile semantics) |
| 2011 | Java 7: invokedynamic, MethodHandles |
| 2014 | Java 8: lambdas via LambdaMetafactory |
| 2017 | Java 9: VarHandles, StringConcatFactory, JPMS |
| 2020 | Java 15: hidden classes (JEP 371) |
| 2021 | Java 17: strong encapsulation of JDK internals by default |
| 2022 | Java 18: core reflection reimplemented with method handles (JEP 416) |
| 2025 | Java 24: ahead-of-time class loading and linking cache (JEP 483) |

## 17. Expert Deep Dive

- **Constant folding of handles.** `static final` MethodHandles and VarHandles are treated as constants by the JIT, enabling full inlining through them.
- **Hidden classes.** Unnamed, non-discoverable classes defined via `Lookup.defineHiddenClass`; used for lambdas and by frameworks for generated proxies, and unloadable independently.
- **Class loader leaks.** A single static reference from a parent loader (a JDBC driver, a ThreadLocal) keeps a whole web app's loader and metaspace alive after redeploy.
- **Final field semantics caveats.** Guarantees hold only if `this` doesn't escape during construction; reflection can modify finals (with consequences the JIT may not respect).
- **Project Leyden.** Ongoing work to shift class loading, linking and profiling earlier (AOT caches) without changing the programming model.

> 📖 **War story: the NullPointerException that only ARM could see**  
> FreshCart moved its pricing service to ARM-based instances to save cost. Within a day, a handful of requests per hour failed with `NullPointerException` right after config reloads. The reload code created a `PriceConfig`, assigned it to a plain static field, and then filled its map. On x86 the reordering never showed; on ARM, readers occasionally saw the new object with an empty map. **Fixes:** the config became an immutable record built completely before publication through an `AtomicReference`; a jcstress test reproduced the original race; ARM runners were added to CI. **Lesson:** "works on x86" is not a proof of correct publication; follow the memory model, not your hardware.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **When is a class initialised?** On **first active use**, once per class loader.
2. **What's a dynamic proxy?** A **runtime-generated class** routing calls to a handler.
3. **Is a lambda an inner class?** **No**; it's linked via **invokedynamic** to a hidden class.
4. **What does volatile guarantee?** **Visibility and ordering** of reads and writes to that field.
5. **Why are immutable objects thread-safe?** **State can't change after safe construction.**

**▸ 🟡 L3**

1. **Why is the holder idiom thread-safe?** **Class initialisation is locked and happens once.**
2. **JDK proxy vs CGLIB?** **Interfaces** vs **subclass**; final methods can't be proxied by subclassing.
3. **Why doesn't @Transactional work on self-invocation?** **The call doesn't go through the proxy.**
4. **MethodHandle vs reflection?** **Typed, checked once, inlinable** vs **checked per call**.
5. **Why does DCL need volatile?** Without it, **another thread can see a partly constructed object**.

**▸ 🔴 L4–L5**

1. **How can there be two singletons?** **Two class loaders.**
2. **What does JLS 17.5 guarantee?** **Final fields visible after construction**, if `this` didn't escape.
3. **What's a hidden class?** A **non-discoverable class** defined at runtime, e.g. for lambdas.
4. **What do VarHandles add over volatile?** **Per-access memory modes and CAS** on fields and arrays.
5. **How do you diagnose a class loader leak?** **Metaspace growth, heap dump GC roots of the loader, jcmd VM.classloaders.**

**▸ 💻 Hands-on**

**Task:** Make `AuditProxyDemo` audit the inner `setPrice` calls too, without changing the proxy.

> ✅ **Solution**  
> ```java
> // Give the service a reference to its own proxy and call through it:
> static final class PriceAdminService implements PriceAdmin {
>     PriceAdmin self = this;                 // replaced with the proxy after creation
>     public void bulkDiscount(List<String> skus, int percent) {
>         for (String s : skus) self.setPrice(s, prices.get(s) * (100 - percent) / 100);
>     }
> }
> service.self = admin;   // now inner calls are audited (Spring: inject the bean into itself or split beans)
> ```

## 19. Practice

- [ ] Run a demo with `-Xlog:class+load` and find when the holder class loads.
    > 💡 **Hint**  
    > `java -Xlog:class+load ClassLoadingSingletonDemo.java | grep Holder`.

- [ ] Use `javap -c -p` on a class with a lambda and find the `invokedynamic`.
    > 💡 **Hint**  
    > Compile first with `javac`, then look for `lambda$` methods.

- [ ] Write a jcstress test for unsafe publication of a mutable object.
    > 💡 **Hint**  
    > Start from jcstress's `UnsafePublication` sample.

- [ ] Replace a reflective call in a hot path with a static final MethodHandle and benchmark it with JMH.
    > 💡 **Hint**  
    > Use `invokeExact` with exact casts.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Class init: once per loader, locked; holder idiom and enums are safe lazy singletons.
> - Proxies: JDK for interfaces, subclasses otherwise; self-calls and finals aren't intercepted.
> - Lambdas: invokedynamic + LambdaMetafactory → hidden class; non-capturing cached.
> - MethodHandles: typed, fast if static final; VarHandles: memory modes + CAS.
> - JMM: no happens-before, no visibility; publish immutable snapshots via volatile/atomics.
>
> ```java
> private static final class Holder { static final PriceTable INSTANCE = new PriceTable(); }
> static PriceTable get() { return Holder.INSTANCE; }
> ```

## 21. Further Reading

- *The Java Language Specification*, chapters 12.4 (initialisation) and 17 (threads and locks).
- Brian Goetz et al., *Java Concurrency in Practice* (2006), chapter 3 "Sharing Objects".
- Aleksey Shipilëv, [JMM Pragmatics](https://shipilev.net/blog/2014/jmm-pragmatics/) and the jcstress samples.
- JEP 371 (Hidden Classes), JEP 416 (Reimplement Core Reflection with Method Handles), JEP 483 (AOT Class Loading & Linking).

---

[← Previous: Legacy Modernisation Patterns](legacy-modernisation.md) · [Next: Designing Frameworks and Libraries →](framework-and-library-design.md)
