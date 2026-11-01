---
tags: [reference]
---
# Java Version History for Patterns

!!! abstract "TL;DR"
    Java's evolution keeps changing how patterns are written. Generics made factories and iterators type-safe, lambdas turned Strategy and Command into one-liners, records made value objects and builders shorter, sealed types and pattern matching replaced most Visitors, and virtual threads changed concurrency patterns. This page lists, version by version, what changed and why it matters for pattern code.

**How to read it.** Each row is a feature that changed pattern code, with the pattern it affects. LTS versions are marked. The playbook targets **Java 21 (LTS)**; notes on later versions say when something requires them.

## Timeline

| Version (year) | Feature | What it changed for patterns |
|----------------|---------|------------------------------|
| 1.0 (1996) | `java.util.Observable`, `Enumeration` | Observer and Iterator in the platform from day one |
| 1.1 (1997) | Inner classes, event listener model | Anonymous-class Strategies and Commands; Observer in AWT |
| 1.2 (1998) | Collections framework | `Iterator`, `Comparator` as Strategy, unmodifiable views as Proxy |
| 1.3 (2000) | `java.lang.reflect.Proxy` | Dynamic proxies for AOP, remoting and mocks |
| 5 (2004) | Generics, enums, annotations, `java.util.concurrent`, JSR-133 memory model | Type-safe factories; enum Singleton and enum Strategy; annotation-driven frameworks; correct double-checked locking with `volatile` |
| 6 (2006) | Public `ServiceLoader` | SPI and plugin discovery |
| 7 (2011) | try-with-resources, `invokedynamic`, `MethodHandles` | Resource handling without finally blocks; foundation for lambdas |
| 8 LTS (2014) | Lambdas, method references, streams, `Optional`, default methods, `java.time`, `CompletableFuture` | Strategy, Command and Observer as lambdas; Template Method via higher-order functions; interfaces evolve with defaults; injectable `Clock` |
| 9 (2017) | JPMS modules, `Flow` (reactive streams), `VarHandle`, `List.of` factories, `Observable` deprecated | Enforced encapsulation; standard publish/subscribe interfaces; static factories everywhere |
| 10 (2018) | `var` | Less noise around builders and factories |
| 11 LTS (2018) | `HttpClient` | Standard client for adapters to remote services |
| 14 (2020) | Switch expressions | Strategy and State selection as expressions |
| 15 (2020) | Text blocks, hidden classes | Readable templates; lambda and proxy classes as hidden classes |
| 16 (2021) | Records, pattern matching for `instanceof` | Value objects, DTOs and immutable builders' results in one line |
| 17 LTS (2021) | Sealed classes, strong encapsulation of JDK internals | Closed hierarchies for State and Composite; frameworks must stop deep reflection into the JDK |
| 18 (2022) | Core reflection reimplemented with method handles | Faster reflective frameworks |
| 19 / 20 (2022–23) | Previews of virtual threads, record patterns, scoped values | Preparing Java 21 idioms |
| 21 LTS (2023) | Virtual threads, record patterns, pattern matching for `switch`, sequenced collections | Visitor becomes an exhaustive `switch`; thread-per-request is cheap again; fewer reactive pipelines for simple I/O |
| 22 (2024) | Unnamed variables and patterns (`_`), Foreign Function & Memory API | Cleaner pattern-matching code; native adapters without JNI |
| 24 (2025) | Virtual threads no longer pin on `synchronized` (JEP 491), AOT class loading cache (JEP 483), Stream gatherers (JEP 485) | `synchronized` is fine with virtual threads again; faster startup for framework-heavy apps; custom stream stages |
| 25 LTS (2025) | Scoped values (JEP 506), module import declarations (JEP 511), flexible constructor bodies (JEP 513) | Context propagation without `ThreadLocal`; validation before `super(...)` in constructors |

## The same Strategy across Java versions

```java
// Java 1.1 to 7: anonymous class
DeliveryFee fee = new DeliveryFee() {
    public long feePaise(long basket) { return basket >= 50000 ? 0 : 2900; }
};

// Java 8+: lambda
DeliveryFee fee = basket -> basket >= 50000 ? 0 : 2900;

// Java 21: sealed types and pattern matching when the strategy needs data
sealed interface FeeRule permits FreeAbove, Flat {}
record FreeAbove(long thresholdPaise, long feePaise) implements FeeRule {}
record Flat(long feePaise) implements FeeRule {}

long fee(FeeRule rule, long basket) {
    return switch (rule) {
        case FreeAbove(long t, long f) -> basket >= t ? 0 : f;
        case Flat(long f) -> f;
    };
}
```

## Framework versions that changed pattern usage

| Release (year) | Change |
|----------------|--------|
| Spring 2.5 (2007) | Annotation-driven configuration (`@Autowired`, `@Component`) |
| Spring 4.3 (2016) | Implicit constructor injection for single-constructor beans |
| Spring Boot 2.1 (2018) | Bean definition overriding disabled by default |
| Spring Boot 2.6 (2021) | Circular references prohibited by default |
| Spring 6 / Boot 3 (2022) | Java 17 baseline, `jakarta.*`, AOT processing for native images |
| Spring 6.1 / Boot 3.2 (2023) | `RestClient`, virtual thread support |
| Resilience4j 1.0 (2019) | Replacement for Hystrix with functional decorators |
| Caffeine 3.0 (2021) | Java 11 baseline |
| JUnit 5 (2017) | Extension model replacing runners and rules |
| ArchUnit 1.0 (2022) | Stable API for architecture tests |

See [Patterns in Modern Java](../l3-professional/patterns-in-modern-java.md) for side-by-side examples, and the [Migration Guide](migration-guide.md) for upgrade steps.
