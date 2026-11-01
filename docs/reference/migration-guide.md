---
tags: [reference]
---
# Migration Guide

!!! abstract "TL;DR"
    How to move pattern implementations from older Java and framework styles to modern Java 21 idioms: anonymous classes to lambdas, Visitor to sealed types with pattern matching, hand-written value classes to records, field injection to constructor injection, and older libraries (Hystrix, Guava Cache, `java.util.Observable`) to their maintained replacements. Each migration lists the safe steps.

**General approach.** Migrate one pattern at a time, behind tests. Write characterisation tests first if there are none, change the implementation, keep the public API until callers move, then remove the old code. See [Refactoring to Patterns](../l3-professional/refactoring-to-patterns.md) and [Legacy Modernisation](../l5-staff-principal/legacy-modernisation.md).

## Java language migrations

| From | To | Since | Pattern pages |
|------|----|-------|---------------|
| Anonymous inner classes for one-method interfaces | Lambdas and method references | Java 8 | [Strategy](../l1-foundations/strategy.md), [Command](../l2-practitioner/command.md) |
| Hand-written immutable classes (fields, constructor, `equals`, `hashCode`, `toString`) | Records | Java 16 | [Builder](../l1-foundations/builder.md), [DDD Tactical](../l4-senior/ddd-tactical.md) |
| Visitor with `accept` methods | Sealed interfaces plus pattern-matching `switch` | Java 21 | [Visitor](../l2-practitioner/visitor.md) |
| `instanceof` then cast | Pattern matching for `instanceof` | Java 16 | [Patterns in Modern Java](../l3-professional/patterns-in-modern-java.md) |
| Type-code constants with `switch` statements | Enums with behaviour, or sealed types | Java 5 / 17 | [State](../l2-practitioner/state.md) |
| Double-checked locking singletons | Holder idiom or enum | Java 5 | [Singleton](../l1-foundations/singleton.md) |
| `java.util.Date` / `Calendar` | `java.time` | Java 8 | [Testing Patterns](../l2-practitioner/testing-patterns.md) (injectable `Clock`) |
| Platform thread pools for blocking I/O | Virtual threads | Java 21 | [Concurrency Patterns](../l3-professional/concurrency-patterns.md) |
| `finalize()` | `Cleaner` or try-with-resources | Java 9 | [Object Pool](../l2-practitioner/object-pool.md) |
| `java.util.Observable` / `Observer` (deprecated in 9) | Your own listener interface, `java.util.concurrent.Flow`, or events | Java 9 | [Observer](../l1-foundations/observer.md) |
| `sun.misc.Unsafe` field access | `VarHandle` | Java 9 | [JVM Internals](../l5-staff-principal/jvm-internals-behind-patterns.md) |

### Visitor to sealed types and pattern matching

1. Make the element hierarchy `sealed` with `permits` for every existing subtype.
2. For one visitor, write a method with an exhaustive `switch` over the sealed type that calls the same logic.
3. Point callers at the new method; run tests.
4. Repeat per visitor, then delete the `accept` methods and the visitor interface.

```java
// before: price.accept(new TaxVisitor())
long tax(BasketItem item) {
    return switch (item) {
        case Product p -> p.pricePaise() * 5 / 100;
        case Bundle b -> b.items().stream().mapToLong(this::tax).sum();
    };
}
```

### Value classes to records

1. Check that the class is effectively immutable and has no identity-based behaviour.
2. Replace it with a record; move validation into the compact constructor.
3. Replace getter calls (`getSku()`) with accessor calls (`sku()`), or keep getters temporarily as extra methods.
4. Watch for frameworks that need no-arg constructors (older JPA); records fit DTOs and value objects, not JPA entities.

## Framework migrations

| From | To | Notes |
|------|----|-------|
| Field injection (`@Autowired` on fields) | Constructor injection | Final fields, testable without the container ([Dependency Injection](../l2-practitioner/dependency-injection.md)) |
| Spring XML configuration | Java `@Configuration` and component scanning | Type-checked wiring |
| `javax.*` packages | `jakarta.*` | Required by Spring Boot 3 and Jakarta EE 9+; OpenRewrite automates most of it |
| Netflix Hystrix (maintenance mode since 2018) | Resilience4j | Map commands to decorators; check different defaults ([Resilience Patterns](../l4-senior/resilience-patterns.md)) |
| Guava `CacheBuilder` | Caffeine | Near-identical builder API, better hit rates ([Caching Patterns](../l4-senior/caching-patterns.md)) |
| Spring Cloud Netflix Ribbon / Zuul | Spring Cloud LoadBalancer / Spring Cloud Gateway | Ribbon and Zuul 1 are in maintenance ([Microservice Patterns](../l4-senior/microservice-patterns.md)) |
| `RestTemplate` | `RestClient` (Spring 6.1) or `WebClient` | `RestTemplate` is in maintenance mode; set timeouts explicitly |
| JUnit 4 | JUnit 5 (Jupiter) | Vintage engine lets both run during migration ([Testing Patterns](../l2-practitioner/testing-patterns.md)) |

### Hystrix to Resilience4j

1. List each `HystrixCommand` with its timeout, thread pool size and fallback.
2. Create a Resilience4j `TimeLimiter`, `CircuitBreaker` and `Bulkhead` per dependency with equivalent settings; Hystrix used a 10-second rolling window and 50% error threshold by default, so set those explicitly rather than relying on Resilience4j defaults.
3. Wrap the call with decorators in the order fallback, circuit breaker, retry, time limiter.
4. Compare metrics in a canary before switching all traffic.

## Architecture migrations

| From | To | Approach |
|------|----|----------|
| Monolith | Modular monolith | Enforce module boundaries with ArchUnit or JPMS first ([Evolutionary Architecture](../l5-staff-principal/evolutionary-architecture.md)) |
| Modular monolith | Microservices | Extract one bounded context at a time with a strangler fig ([Microservice Patterns](../l4-senior/microservice-patterns.md)) |
| Dual writes | Transactional outbox | Add the outbox table, write to it in the same transaction, run a relay, then remove direct publishing ([Distributed Data Patterns](../l4-senior/distributed-data-patterns.md)) |
| Shared database | Database per service | Expand-and-contract, CDC to sync during transition, then cut access |
| In-house API v1 | v2 alongside v1 | Version, publish a deprecation schedule, track usage, then retire ([API Design Patterns](../l4-senior/api-design-patterns.md)) |

## Tools that automate migrations

| Tool | Helps with |
|------|------------|
| OpenRewrite recipes | Java version upgrades, `javax` to `jakarta`, JUnit 4 to 5, Spring Boot upgrades |
| IntelliJ IDEA inspections | Anonymous class to lambda, pattern matching, records |
| `jdeprscan` | Finds uses of deprecated JDK APIs |
| `jdeps --jdk-internals` | Finds uses of JDK internal APIs before upgrading |
| ArchUnit | Proves the new boundaries hold after the migration |
