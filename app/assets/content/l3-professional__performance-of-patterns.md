# Performance Cost of Patterns

L3 🟡 Professional · ⏱ 45 min read · Prerequisites: [Decorator](../l1-foundations/decorator.md), [Proxy](../l2-practitioner/proxy.md), [Patterns in Spring](patterns-in-spring.md)

> 📌 **TL;DR**  
> Most design patterns add indirection: an interface call, a wrapper object, a proxy, a lookup. On the JVM that indirection is usually **almost free**, because the JIT compiler inlines calls at **monomorphic and bimorphic** call sites and **escape analysis** removes short-lived objects. Costs become real when call sites turn **megamorphic** (many implementations through one site), when objects **escape** (stored, boxed, carried in loops), when calls go through **reflection or dynamic proxies** on hot paths, and at **startup**, where reflective DI containers scan and wire classes. Rules: don't guess, **measure with JMH**; compare against a baseline in the same run; optimise only the hot path, and keep the pattern everywhere else.

## 1. What It Is

**Plain English.** People worry that patterns make code slow: "a Decorator chain of four classes must be four times slower than one method." Usually it isn't, because the JVM rewrites your code as it runs. This page explains when the worry is wrong, when it's right, and how to find out for your own code.

**Definition.** The main cost sources:

| Cost | Patterns affected | Usually matters? |
|------|-------------------|------------------|
| Virtual/interface calls | Strategy, Decorator, State, Visitor, Observer | Only when megamorphic in hot loops |
| Allocation | Builder, Command, value objects, iterators, boxing | When objects escape or are boxed in bulk |
| Reflection and proxies | Spring AOP, JDK Proxy, ORMs, serializers | Hot paths with very short methods |
| Startup | DI containers, classpath scanning, proxies | Serverless, CLIs, autoscaling |
| Memory | Flyweight (helps), caches, wrappers | Large collections |

## 2. Why It Exists

Two opposite mistakes are common. One team avoids interfaces and small objects "for performance" and ends up with unmaintainable code that's no faster. Another stacks proxies, reflection and boxing on a hot path and wonders why throughput collapses. Understanding how HotSpot optimises (inlining, type profiling, escape analysis) and how to measure correctly lets you keep good design almost everywhere and spend effort only where measurements say it pays.

## 3. Mental Model / Analogy

**Analogy.** A warehouse picker following instructions on cards. If every card says "go to the next card" (indirection), it seems slow, but after a few rounds the picker memorises the route and skips the cards (inlining). If the route changes every time (megamorphic), they must read every card. Packing items into boxes that are immediately unpacked costs nothing once the picker learns to skip boxing (escape analysis); boxes that are shipped (escaping) must be real.

**Technical model.** HotSpot starts by interpreting, profiles each call site (which receiver types appear), and compiles hot methods with C2, inlining small methods behind cheap type guards. If a guard fails later, it deoptimises and recompiles.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Virtual calls, inlining and megamorphic call sites

**Description.** An interface call (`rule.fee(p)`) needs to find the right implementation. HotSpot records which classes actually appear at each call site. With one class (**monomorphic**), it inlines that implementation behind a type check; with two (**bimorphic**), both; with more (**megamorphic**), it uses a dispatch table and can't inline, which also blocks further optimisations. Patterns like Strategy are usually monomorphic per call site in practice, so they're free.

**Advantages / features.** Clean polymorphic designs cost nothing at mono/bimorphic sites; only a few hot sites ever need attention.

**CallSiteShapesDemo.java**

```java
/**
 * Monomorphic, bimorphic and megamorphic call sites. HotSpot profiles each call site; if it has seen one or two
 * receiver types it inlines them behind a cheap type check, with more it falls back to virtual dispatch and can't
 * inline. Results go to stdout; timings (machine-dependent) go to stderr.
 */
public class CallSiteShapesDemo {

    interface FeeRule { long fee(long paise); }
    static final class Flat implements FeeRule { public long fee(long p) { return 2_500; } }
    static final class Percent implements FeeRule { public long fee(long p) { return p / 50; } }
    static final class Capped implements FeeRule { public long fee(long p) { return Math.min(p / 40, 4_000); } }
    static final class Free implements FeeRule { public long fee(long p) { return 0; } }

    // Three identical loops so that each has its own call site and its own type profile.
    static long mono(FeeRule[] r, int n) { long s = 0; for (int i = 0; i < n; i++) s += r[i & (r.length - 1)].fee(i); return s; }
    static long bi(FeeRule[] r, int n) { long s = 0; for (int i = 0; i < n; i++) s += r[i & (r.length - 1)].fee(i); return s; }
    static long mega(FeeRule[] r, int n) { long s = 0; for (int i = 0; i < n; i++) s += r[i & (r.length - 1)].fee(i); return s; }

    interface Loop { long run(FeeRule[] r, int n); }

    static long measure(String label, Loop loop, FeeRule[] rules) {
        int n = 20_000_000;
        for (int warm = 0; warm < 5; warm++) loop.run(rules, n);
        long start = System.nanoTime();
        long result = loop.run(rules, n);
        System.err.printf("%-12s %6.2f ns/call%n", label, (System.nanoTime() - start) / (double) n);
        return result;
    }

    public static void main(String[] args) {
        long m = measure("monomorphic", CallSiteShapesDemo::mono, new FeeRule[] {new Percent()});
        long b = measure("bimorphic", CallSiteShapesDemo::bi, new FeeRule[] {new Percent(), new Flat()});
        long g = measure("megamorphic", CallSiteShapesDemo::mega, new FeeRule[] {new Percent(), new Flat(), new Capped(), new Free()});
        System.out.println("checksums: " + m + ", " + b + ", " + g);
        System.out.println("timings per call are printed to stderr; they vary by machine and JVM");
    }
}
```

1. Four Strategy implementations of a fee rule.
2. Three identical loops, so each call site gets its own type profile: one with one type, one with two, one with four.
3. Warm-up runs let the JIT compile each loop before it's timed.

**Run it:** `java CallSiteShapesDemo.java`

```text
checksums: 3999990000000, 2024995000000, 1032417380000
timings per call are printed to stderr; they vary by machine and JVM
```

Timings printed to stderr on one cloud VM (JDK 21); yours will differ:

| Call site | ns/call |
|-----------|---------|
| monomorphic | 1.5 |
| bimorphic | 1.4 |
| megamorphic | 5.4 |

| Situation | Typical effect |
|-----------|----------------|
| Strategy with one active implementation | Free (inlined) |
| Decorator chain, fixed composition | Usually free (each site sees one type) |
| Visitor / Observer over many types in one loop | Megamorphic: 3–5x per call, no inlining |
| `List<Shape>` with many shapes, `shape.area()` | Megamorphic |

**Use** polymorphism freely; check hot loops over heterogeneous collections. **Avoid** "optimising" by replacing interfaces with `instanceof` chains unless JMH shows a real gain; sealed types + `switch` is a clean alternative when you do need it.
**Real-world Java:** `ArrayList.forEach` with many lambda types, Stream pipelines (each lambda a different class), Netty pipelines.
**Best practice:** if a megamorphic site is hot, split by type (sort or group first) so each loop is monomorphic. **Gotcha:** microbenchmarks with one implementation look great and then degrade in production when more implementations load: profile pollution.

### Allocation and escape analysis

**Description.** Patterns create objects: value objects (`Money`), commands, iterators, builders, lambdas. Allocation in the JVM is cheap (bump-the-pointer in thread-local buffers), and **escape analysis** lets C2 eliminate objects that never leave a compiled method (scalar replacement). Objects that are stored in fields or arrays, returned to non-inlined callers, or carried around a loop usually remain real allocations and add GC work.

**Advantages / features.** Immutable value objects and small helper objects are often literally free.

**EscapeAnalysisDemo.java**

```java
import java.lang.management.ManagementFactory;

/**
 * Small, short-lived value objects are often free: escape analysis lets the JIT replace them with plain locals
 * (scalar replacement). Objects that are stored, or carried around a loop, are usually still allocated.
 * Bytes per iteration (machine- and JVM-dependent) go to stderr.
 */
public class EscapeAnalysisDemo {

    record Money(long paise) {
        Money plus(Money o) { return new Money(paise + o.paise); }
        Money times(int q) { return new Money(paise * q); }
    }

    static long temporaries(int n) {
        long total = 0;
        for (int i = 0; i < n; i++) total += new Money(i % 100).times(3).paise();
        return total;
    }

    static long loopCarried(int n) {
        Money total = new Money(0);
        for (int i = 0; i < n; i++) total = total.plus(new Money(i % 100).times(3));
        return total.paise();
    }

    static Money[] stored(int n) {
        Money[] kept = new Money[n];
        for (int i = 0; i < n; i++) kept[i] = new Money(i % 100).times(3);
        return kept;
    }

    static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    public static void main(String[] args) {
        int n = 5_000_000;
        for (int i = 0; i < 10; i++) { temporaries(n); loopCarried(n); }
        long b0 = allocatedBytes();
        long a = temporaries(n);
        long b1 = allocatedBytes();
        long b = loopCarried(n);
        long b2 = allocatedBytes();
        Money[] kept = stored(n);
        long b3 = allocatedBytes();

        System.err.printf("temporaries:  %6.2f bytes/iteration%n", (b1 - b0) / (double) n);
        System.err.printf("loop-carried: %6.2f bytes/iteration%n", (b2 - b1) / (double) n);
        System.err.printf("stored:       %6.2f bytes/iteration%n", (b3 - b2) / (double) n);
        System.out.println("same totals: " + (a == b) + ", stored " + kept.length + " objects");
        System.out.println("temporaries allocate least: " + ((b1 - b0) < (b2 - b1) && (b1 - b0) < (b3 - b2)));
    }
}
```

1. An immutable value object with methods returning new instances.
2. Temporaries used only to compute a `long` are eliminated by scalar replacement.
3. An object reassigned around a loop flows through a merge point; C2 often can't eliminate it, so one object per iteration is allocated.

**Run it:** `java EscapeAnalysisDemo.java`

```text
same totals: true, stored 5000000 objects
temporaries allocate least: true
```

Bytes allocated per iteration on one cloud VM (stderr):

| Style | Bytes/iteration |
|-------|-----------------|
| Temporaries | 0 |
| Loop-carried object | 24 |
| Stored in an array | ~29 (object + reference) |

| Allocation source | Mitigation |
|-------------------|------------|
| Boxing (`List<Long>`, `Map<Integer, ...>`) | Primitive arrays, `LongStream`, primitive collections |
| Loop-carried value objects | Accumulate primitives, convert at the end |
| Iterators in hot loops | Indexed loops over arrays/`ArrayList` (often eliminated anyway) |
| Lambdas capturing variables | Usually eliminated if inlined; non-capturing lambdas are singletons |
| Builders on hot paths | Fine if short-lived and inlined |

**Use** value objects everywhere; they're usually free and always clearer. **Avoid** boxing large numeric datasets; that cost is real and escape analysis doesn't help collections.
**Real-world Java:** `Optional` and `Stream` intermediates are often scalar-replaced; Valhalla value classes (preview) aim to make this guaranteed.
**Best practice:** measure allocation with JMH's `-prof gc` or JFR allocation profiling. **Gotcha:** escape analysis only works after C2 compiles and inlines; in short-lived processes (CLIs, tests) everything allocates.

### Proxy and reflection overhead

**Description.** Frameworks implement Proxy, Decorator and DI using JDK dynamic proxies, CGLIB/ByteBuddy subclasses, reflection (`Method.invoke`) and `MethodHandle`s. Each adds per-call overhead (argument arrays, boxing, access checks, `InvocationHandler` dispatch). For calls that do real work (database, HTTP, milliseconds), nanoseconds of overhead don't matter; for tiny methods in tight loops, they can dominate.

**Advantages / features.** The same transparency that makes Spring AOP, `@Transactional` and mocks possible.

**ProxyReflectionDemo.java**

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.LongUnaryOperator;

/** Direct call vs interface vs JDK dynamic proxy vs reflection vs MethodHandle: same result, different costs. */
public class ProxyReflectionDemo {

    public interface Pricing { long price(long paise); }
    public static final class GstPricing implements Pricing { public long price(long p) { return p + p * 5 / 100; } }

    static long run(String label, LongUnaryOperator call) {
        int n = 5_000_000;
        long sum = 0;
        for (int w = 0; w < 5; w++) for (int i = 0; i < n; i++) sum += call.applyAsLong(i);
        sum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) sum += call.applyAsLong(i);
        System.err.printf("%-14s %7.2f ns/call%n", label, (System.nanoTime() - start) / (double) n);
        return sum;
    }

    public static void main(String[] args) throws Throwable {
        GstPricing target = new GstPricing();
        Pricing viaInterface = target;
        Pricing proxy = (Pricing) Proxy.newProxyInstance(Pricing.class.getClassLoader(), new Class<?>[] {Pricing.class},
                (p, method, a) -> method.invoke(target, a));
        Method reflective = GstPricing.class.getMethod("price", long.class);
        MethodHandle handle = MethodHandles.lookup().findVirtual(GstPricing.class, "price", MethodType.methodType(long.class, long.class))
                .bindTo(target);

        long direct = run("direct", target::price);
        long iface = run("interface", viaInterface::price);
        long prox = run("dynamic proxy", proxy::price);
        long refl = run("reflection", p -> { try { return (long) reflective.invoke(target, p); } catch (Exception e) { throw new RuntimeException(e); } });
        long mh = run("MethodHandle", p -> { try { return (long) handle.invokeExact(p); } catch (Throwable e) { throw new RuntimeException(e); } });

        System.out.println("all five give the same total: " + (direct == iface && iface == prox && prox == refl && refl == mh));
        System.out.println("total = " + direct);
    }
}
```

1. A JDK dynamic proxy forwarding to the target, like a minimal AOP proxy.
2. Plain reflection: boxing arguments and results on each call.
3. A bound `MethodHandle`: the JIT can optimise it much better than `Method.invoke`.

**Run it:** `java ProxyReflectionDemo.java`

```text
all five give the same total: true
total = 13124995000000
```

On one cloud VM (stderr; via a lambda wrapper, so all include that cost):

| Call style | ns/call |
|------------|---------|
| direct | 2.9 |
| interface | 3.2 |
| dynamic proxy | 8.7 |
| reflection (`Method.invoke`) | 18.7 |
| `MethodHandle` | 8.6 |

| Mechanism | Used by | Notes |
|-----------|---------|-------|
| JDK dynamic proxy | Spring AOP (interfaces), Mockito (some modes), Retrofit | Reflection-based dispatch |
| Subclass proxies (ByteBuddy/CGLIB) | Spring AOP on classes, Hibernate lazy loading | Generated bytecode, faster than JDK proxy |
| `Method.invoke` | Older frameworks, Jackson (partly) | Reimplemented on method handles in JDK 18 (JEP 416) |
| `MethodHandle` / `LambdaMetafactory` | Modern frameworks, records' `toString` | Near-direct speed when constant |

**Use** proxies and reflection at boundaries (services, repositories, controllers). **Avoid** them inside tight computational loops; call the target directly or cache method handles in `static final` fields.
**Real-world Java:** Spring `@Transactional` proxies, Hibernate proxies, Jackson's afterburner/blackbird modules.
**Best practice:** a `static final MethodHandle` is treated as a constant and can be inlined. **Gotcha:** Spring self-invocation (`this.method()`) bypasses the proxy entirely, so `@Transactional` or `@Cacheable` silently doesn't apply.

### Measuring with JMH

**Description.** Hand-written timing loops lie: the JIT warms up mid-measurement, removes code whose result is unused (dead-code elimination), folds constants, and different benchmarks pollute each other's type profiles in one JVM. **JMH** (Java Microbenchmark Harness, from the OpenJDK team) handles warm-up, forks fresh JVMs, consumes results with a `Blackhole`, and reports statistics with error bars.

**Advantages / features.** Trustworthy microbenchmarks; built-in profilers (`-prof gc`, `-prof perfasm`, `-prof stack`).

The Maven project in `examples/l3-professional/performance-of-patterns/jmh/` benchmarks the pricing Decorator chain against a flat method:

**PricingBenchmark.java**

```java
package com.freshcart.perf;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Does a decorator chain of pricing rules cost more than one flat method? JMH handles warm-up, forking,
 * dead-code elimination and statistics, which hand-written timing loops get wrong.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PricingBenchmark {

    interface Price { long of(long paise); }
    record Base() implements Price { public long of(long p) { return p; } }
    record Gst(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v + v * 5 / 100; } }
    record Member(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v - v / 20; } }
    record Rounded(Price inner) implements Price { public long of(long p) { return Math.round(inner.of(p) / 100.0) * 100; } }

    @Param({"52000", "120000"})
    long subtotal;

    Price chain;
    List<Price> megamorphicRules;

    @Setup
    public void setUp() {
        chain = new Rounded(new Member(new Gst(new Base())));
        megamorphicRules = List.of(new Base(), new Gst(new Base()), new Member(new Base()), new Rounded(new Base()));
    }

    @Benchmark
    public long flat() {
        long v = subtotal + subtotal * 5 / 100;
        v = v - v / 20;
        return Math.round(v / 100.0) * 100;
    }

    @Benchmark
    public long decoratorChain() {
        return chain.of(subtotal);                     // returned values are consumed by JMH: no dead-code elimination
    }

    @Benchmark
    public void megamorphicLoop(Blackhole bh) {
        for (Price p : megamorphicRules) bh.consume(p.of(subtotal));
    }

    @Benchmark
    public void wrongDeadCode() {
        chain.of(subtotal);                             // result unused: the JIT may remove the work entirely
    }
}
```

**Run it:**

```bash
cd examples/l3-professional/performance-of-patterns/jmh
mvn -q package
java -jar target/benchmarks.jar                       # full run, about 2 minutes
java -jar target/benchmarks.jar -f 1 -wi 2 -i 3 -p subtotal=52000   # quick check
```

One quick run on a cloud VM (short, so the error bars are wide):

```text
Benchmark                         (subtotal)  Mode  Cnt   Score    Error  Units
PricingBenchmark.decoratorChain        52000  avgt    3   7.605 ± 15.253  ns/op
PricingBenchmark.flat                  52000  avgt    3   6.943 ± 14.293  ns/op
PricingBenchmark.megamorphicLoop       52000  avgt    3  28.254 ±  8.952  ns/op
PricingBenchmark.wrongDeadCode         52000  avgt    3   2.288 ±  1.711  ns/op
```

The decorator chain and the flat method are within noise of each other. `wrongDeadCode` looks fastest because the JIT can discard work whose result is never used; that number is meaningless.

| Pitfall | JMH remedy |
|---------|-----------|
| No warm-up | `@Warmup` iterations |
| Dead-code elimination | Return results or use `Blackhole.consume` |
| Constant folding | Read inputs from `@State` fields, not literals |
| Profile pollution between benchmarks | `@Fork` runs each in a fresh JVM |
| One noisy run | Multiple forks and iterations; report error |
| Unknown cause | `-prof gc`, `-prof stack`, `-prof perfasm` |

**Use** JMH for any claim like "X is faster than Y" at the method level. **Avoid** microbenchmarks for whole-system questions; use load tests and production profiling (JFR, async-profiler) for those.
**Real-world Java:** the JDK itself, Netty, Caffeine and Jackson all maintain JMH suites.
**Best practice:** benchmark realistic inputs (`@Param`) and the shapes production sees (several implementations, not one). **Gotcha:** a benchmark that's faster than physics (sub-nanosecond for real work) is measuring nothing.

### Startup cost of DI containers

**Description.** Reflective DI containers do work at startup: scan the classpath, read annotations, resolve constructors, create proxies. That costs hundreds of milliseconds to seconds, which matters for serverless functions, CLIs, autoscaling and tests. Compile-time DI (Dagger, Micronaut, Quarkus build-time processing, Spring AOT) moves that work to the build.

**Advantages / features.** Same Dependency Injection pattern, different time of wiring; faster startup and lower memory.

**DiStartupDemo.java**

```java
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Startup cost of a DI container: a reflective container discovers constructors and resolves dependencies at
 * runtime; hand wiring (or compile-time DI like Dagger, Micronaut, Spring AOT) does that work at build time.
 */
public class DiStartupDemo {

    public static final class Clock { }
    public static final class PriceRepo { public PriceRepo(Clock c) { } }
    public static final class StockRepo { public StockRepo(Clock c) { } }
    public static final class Pricing { public Pricing(PriceRepo r) { } }
    public static final class Stock { public Stock(StockRepo r) { } }
    public static final class Checkout { public Checkout(Pricing p, Stock s, Clock c) { } }

    /** A tiny reflective container: picks each class's only constructor and resolves parameters recursively. */
    static final class ReflectiveContainer {
        private final Map<Class<?>, Object> singletons = new HashMap<>();
        final List<String> created = new ArrayList<>();
        <T> T get(Class<T> type) {
            Object existing = singletons.get(type);
            if (existing != null) return type.cast(existing);
            try {
                Constructor<?> ctor = type.getConstructors()[0];
                Object[] deps = new Object[ctor.getParameterCount()];
                for (int i = 0; i < deps.length; i++) deps[i] = get(ctor.getParameterTypes()[i]);
                Object instance = ctor.newInstance(deps);
                singletons.put(type, instance);
                created.add(type.getSimpleName());
                return type.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot create " + type.getSimpleName(), e);
            }
        }
    }

    static Checkout wireByHand() {
        Clock clock = new Clock();
        return new Checkout(new Pricing(new PriceRepo(clock)), new Stock(new StockRepo(clock)), clock);
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        ReflectiveContainer container = new ReflectiveContainer();
        Checkout viaContainer = container.get(Checkout.class);
        long t1 = System.nanoTime();
        Checkout byHand = wireByHand();
        long t2 = System.nanoTime();
        System.err.printf("reflective container: %d us, hand wiring: %d us (first call, cold JVM)%n", (t1 - t0) / 1000, (t2 - t1) / 1000);

        System.out.println("container created, in dependency order: " + container.created);
        System.out.println("Clock shared as a singleton: " + (container.get(Clock.class) == container.get(Clock.class)));
        System.out.println("both produced a Checkout: " + (viaContainer != null && byHand != null));
    }
}
```

1. A tiny reflective container: discovers constructors and resolves dependencies at runtime, caching singletons.
2. Hand wiring: the same object graph built with plain `new`; this is what compile-time DI generates.
3. The container created classes in dependency order; both approaches produce the same graph.

**Run it:** `java DiStartupDemo.java`

```text
container created, in dependency order: [Clock, PriceRepo, Pricing, StockRepo, Stock, Checkout]
Clock shared as a singleton: true
both produced a Checkout: true
```

On one cloud VM (stderr, cold JVM): reflective container about 2.8 ms for six classes, hand wiring about 5 µs. Real containers handle thousands of classes, plus scanning and proxy generation.

| Approach | Wiring happens | Startup | Examples |
|----------|----------------|---------|----------|
| Reflective container | Runtime | Slowest | Spring (classic), Guice |
| Build-time processing | Build | Fast | Micronaut, Quarkus, Dagger |
| Spring AOT + GraalVM native image | Build | Very fast (tens of ms) | Spring Boot 3 native |
| CDS / AppCDS / Leyden (AOT cache, JDK 24+) | Training run | Faster JVM startup | Any JVM app |
| Hand wiring | Code | Fastest | Small apps, libraries |

**Use** compile-time DI or native images when startup matters (functions, CLIs, scale-to-zero). **Avoid** premature migration for long-running services, where startup is paid once.
**Real-world Java:** Spring Boot 3 AOT and native, Micronaut, Quarkus, Dagger on Android (reflection is slow there).
**Best practice:** measure startup with `-Xlog:startuptime` or Spring's `ApplicationStartup` / `BufferingApplicationStartup` to find slow beans. **Gotcha:** native images need reflection metadata; libraries that use reflection dynamically need hints or fail at runtime.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop's reporting job sums a million order amounts held in a `List<Long>`. How much memory does boxing cost compared with a `long[]`?

**BoxingCostDemo.java**

```java
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** QuickDrop: summing order amounts in a List<Long> vs a long[]. Boxing costs memory and indirection. */
public class BoxingCostDemo {

    static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    public static void main(String[] args) {
        int n = 1_000_000;
        long b0 = allocated();
        List<Long> boxed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) boxed.add(1_000L + i);
        long b1 = allocated();
        long[] primitive = new long[n];
        for (int i = 0; i < n; i++) primitive[i] = 1_000L + i;
        long b2 = allocated();

        long sumBoxed = 0;
        for (Long v : boxed) sumBoxed += v;
        long sumPrimitive = 0;
        for (long v : primitive) sumPrimitive += v;

        System.err.printf("List<Long>: %.1f bytes per amount, long[]: %.1f bytes per amount%n", (b1 - b0) / (double) n, (b2 - b1) / (double) n);
        System.out.println("same sum: " + (sumBoxed == sumPrimitive) + " (" + sumPrimitive + ")");
        System.out.println("boxed list used at least twice the memory: " + ((b1 - b0) >= 2 * (b2 - b1)));
    }
}
```

1. Each `Long` is a separate heap object, plus a reference in the list.
2. A primitive array stores the values inline, 8 bytes each.

**Run it:** `java BoxingCostDemo.java`

**Output:**

```text
same sum: true (500999500000)
boxed list used at least twice the memory: true
```

**What to notice:**

- On one VM (stderr), the list used about 28 bytes per amount and the array 8: boxing costs 3.5x memory, and escape analysis can't remove objects stored in a collection.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** a flash-sale repricing job runs pricing rules (GST, member discount, sale) over 2 million cart lines. Someone proposes replacing the Decorator chain with one flat method "for speed". Measure first.

**PricingHotPathDemo.java**

```java
import java.util.List;

/**
 * FreshCart prices 2 million cart lines during a flash-sale repricing job. Is the Decorator chain of pricing rules
 * too slow? Measure before flattening: usually the chain is inlined and costs little, unless call sites go
 * megamorphic. Results to stdout, timings to stderr.
 */
public class PricingHotPathDemo {

    interface Price { long of(long paise); }
    record Base() implements Price { public long of(long p) { return p; } }
    record Gst(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v + v * 5 / 100; } }
    record Member(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v - v / 20; } }
    record FlashSale(Price inner, int percent) implements Price { public long of(long p) { long v = inner.of(p); return v - v * percent / 100; } }

    static long flat(long p) {
        long v = p + p * 5 / 100;
        v = v - v / 20;
        return v - v * 10 / 100;
    }

    static long runChain(Price price, long[] lines) { long s = 0; for (long l : lines) s += price.of(l); return s; }
    static long runFlat(long[] lines) { long s = 0; for (long l : lines) s += flat(l); return s; }

    static long time(String label, java.util.function.LongSupplier work) {
        for (int i = 0; i < 10; i++) work.getAsLong();
        long best = Long.MAX_VALUE, result = 0;
        for (int i = 0; i < 5; i++) {
            long t = System.nanoTime();
            result = work.getAsLong();
            best = Math.min(best, System.nanoTime() - t);
        }
        System.err.printf("%-22s best of 5: %6.2f ms%n", label, best / 1e6);
        return result;
    }

    public static void main(String[] args) {
        long[] lines = new long[2_000_000];
        for (int i = 0; i < lines.length; i++) lines[i] = 2_000 + (i * 37L) % 98_000;
        Price chain = new FlashSale(new Member(new Gst(new Base())), 10);

        long a = time("flat method", () -> runFlat(lines));
        long b = time("decorator chain", () -> runChain(chain, lines));
        List<Price> variants = List.of(chain, new Gst(new Base()), new Member(new Base()), new FlashSale(new Base(), 20));
        long c = time("4 chains, same site", () -> { long s = 0; for (Price p : variants) s += runChain(p, lines); return s; });

        System.out.println("flat and chain agree: " + (a == b) + ", total " + a);
        System.out.println("mixed run total: " + c);
    }
}
```

1. The flattened alternative: the same arithmetic in one method.
2. Warm-up before timing, and the best of five runs, to reduce noise.
3. Several chain shapes through the same call site make it megamorphic.

**Run it:** `java PricingHotPathDemo.java`

**Output:**

```text
flat and chain agree: true, total 91559853307
mixed run total: 377124505307
```

**What to notice:**

- On one VM (stderr): flat 9.2 ms, decorator chain 9.6 ms. The pattern is effectively free when the chain's shape is stable.
- Running four different chain shapes through one site took 59 ms for 4x the work, about 1.6x slower per line: megamorphism, not the pattern itself, is the cost.
- The right decision is to keep the decorators; if mixed shapes are ever hot, process each shape in its own batch.

### Example 3: Production-grade

**Scenario:** DairyDay wants CI to catch performance regressions in the pricing hot path. Absolute timings vary between developer laptops and CI machines, so the gate must compare against a baseline measured in the same run, use medians, avoid dead-code elimination, fail obvious regressions even on noisy machines, and say INCONCLUSIVE rather than flake when results are too noisy to judge. Its logic must be unit-testable without real timing.

**PerfBudgetGateDemo.java**

```java
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * DairyDay's CI performance gate for the pricing hot path. Absolute timings differ between laptops and CI machines,
 * so the gate compares each candidate with a baseline measured in the same JVM run, uses medians over many rounds,
 * guards against dead-code elimination with a sink, and reports INCONCLUSIVE instead of failing when the
 * measurements are too noisy. The clock is injectable so the gate's own logic is unit-testable.
 */
// run-all: retries=3 (real timings can be noisy on a busy machine)
public class PerfBudgetGateDemo {

    enum Verdict { PASS, FAIL, INCONCLUSIVE }
    record Stats(double minNanos, double medianNanos, double p90Nanos, double cv) {}
    record Budget(String candidate, String baseline, double maxRatio) {
        Budget { if (maxRatio < 1.0) throw new IllegalArgumentException("maxRatio below 1.0 means the candidate must beat the baseline"); }
    }
    record Report(Map<String, Stats> stats, Map<String, Verdict> verdicts) {}

    static final class Harness {
        private final LongSupplier clock;
        private final int warmupRounds, rounds;
        volatile long sink;
        Harness(LongSupplier clock, int warmupRounds, int rounds) { this.clock = clock; this.warmupRounds = warmupRounds; this.rounds = rounds; }

        Stats measure(LongSupplier work) {
            for (int i = 0; i < warmupRounds; i++) sink += work.getAsLong();
            double[] samples = new double[rounds];
            for (int i = 0; i < rounds; i++) {
                long t = clock.getAsLong();
                sink += work.getAsLong();
                samples[i] = clock.getAsLong() - t;
            }
            Arrays.sort(samples);
            double mean = Arrays.stream(samples).average().orElse(0);
            double sd = Math.sqrt(Arrays.stream(samples).map(s -> (s - mean) * (s - mean)).average().orElse(0));
            return new Stats(samples[0], samples[rounds / 2], samples[(int) Math.ceil(rounds * 0.9) - 1], mean == 0 ? 0 : sd / mean);
        }
    }

    static Report evaluate(Harness h, Map<String, LongSupplier> benchmarks, List<Budget> budgets, double maxCv) {
        Map<String, Stats> stats = new LinkedHashMap<>();
        benchmarks.forEach((name, work) -> stats.put(name, h.measure(work)));
        Map<String, Verdict> verdicts = new LinkedHashMap<>();
        for (Budget b : budgets) {
            Stats c = stats.get(b.candidate()), base = stats.get(b.baseline());
            if (c.minNanos() / base.p90Nanos() > b.maxRatio()) { verdicts.put(b.candidate(), Verdict.FAIL); continue; }
            if (c.cv() > maxCv || base.cv() > maxCv) { verdicts.put(b.candidate(), Verdict.INCONCLUSIVE); continue; }
            double ratio = c.medianNanos() / base.medianNanos();
            verdicts.put(b.candidate(), ratio <= b.maxRatio() ? Verdict.PASS : Verdict.FAIL);
        }
        return new Report(stats, verdicts);
    }

    // ---------- The code under test ----------
    interface Price { long of(long paise); }
    record Base() implements Price { public long of(long p) { return p; } }
    record Gst(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v + v * 5 / 100; } }
    record Member(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v - v / 20; } }
    static long flat(long p) { long v = p + p * 5 / 100; return v - v / 20; }

    public static void main(String[] args) {
        long[] lines = new long[200_000];
        for (int i = 0; i < lines.length; i++) lines[i] = 2_000 + (i * 37L) % 98_000;
        Price chain = new Member(new Gst(new Base()));
        Price viaStrings = p -> Long.parseLong(String.format("%d", chain.of(p)));      // a regression someone "simplified" in

        Map<String, LongSupplier> benches = new LinkedHashMap<>();
        benches.put("flat", () -> { long s = 0; for (long l : lines) s += flat(l); return s; });
        benches.put("decorator", () -> { long s = 0; for (long l : lines) s += chain.of(l); return s; });
        benches.put("stringly", () -> { long s = 0; for (long l : lines) s += viaStrings.of(l); return s; });

        List<Budget> budgets = List.of(new Budget("decorator", "flat", 3.0), new Budget("stringly", "flat", 3.0));
        Report report = evaluate(new Harness(System::nanoTime, 30, 25), benches, budgets, 0.5);

        report.stats().forEach((k, s) -> System.err.printf("%-10s median %8.0f ns, p90 %8.0f ns, cv %.2f%n", k, s.medianNanos(), s.p90Nanos(), s.cv()));
        System.out.println("budgets: decorator <= 3.0x flat, stringly <= 3.0x flat");
        report.verdicts().forEach((k, v) -> System.out.println(k + ": " + (v == Verdict.INCONCLUSIVE ? "INCONCLUSIVE (noisy machine, rerun)" : v)));
    }
}
```

1. A volatile sink consumes every result, so the JIT can't discard the measured work.
2. Warm-up rounds run before measurement; the clock is injected (`System::nanoTime` in production, scripted in tests).
3. Median and p90 resist outliers; the coefficient of variation measures noise.
4. If even the candidate's best time exceeds the budget against the baseline's p90, it's a clear failure regardless of noise.
5. Otherwise, the median ratio is compared with the budget, unless noise makes the result inconclusive.

**Run it:** `java PerfBudgetGateDemo.java`

**Output:**

```text
budgets: decorator <= 3.0x flat, stringly <= 3.0x flat
decorator: PASS
stringly: FAIL
```

**What to notice:**

- The decorator chain passed (it runs at the same speed as the flat method).
- The "stringly" regression (formatting and parsing a number per call) failed by a wide margin even though its timings were noisy because of GC.
- For production use, run real JMH benchmarks in CI with the same relative-budget idea, on dedicated runners.

## 7. Pros, Cons & Trade-offs

| Choice | Pros | Cons |
|--------|------|------|
| Patterns with interfaces | Flexible, testable; usually free | Megamorphic hot sites cost 3–5x per call |
| Value objects | Clear, safe; usually scalar-replaced | Real allocation when stored or loop-carried |
| Proxies / reflection | Transparent cross-cutting concerns | Per-call overhead; self-invocation pitfalls |
| Reflective DI | Convenient, dynamic | Slower startup, more memory |
| Compile-time DI / AOT | Fast startup | Build complexity; less dynamism |
| Hand optimisation | Maximum speed on a hot path | Harder to read and change |

## 8. When to Use / When NOT to Use

**Worry about pattern cost when:**

- profiling shows a hot method dominated by dispatch, reflection or allocation;
- the code runs millions of times per second (pricing engines, parsers, serializers);
- startup time is a requirement (serverless, CLIs, scale-to-zero).

**Don't worry when:**

- the method does I/O (database, network): microseconds are lost in milliseconds;
- the code runs rarely (admin screens, nightly jobs);
- you haven't measured.

## 9. Best Practices

1. **Measure before optimising; use JMH for micro-level claims.** *(Consensus.)*
2. **Profile production-like workloads (JFR, async-profiler) to find real hot spots.** *(Consensus.)*
3. **Keep patterns; optimise only measured hot paths.** *(Consensus.)*
4. **Avoid boxing in large numeric workloads.** *(Consensus.)*
5. **Keep hot call sites monomorphic where it matters (group by type).** *(Consensus among performance engineers.)*
6. **Cache reflective lookups; prefer `MethodHandle` in `static final` fields.** *(Consensus.)*
7. **Use relative performance budgets in CI, on stable runners.** *(Opinion, widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Proxy on a hot path (production failure mode)**  
> **Symptom:** after adding `@Timed` and `@Cacheable` to a small price-lookup method, the repricing job slowed from 4 to 11 minutes.
> **Root cause:** the method was called 50 million times per run; each call went through an AOP proxy, a cache key allocation and a metrics timer, costing more than the method's own work.
> **Fix:** move the annotations to the batch-level method; inside the loop, call the plain implementation; measure with JMH and JFR before and after.

> 🚨 **Benchmarking dead code**  
> **Symptom:** a benchmark shows the new algorithm at 0.3 ns per call, "100x faster".
> **Root cause:** the result was unused, so the JIT removed the work.
> **Fix:** return results or use `Blackhole`; sanity-check numbers against physics.

> 🚨 **Premature de-patterning**  
> **Symptom:** strategies replaced with a 400-line `switch` "for performance"; no measurable gain; every change now risky.
> **Root cause:** optimising without measurement.
> **Fix:** revert; measure; optimise only proven hot spots.

> 🚨 **Startup blamed on the JVM**  
> **Symptom:** a Lambda function takes 9 seconds on cold start.
> **Root cause:** a full Spring context with classpath scanning of 4,000 classes for a function that uses three beans.
> **Fix:** Spring AOT/native image, or a lighter framework, or hand wiring; enable SnapStart/CRaC where available.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `-XX:+DoEscapeAnalysis` | on | Escape analysis | Never turn off (diagnostics only) | Large slowdowns |
| `-XX:MaxInlineSize` / `-XX:FreqInlineSize` | 35 / 325 bytecodes | Inlining limits | Rarely; measure | Code bloat |
| `-XX:TypeProfileMajorReceiverPercent` | 90 | When a dominant type is inlined at polymorphic sites | Rarely | Deopt churn |
| `-XX:TieredStopAtLevel=1` | full tiering | C1 only: faster startup, slower peak | Short-lived tools, dev | Lower peak performance |
| `-XX:SharedArchiveFile` (AppCDS) | none | Class data sharing archive | Faster startup | Archive maintenance |
| `spring.main.lazy-initialization` | false | Creates beans on first use | Dev and tests | First-request latency, late errors |

## 12. Security Considerations

- Reflection-heavy code may need `--add-opens`, weakening module encapsulation; prefer `MethodHandles.privateLookupIn` with explicit grants.
- Dynamic proxies and bytecode generation increase attack surface (deserialisation gadgets have used proxy classes); keep libraries updated.
- Performance shortcuts must not skip security checks (e.g., bypassing a security proxy by calling the target directly in a loop); keep authorisation at the batch boundary.
- Benchmarks and profilers in production must not expose data; restrict JFR and async-profiler access.

## 13. Performance & Scalability

Rough costs on modern hardware (orders of magnitude, measure your own): inlined call ~0 ns; megamorphic interface call ~2–5 ns; small allocation ~5–20 ns plus GC; dynamic proxy ~5–10 ns; `Method.invoke` ~10–30 ns; uncontended lock ~20 ns; L3 cache miss ~40 ns; database round trip 0.5–5 ms. Pattern overhead matters only when it is a meaningful fraction of the work per call. For throughput, GC pressure from escaping allocations often matters more than dispatch.

## 14. Testing It

Performance logic (statistics, budgets, noise handling) is ordinary code and deserves deterministic unit tests with a scripted clock; the measurements themselves run in JMH or a dedicated CI stage:

**PerfBudgetGateTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** The gate's decisions are tested with a scripted clock, so these tests are deterministic on any machine. */
class PerfBudgetGateTest {

    /** Each measured round reads the clock twice; this clock returns 0 then the scripted duration. */
    static LongSupplier scripted(long... durations) {
        Deque<Long> readings = new ArrayDeque<>();
        for (long d : durations) { readings.add(0L); readings.add(d); }
        return readings::poll;
    }

    static PerfBudgetGateDemo.Report run(long[] baseline, long[] candidate, double maxRatio) {
        long[] all = new long[baseline.length + candidate.length];
        System.arraycopy(baseline, 0, all, 0, baseline.length);
        System.arraycopy(candidate, 0, all, baseline.length, candidate.length);
        var harness = new PerfBudgetGateDemo.Harness(scripted(all), 0, baseline.length);
        Map<String, LongSupplier> benches = new LinkedHashMap<>();
        benches.put("base", () -> 1);
        benches.put("cand", () -> 1);
        return PerfBudgetGateDemo.evaluate(harness, benches, List.of(new PerfBudgetGateDemo.Budget("cand", "base", maxRatio)), 0.5);
    }

    @Test
    void passesWithinBudget() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {150, 150, 150, 150, 150}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.PASS, r.verdicts().get("cand"));
    }

    @Test
    void failsOverBudget() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {250, 250, 250, 250, 250}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.FAIL, r.verdicts().get("cand"));
    }

    @Test
    void noisyMeasurementsNearTheBudgetAreInconclusive() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {20, 400, 30, 380, 150}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.INCONCLUSIVE, r.verdicts().get("cand"));
    }

    @Test
    void hugeRegressionsFailEvenWhenNoisy() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {5_000, 90_000, 6_000, 80_000, 7_000}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.FAIL, r.verdicts().get("cand"));
    }

    @Test
    void statsUseMedianAndP90() {
        var r = run(new long[] {10, 20, 30, 40, 1000}, new long[] {10, 10, 10, 10, 10}, 2.0);
        var s = r.stats().get("base");
        assertEquals(30.0, s.medianNanos());
        assertEquals(1000.0, s.p90Nanos());
        assertEquals(10.0, s.minNanos());
    }

    @Test
    void budgetBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerfBudgetGateDemo.Budget("a", "b", 0.9));
    }
}
```

## 15. Observability & Operations

In production, use **JFR** (continuous, ~1% overhead) for allocation, lock and method profiling, and **async-profiler** for CPU flame graphs that include JIT-compiled frames. Watch GC metrics (allocation rate, pause times), and JIT events (`jdk.Deoptimization`, compilation failures). Track startup time per deploy. Compare flame graphs before and after refactorings that touch hot paths.

## 16. Version Notes

| Java | Change relevant to pattern performance |
|------|----------------------------------------|
| 6u23 | Escape analysis on by default |
| 7 | `invokedynamic`, `MethodHandle` |
| 8 | Lambdas via `LambdaMetafactory` |
| 9 | AppCDS for application classes (commercial in 8) |
| 12+ | Default CDS archive |
| 18 | Core reflection reimplemented on method handles (JEP 416) |
| 21 | Virtual threads; generational ZGC |
| 24 | Ahead-of-time class loading and linking (JEP 483, Project Leyden) |
| 25 | AOT method profiling (JEP 515), compact object headers (JEP 519) |

## 17. Expert Deep Dive

- **Profile pollution.** Type profiles are per bytecode, not per caller; a shared helper called with many types becomes megamorphic for everyone. Inlining the helper into callers (by making it small) gives each caller its own profile.
- **Class hierarchy analysis (CHA).** If only one class implements an interface at the time of compilation, the JIT can inline without a type check; loading another implementation later triggers deoptimisation.
- **Partial escape analysis.** GraalVM's compiler removes allocations on paths where an object doesn't escape, even if it escapes elsewhere; it often beats C2 on pattern-heavy code (Scala, Kotlin, streams).
- **Valhalla.** Value classes (preview builds) will let `Money`-style objects be flattened in arrays and fields, removing the "stored objects allocate" cost.
- **Startup vs peak.** CDS, Leyden AOT caches and CRaC checkpoints attack startup without giving up the JIT's peak performance; native images trade some peak throughput for instant start.

> 📖 **War story: the 11-minute repricing job**  
> FreshCart's nightly repricing job took 4 minutes. A team added `@Cacheable` to the price lookup and `@Timed` for metrics, both on a method called 50 million times per run. The job took 11 minutes and allocated 40 GB more. JFR showed most time in proxy dispatch, cache-key construction (`SimpleKey` with boxed arguments) and timer recording, all around a method that did a few arithmetic operations on in-memory data. **Fixes:** caching and timing moved to the per-batch method (one call per 10,000 lines); the inner loop called the implementation directly; a JMH benchmark and a relative budget were added to CI. The job dropped to 3.5 minutes. **Lesson:** proxies are cheap per call for real work, and expensive in bulk for trivial work; put cross-cutting concerns at the right granularity.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Are interface calls slow in Java?** Usually **no**: the JIT **inlines** them.
2. **What's boxing?** Wrapping a primitive in an **object** (`long` → `Long`).
3. **Why benchmark with JMH?** It handles **warm-up, dead-code elimination, forks and statistics**.
4. **What's JIT compilation?** Compiling **hot bytecode to machine code** at runtime.
5. **Does DI slow down startup?** **Reflective** DI can; compile-time DI avoids it.

**▸ 🟡 L3**

1. **What's a megamorphic call site?** One that sees **3+ receiver types**: **no inlining**.
2. **What's escape analysis?** Removing allocations of objects that **don't escape** a compiled method.
3. **Why can a benchmark show impossible numbers?** **Dead-code elimination or constant folding.**
4. **Proxy vs MethodHandle vs reflection cost?** **Reflection slowest; MethodHandle near direct when constant.**
5. **When does pattern overhead matter?** **Tiny methods in hot loops**, not around I/O.

**▸ 🔴 L4–L5**

1. **A team wants to remove all interfaces for speed; how do you respond?** **Measure with JMH/JFR; show inlining; optimise only hot spots.**
2. **How do you reduce startup for a Spring service on serverless?** **AOT/native image, CDS/Leyden, CRaC/SnapStart, fewer beans.**
3. **How do you design performance regression tests in CI?** **JMH on stable runners, relative budgets, multiple forks, noise handling.**
4. **Why can a microbenchmark mislead about production?** **Profile pollution, different data shapes, GC and cache effects.**
5. **How would Valhalla change pattern costs?** **Value objects flattened; no allocation for stored values.**

**▸ 💻 Hands-on**

**Task:** In `PricingHotPathDemo`, keep four chain shapes but make each call site monomorphic again, without removing the Decorator pattern.

> ✅ **Solution**  
> ```java
> // Process each shape in its own loop method so each has its own call-site profile.
> static long runShape0(Price p, long[] lines) { long s = 0; for (long l : lines) s += p.of(l); return s; }
> static long runShape1(Price p, long[] lines) { long s = 0; for (long l : lines) s += p.of(l); return s; }
> // ... one per shape, or group input lines by shape and dispatch once per batch:
> long total = 0;
> for (Price p : variants) total += batchFor(p).run(p, lines);   // batchFor returns a dedicated loop per shape
> // Re-measure: each loop is monomorphic, so the chain inlines again.
> ```

## 19. Practice

- [ ] Run the JMH project with `-prof gc` and compare `gc.alloc.rate.norm` for each benchmark.
    > 💡 **Hint**  
    > `java -jar target/benchmarks.jar -prof gc`.

- [ ] Add a `@Benchmark` for a `Stream`-based version of the pricing loop and compare.
    > 💡 **Hint**  
    > `Arrays.stream(lines).map(chain::of).sum()`.

- [ ] Record a JFR file for `PricingHotPathDemo` and look at the hottest methods.
    > 💡 **Hint**  
    > `java -XX:StartFlightRecording=duration=30s,filename=rec.jfr PricingHotPathDemo.java`, then open in JDK Mission Control.

- [ ] Measure Spring Boot startup of a sample app with and without `spring.main.lazy-initialization=true`.
    > 💡 **Hint**  
    > Look at the "Started ... in X seconds" log line, averaged over several runs.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Mono/bimorphic calls inline (free); megamorphic hot sites cost 3–5x per call.
> - Short-lived objects are often scalar-replaced; stored, boxed and loop-carried ones aren't.
> - Proxies and reflection: fine around I/O, costly in tight loops; prefer `MethodHandle`s.
> - Reflective DI costs startup; AOT/compile-time DI and CDS help.
> - Measure with JMH (forks, warm-up, Blackhole); profile with JFR/async-profiler.
> - Keep the pattern; optimise only the measured hot path.
>
> ```java
> @Benchmark
> public long decoratorChain() {
>     return chain.of(subtotal);   // return the result so the JIT can't discard the work
> }
> ```

## 21. Further Reading

- [JMH samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples), the best tutorial on benchmarking pitfalls.
- Aleksey Shipilёv, ["The Black Magic of (Java) Method Dispatch"](https://shipilev.net/blog/2015/black-magic-method-dispatch/).
- Scott Oaks, *Java Performance* (2nd ed., 2020).
- [OpenJDK Project Leyden](https://openjdk.org/projects/leyden/) and [Spring AOT and native images](https://docs.spring.io/spring-boot/reference/packaging/native-image/index.html).
- [async-profiler](https://github.com/async-profiler/async-profiler) and [JDK Flight Recorder](https://docs.oracle.com/en/java/javase/21/jfapi/).

---

[← Previous: Refactoring to Patterns](refactoring-to-patterns.md) · [Next: L4 Senior / Lead →](../l4-senior/index.md)
