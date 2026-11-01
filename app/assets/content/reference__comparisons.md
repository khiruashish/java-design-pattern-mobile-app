# Pattern Comparisons

> 📌 **TL;DR**  
> Many patterns look alike in a class diagram and differ only in **intent**. This page puts the commonly confused ones side by side, with a decision table for each group, so you can name the pattern you need and explain why not the others.

**The one question that separates look-alikes:** *what is the wrapper or the indirection for?* Converting an interface, simplifying a subsystem, adding behaviour or controlling access all produce "a class that holds another class".

## Wrappers: Adapter, Facade, Decorator, Proxy

| | [Adapter](../l1-foundations/adapter.md) | [Facade](../l1-foundations/facade.md) | [Decorator](../l1-foundations/decorator.md) | [Proxy](../l2-practitioner/proxy.md) |
|-|---------|--------|-----------|-------|
| Intent | Convert an interface | Simplify a subsystem | Add behaviour | Control access |
| Interface vs wrapped object | Different | New, simpler | Same | Same |
| Wraps | One object | Many objects | One object (stackable) | One object |
| Who creates the wrapped object | Caller | Facade | Caller | Often the proxy (lazy) |
| FreshCart example | `ParcelGoAdapter` | `CheckoutFacade` | `CachingPriceService` | Lazy product image, Spring `@Transactional` |

| If you need to… | Choose |
|-----------------|--------|
| use a partner SDK through your own interface | Adapter |
| give callers one method for a multi-step process | Facade |
| add logging, caching or retries without changing the class | Decorator |
| delay creation, check permissions, or call remotely | Proxy |

## Families of algorithms: Strategy, State, Template Method, Command

| | [Strategy](../l1-foundations/strategy.md) | [State](../l2-practitioner/state.md) | [Template Method](../l1-foundations/template-method.md) | [Command](../l2-practitioner/command.md) |
|-|----------|-------|-----------------|---------|
| Varies | The whole algorithm | Behaviour per lifecycle state | Steps of a fixed algorithm | Which request is executed |
| Chosen by | Caller or configuration | The object itself, as state changes | Subclass | Whoever creates the command |
| Mechanism | Composition | Composition, states know transitions | Inheritance | Object holding a request |
| Lifetime | Usually long-lived | Replaced on transitions | Fixed per subclass | Created per request, can be queued |
| Example | Delivery fee rules | Order: placed → packed → delivered | Report jobs | Cart edits with undo |

| If… | Choose |
|-----|--------|
| the rule changes by city or campaign | Strategy |
| allowed actions depend on the current status | State |
| the process is fixed and only steps vary | Template Method (or Strategy if you prefer composition) |
| you need to queue, retry, log or undo requests | Command |

## Creation: Factory Method, Abstract Factory, Builder, Prototype, DI

| | [Factory Method](../l1-foundations/factory-method.md) | [Abstract Factory](../l2-practitioner/abstract-factory.md) | [Builder](../l1-foundations/builder.md) | [Prototype](../l2-practitioner/prototype.md) | [DI](../l2-practitioner/dependency-injection.md) |
|-|----|----|----|----|----|
| Creates | One product | A family of products | One complex product | Copies of an existing object | Whole object graphs |
| Main problem solved | Which class? | Which family? | Too many parameters | Expensive or templated setup | Wiring |
| Java idiom | Static factory, `switch` | Interface with several `create` methods | Fluent builder, records with compact constructors | Copy constructor, `with` methods | Constructor injection |

| If… | Choose |
|-----|--------|
| the concrete class depends on input | Factory Method |
| several objects must come from the same vendor or theme | Abstract Factory |
| an object has many optional fields or cross-field validation | Builder |
| new objects start as variations of a template | Prototype |
| objects need collaborators supplied | Dependency Injection |

## Notification and coordination: Observer, Mediator, Pub/Sub, Chain of Responsibility

| | [Observer](../l1-foundations/observer.md) | [Mediator](../l2-practitioner/mediator.md) | [Pub/Sub](../l4-senior/messaging-patterns.md) | [Chain of Responsibility](../l2-practitioner/chain-of-responsibility.md) |
|-|----------|----------|---------|-------------------------|
| Topology | One subject, many listeners | Hub between peers | Broker between services | Linear pipeline |
| Coupling | Listeners know the subject | Peers know only the mediator | Only the topic and message | Handlers know the next handler or the chain |
| Process boundary | In-process | In-process | Across processes | In-process |
| Delivery | Synchronous by default | Synchronous | Asynchronous, durable | Synchronous, stops when handled |

## Trees and wrappers: Composite vs Decorator, Visitor vs pattern matching

| | [Composite](../l2-practitioner/composite.md) | [Decorator](../l1-foundations/decorator.md) |
|-|-----------|-----------|
| Children | Many | Exactly one |
| Purpose | Treat groups like items | Add behaviour |
| Example | Bundles of products | Caching price service |

| | [Visitor](../l2-practitioner/visitor.md) | Pattern-matching `switch` (Java 21) |
|-|---------|----------------------------------|
| Adding a new operation | New visitor class | New method with a `switch` |
| Adding a new type | Change every visitor | Compiler flags non-exhaustive switches over sealed types |
| Boilerplate | `accept` methods everywhere | None |
| Best for | Pre-Java 21 code, double dispatch in libraries | Sealed hierarchies in modern Java ([Patterns in Modern Java](../l3-professional/patterns-in-modern-java.md)) |

## Singletons: classic Singleton vs DI-managed singleton

| | Classic Singleton | DI container singleton |
|-|-------------------|------------------------|
| Access | Global static | Injected |
| Testability | Hard to replace | Easy (constructor parameter) |
| Lifecycle | Class loading | Container start and stop |
| Use for | JDK-level registries, enums | Application services |

## Resilience: Timeout, Retry, Circuit Breaker, Bulkhead, Rate Limiter, Fallback

| Pattern | Protects against | Without it |
|---------|------------------|------------|
| Timeout | Slow dependencies | Threads wait forever |
| Retry | Transient failures | Users see errors that a second try would fix |
| Circuit breaker | Persistent failures | Every call waits for a dead dependency |
| Bulkhead | One dependency using all threads | One slow partner takes down everything |
| Rate limiter | Exceeding a quota | The partner throttles or bans you |
| Fallback | Total failure | No answer at all |

Order them as **fallback( circuit breaker( retry( timeout( call ) ) ) )**, with the bulkhead around the call. See [Resilience Patterns](../l4-senior/resilience-patterns.md).

## Distributed data: 2PC, Saga, Outbox, CQRS, Event Sourcing

| | Two-phase commit | Saga | Transactional outbox | CQRS | Event sourcing |
|-|------------------|------|----------------------|------|----------------|
| Solves | Atomic commit across resources | Long business transactions across services | Reliable "save and publish" | Different read and write models | Full history as the source of truth |
| Consistency | Strong | Eventual, with compensations | Eventual | Eventual between models | Strong per stream |
| Cost | Locks, coordinator availability | Compensation logic | Relay process | Two models | Event schema evolution, projections |
| Use when | Rarely, inside one database cluster | Orders spanning payment, stock, delivery | Every event from a service with a database | Read-heavy screens with complex queries | Audit-critical domains |

**Saga styles:** orchestration (a coordinator tells each step what to do; easier to follow) vs choreography (services react to each other's events; fewer central parts, harder to see the whole flow). See [Distributed Data Patterns](../l4-senior/distributed-data-patterns.md).

## Caching strategies

| | Cache-aside | Read-through | Write-through | Write-behind |
|-|-------------|--------------|---------------|--------------|
| Who loads | Application | Cache | Cache | Cache |
| Write path | Write DB, then invalidate | n/a | Write cache and DB together | Write cache, DB later |
| Staleness | Until invalidated or TTL | TTL | Low | Low in cache, DB lags |
| Risk | Stampedes, stale entries | Hidden latency | Slower writes | Data loss on crash |

See [Caching Patterns](../l4-senior/caching-patterns.md).

## Architecture styles

| | Layered | Hexagonal / clean | Modular monolith | Microservices |
|-|---------|-------------------|------------------|---------------|
| Dependency direction | Top to bottom | Inward, to the domain | Between modules through APIs | Between services through networks |
| Deployment | One unit | One unit | One unit | Many |
| Team scaling | Low | Low to medium | Medium to high | High |
| Operational cost | Low | Low | Low | High |
| Start here when | Simple CRUD | Rich domain logic | Most new products | Many teams with clear boundaries |

See [Architectural Patterns](../l4-senior/architectural-patterns.md) and [Microservice Patterns](../l4-senior/microservice-patterns.md).
