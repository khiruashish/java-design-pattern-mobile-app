---
tags: [reference]
---
# Anti-Pattern Catalogue

!!! abstract "TL;DR"
    Every anti-pattern mentioned in the playbook, in one place: how to **recognise** it, why it **hurts**, and the **fix**, with a link to the page that explains it in depth. Use it as a review checklist.

**How to use it.** Scan the "Symptom" column during code or design review. An anti-pattern is a common response to a real problem that causes more harm than it removes, so each fix names the pattern or practice that solves the original problem better.

## Code-level smells

| Anti-pattern | Symptom | Why it hurts | Fix | Read more |
|--------------|---------|--------------|-----|-----------|
| God class | `CheckoutService` with 3,000 lines and 40 dependencies | Every change conflicts; nobody understands it | Extract classes by responsibility, Facade for callers | [Anti-Patterns and Code Smells](../l3-professional/anti-patterns-and-code-smells.md) |
| Long parameter list | `quote(a, b, c, d, e, f, g)` | Wrong argument order, hard to evolve | Parameter object or Builder | [Builder](../l1-foundations/builder.md) |
| Primitive obsession | Prices as `double`, SKUs as `String` everywhere | Rounding bugs, mixed-up values | Value objects (records) such as `Money`, `Sku` | [DDD Tactical](../l4-senior/ddd-tactical.md) |
| Switch on type everywhere | The same `switch (type)` in many classes | Every new type means editing many places | Strategy or polymorphism, or a sealed type with one exhaustive switch | [Strategy](../l1-foundations/strategy.md) |
| Feature envy | A method uses another object's data more than its own | Logic in the wrong place | Move the method to the data | [Refactoring to Patterns](../l3-professional/refactoring-to-patterns.md) |
| Shotgun surgery | One change touches ten files | Slow, risky changes | Gather the knowledge into one class or module | [SOLID Principles](../l1-foundations/solid-principles.md) |
| Null returns everywhere | `if (x != null)` chains | NullPointerExceptions, noise | `Optional` for return values, Null Object for collaborators | [Null Object](../l2-practitioner/null-object.md) |
| Swallowed exceptions | `catch (Exception e) {}` | Silent data corruption | Handle, translate or rethrow; log once at the boundary | [Error Handling Patterns](../l3-professional/error-handling-patterns.md) |
| Exceptions for control flow | `try { parse } catch` to test validity | Slow, unclear | Validation methods, result types | [Error Handling Patterns](../l3-professional/error-handling-patterns.md) |

## Pattern misuse

| Anti-pattern | Symptom | Why it hurts | Fix | Read more |
|--------------|---------|--------------|-----|-----------|
| Patternitis | Factory, Strategy and Visitor for a feature with one variant | Indirection without benefit | Plain code until the third variation (rule of three) | [Choosing Patterns](../l5-staff-principal/pattern-selection.md) |
| Singleton as a global variable | Mutable state reachable from anywhere | Hidden coupling, untestable, thread-safety bugs | Inject a DI-managed instance; keep singletons immutable | [Singleton](../l1-foundations/singleton.md) |
| Service locator | `ctx.getBean(X.class)` inside business code | Hidden dependencies, runtime failures | Constructor injection | [Dependency Injection](../l2-practitioner/dependency-injection.md) |
| Anemic domain model | Entities with only getters and setters, all logic in services | Rules duplicated and scattered | Put behaviour and invariants on the aggregate | [DDD Tactical](../l4-senior/ddd-tactical.md) |
| Deep inheritance for reuse | `AbstractBaseOrderServiceImpl` chains | Fragile base class, rigid design | Composition, Strategy, Decorator | [OOP Refresher](../l1-foundations/oop-refresher.md) |
| Observer leaks | Listeners never unregistered | Memory leaks, duplicate notifications | Return a subscription handle; weak references only with care | [Observer](../l1-foundations/observer.md) |
| Decorator order bugs | Cache wraps retry wraps logging in random order | Surprising behaviour (caching errors, double logging) | Compose in one place, test the order | [Decorator](../l1-foundations/decorator.md) |
| Pooling cheap objects | Pools for `StringBuilder` or DTOs | Contention and bugs, slower than allocation | Allocate; pool only expensive resources | [Object Pool](../l2-practitioner/object-pool.md) |
| Self-invocation through proxies | `@Transactional` method called from the same class | Annotation silently ignored | Call through another bean | [JVM Internals](../l5-staff-principal/jvm-internals-behind-patterns.md) |

## Concurrency

| Anti-pattern | Symptom | Why it hurts | Fix | Read more |
|--------------|---------|--------------|-----|-----------|
| Double-checked locking without volatile | Lazy init with two `if (x == null)` checks | Half-built objects seen by other threads | `volatile`, holder idiom, or enum | [Concurrency Patterns](../l3-professional/concurrency-patterns.md) |
| Shared mutable state | Static `HashMap` caches updated from requests | Lost updates, corrupted maps | Immutable snapshots, concurrent collections | [Concurrency Patterns](../l3-professional/concurrency-patterns.md) |
| Unbounded queues and pools | `Executors.newCachedThreadPool()` under load | Memory exhaustion, thread explosion | Bounded pools, back-pressure, virtual threads for I/O | [Performance Cost of Patterns](../l3-professional/performance-of-patterns.md) |
| Blocking inside reactive or event loops | JDBC call in a Netty thread | Whole service stalls | Offload to a bounded executor, or use virtual threads | [Concurrency Patterns](../l3-professional/concurrency-patterns.md) |

## Architecture

| Anti-pattern | Symptom | Why it hurts | Fix | Read more |
|--------------|---------|--------------|-----|-----------|
| Big ball of mud | No visible structure, everything depends on everything | Every change is risky | Modules with enforced boundaries, fitness functions | [Evolutionary Architecture](../l5-staff-principal/evolutionary-architecture.md) |
| Distributed monolith | Services that must deploy together | Microservice cost, monolith coupling | Merge services or decouple via events and contracts | [Microservice Patterns](../l4-senior/microservice-patterns.md) |
| Shared database between services | Two services write the same tables | Hidden coupling, impossible schema changes | Database per service, events or APIs | [Microservice Patterns](../l4-senior/microservice-patterns.md) |
| Entity-based services | `ProductService`, `CustomerService`, `OrderService` with chatty calls | Every use case crosses many services | Decompose by business capability or bounded context | [DDD Strategic](../l4-senior/ddd-strategic.md) |
| Leaky domain | JPA annotations and HTTP types inside domain logic | Domain changes with frameworks | Hexagonal ports and adapters | [Architectural Patterns](../l4-senior/architectural-patterns.md) |
| Big-bang rewrite | "Version 2" developed for a year in parallel | Late, risky cut-over; features frozen | Strangler fig, branch by abstraction | [Legacy Modernisation](../l5-staff-principal/legacy-modernisation.md) |

## Distributed systems

| Anti-pattern | Symptom | Why it hurts | Fix | Read more |
|--------------|---------|--------------|-----|-----------|
| No timeouts | Threads stuck on a slow partner | Cascading outages | Timeouts on every remote call | [Resilience Patterns](../l4-senior/resilience-patterns.md) |
| Retry storms | Every layer retries 3 times | 27× load on a struggling dependency | Retry at one layer, backoff with jitter, retry budgets | [Resilience Patterns](../l4-senior/resilience-patterns.md) |
| Dual writes | Save to DB, then publish to Kafka | Lost or phantom events | Transactional outbox or CDC | [Distributed Data Patterns](../l4-senior/distributed-data-patterns.md) |
| Non-idempotent consumers | Duplicate messages create duplicate refunds | Money and stock errors | Idempotent consumer with a processed-message table | [Messaging Patterns](../l4-senior/messaging-patterns.md) |
| Poison message loops | One bad message blocks a partition forever | Processing halts | Dead letter channel after bounded retries | [Messaging Patterns](../l4-senior/messaging-patterns.md) |
| Cache stampede | Hot key expires and 1,000 requests hit the DB | Database overload | Single-flight loading, refresh-ahead, jittered TTL | [Caching Patterns](../l4-senior/caching-patterns.md) |
| Chatty APIs | Mobile screen makes 12 calls | Slow screens, battery drain | Aggregator or BFF endpoint | [UI and Mobile Patterns](../l3-professional/ui-and-mobile-patterns.md) |
| Breaking API changes in place | Renamed JSON fields in a minor release | Partner integrations fail | Versioning, tolerant readers, expand and contract | [API Design Patterns](../l4-senior/api-design-patterns.md) |

## Organisation and process

| Anti-pattern | Symptom | Why it hurts | Fix | Read more |
|--------------|---------|--------------|-----|-----------|
| Architecture review bottleneck | Weeks-long queue for every design | Teams bypass review | Risk-based review routing | [Governing Patterns](../l5-staff-principal/governing-patterns-at-scale.md) |
| Ivory tower standards | Rules nobody can explain | Ignored standards | Owners, rationale, reference implementations, lint | [Governing Patterns](../l5-staff-principal/governing-patterns-at-scale.md) |
| Metric gaming | 100% review coverage with 1-minute reviews | False confidence | Combined metrics with guards; use them to help | [Governing Patterns](../l5-staff-principal/governing-patterns-at-scale.md) |
| Accidental public API | Teams depend on "internal" classes | Refactors break others | JPMS exports, package-private, ArchUnit | [Designing Frameworks and Libraries](../l5-staff-principal/framework-and-library-design.md) |
| Speculative framework | A generic engine with one user | Harder to change than the code it replaced | Rule of three; inline it back | [Choosing Patterns](../l5-staff-principal/pattern-selection.md) |
