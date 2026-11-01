---
tags: [reference]
---
# Decision Guide

!!! abstract "TL;DR"
    "Which pattern when?" as decision trees. Start from the **problem you have**, not the pattern you'd like to use, and always check the first branch: maybe a plain method, a lambda or a record is enough. Each leaf links to the deep dive.

**How to use these trees.** Answer the questions honestly for the code in front of you today. If two leaves seem to fit, read the [Pattern Comparisons](comparisons.md) for that group. If you're unsure whether a pattern pays for itself, use [Choosing Patterns and Trade-offs](../l5-staff-principal/pattern-selection.md).

## Do I need a pattern at all?

```mermaid
flowchart TD
    Q0["Is there real variation or change pressure today?"] -- No --> KISS["Write plain code. YAGNI"]
    Q0 -- Yes --> Q1["Has the same kind of change happened 3 times?"]
    Q1 -- No --> SMALL["Use the smallest tool: a method, a lambda, an enum"]
    Q1 -- Yes --> Q2["Is the change about creating, structuring or behaving?"]
    Q2 -- Creating --> CRE["See: creating objects"]
    Q2 -- Structuring --> STR["See: combining objects"]
    Q2 -- Behaving --> BEH["See: varying behaviour"]
```

## Creating objects

```mermaid
flowchart TD
    C0["What is hard about creating it?"] --> C1["Many optional fields or validation across fields"]
    C1 --> BUILDER["Builder, or a record with a compact constructor"]
    C0 --> C2["Which class to create depends on input"]
    C2 --> FM["Factory Method or a static factory"]
    C0 --> C3["A whole family must match, like one partner's client and parser"]
    C3 --> AF["Abstract Factory"]
    C0 --> C4["Setup is expensive, new objects are variations"]
    C4 --> PROTO["Prototype"]
    C0 --> C5["It needs collaborators"]
    C5 --> DI["Dependency Injection"]
    C0 --> C6["Only one may ever exist"]
    C6 --> SING["DI singleton scope, or an enum Singleton"]
    C0 --> C7["Creating it is very slow, like a connection"]
    C7 --> POOL["Object Pool, usually from a library"]
```

Links: [Builder](../l1-foundations/builder.md) · [Factory Method](../l1-foundations/factory-method.md) · [Abstract Factory](../l2-practitioner/abstract-factory.md) · [Prototype](../l2-practitioner/prototype.md) · [Dependency Injection](../l2-practitioner/dependency-injection.md) · [Singleton](../l1-foundations/singleton.md) · [Object Pool](../l2-practitioner/object-pool.md)

## Combining objects

```mermaid
flowchart TD
    S0["What do you need from the wrapper or structure?"] --> S1["Use a class whose interface does not fit"]
    S1 --> ADAPT["Adapter"]
    S0 --> S2["One simple entry point to many services"]
    S2 --> FACADE["Facade"]
    S0 --> S3["Add optional, stackable behaviour"]
    S3 --> DECO["Decorator"]
    S0 --> S4["Control access, lazy load, or call remotely"]
    S4 --> PROXY["Proxy"]
    S0 --> S5["Treat items and groups the same way"]
    S5 --> COMP["Composite"]
    S0 --> S6["Two dimensions that vary independently"]
    S6 --> BRIDGE["Bridge"]
    S0 --> S7["Millions of objects share most of their state"]
    S7 --> FLY["Flyweight"]
```

Links: [Adapter](../l1-foundations/adapter.md) · [Facade](../l1-foundations/facade.md) · [Decorator](../l1-foundations/decorator.md) · [Proxy](../l2-practitioner/proxy.md) · [Composite](../l2-practitioner/composite.md) · [Bridge](../l2-practitioner/bridge.md) · [Flyweight](../l2-practitioner/flyweight.md)

## Varying behaviour

```mermaid
flowchart TD
    B0["What varies?"] --> B1["The whole algorithm, chosen from outside"]
    B1 --> STRAT["Strategy, often a lambda"]
    B0 --> B2["Behaviour depends on the object's current status"]
    B2 --> STATE["State, or a sealed interface plus switch"]
    B0 --> B3["Some steps of a fixed process"]
    B3 --> TM["Template Method"]
    B0 --> B4["Others must react when something happens"]
    B4 --> OBS["Observer, or messaging across services"]
    B0 --> B5["A request must be queued, logged, retried or undone"]
    B5 --> CMD["Command"]
    B0 --> B6["A request passes through several checks"]
    B6 --> COR["Chain of Responsibility"]
    B0 --> B7["New operations over a fixed set of types"]
    B7 --> VIS["Pattern-matching switch, or Visitor before Java 21"]
    B0 --> B8["Business rules must be combined"]
    B8 --> SPEC["Specification"]
    B0 --> B9["Many components talk to each other"]
    B9 --> MED["Mediator"]
```

Links: [Strategy](../l1-foundations/strategy.md) · [State](../l2-practitioner/state.md) · [Template Method](../l1-foundations/template-method.md) · [Observer](../l1-foundations/observer.md) · [Command](../l2-practitioner/command.md) · [Chain of Responsibility](../l2-practitioner/chain-of-responsibility.md) · [Visitor](../l2-practitioner/visitor.md) · [Specification](../l2-practitioner/specification.md) · [Mediator](../l2-practitioner/mediator.md)

## Calling another service

```mermaid
flowchart TD
    R0["Calling a remote dependency"] --> R1["Always set a timeout"]
    R1 --> R2{"Are failures sometimes transient?"}
    R2 -- Yes --> R3{"Is the operation idempotent or keyed?"}
    R3 -- Yes --> RETRY["Retry with backoff and jitter"]
    R3 -- No --> KEY["Add an idempotency key first"]
    R2 -- No --> R4["Skip retries"]
    RETRY --> R5{"Can it fail for minutes?"}
    R4 --> R5
    R5 -- Yes --> CB["Circuit breaker"]
    R5 -- No --> R6{"Could it use all your threads?"}
    CB --> R6
    R6 -- Yes --> BULK["Bulkhead"]
    R6 -- No --> R7{"Is there a useful degraded answer?"}
    BULK --> R7
    R7 -- Yes --> FB["Fallback"]
    R7 -- No --> ERR["Fail fast with a clear error"]
```

Links: [Resilience Patterns](../l4-senior/resilience-patterns.md) · [API Design Patterns](../l4-senior/api-design-patterns.md)

## Keeping data consistent across services

```mermaid
flowchart TD
    D0["A change spans more than one service"] --> D1{"Can it live in one service and database?"}
    D1 -- Yes --> TX["Use a local transaction. Rethink the boundary"]
    D1 -- No --> D2{"Must you publish an event after saving?"}
    D2 -- Yes --> OUTBOX["Transactional outbox or CDC"]
    D2 -- No --> D3
    OUTBOX --> D3{"Several steps, each can fail?"}
    D3 -- Yes --> SAGA["Saga with compensations"]
    D3 -- No --> D4
    SAGA --> D4{"Reads need a different shape than writes?"}
    D4 -- Yes --> CQRS["CQRS read model"]
    D4 -- No --> DONE["Keep one model"]
    CQRS --> D5{"Is the full history the business record?"}
    D5 -- Yes --> ES["Event sourcing"]
    D5 -- No --> DONE2["Store current state plus an outbox"]
```

Every consumer of those events should be an **idempotent consumer**. Links: [Distributed Data Patterns](../l4-senior/distributed-data-patterns.md) · [Messaging Patterns](../l4-senior/messaging-patterns.md)

## Caching

```mermaid
flowchart TD
    K0["Reads are slow or expensive"] --> K1{"Is some staleness acceptable?"}
    K1 -- No --> NOCACHE["Optimise the query or add a read replica"]
    K1 -- Yes --> K2{"Is it the same process only?"}
    K2 -- Yes --> LOCAL["Local cache like Caffeine with size and TTL"]
    K2 -- No --> SHARED["Shared cache like Redis, cache-aside"]
    LOCAL --> K3{"Hot keys expire under load?"}
    SHARED --> K3
    K3 -- Yes --> REFRESH["Refresh-ahead or single-flight loading"]
    K3 -- No --> K4{"Writes must update the cache?"}
    REFRESH --> K4
    K4 -- Yes --> INVAL["Invalidate on write, or write-through"]
    K4 -- No --> TTL["TTL is enough"]
```

Links: [Caching Patterns](../l4-senior/caching-patterns.md)

## Structuring the system

```mermaid
flowchart TD
    A0["How many teams will work on it in a year?"] --> A1["One team"]
    A1 --> A2{"Rich business rules?"}
    A2 -- Yes --> HEX["Hexagonal modules in one deployable"]
    A2 -- No --> LAYER["Simple layered app"]
    A0 --> A3["Several teams"]
    A3 --> A4{"Clear bounded contexts and independent release needs?"}
    A4 -- No --> MODMONO["Modular monolith with enforced boundaries"]
    A4 -- Yes --> A5{"Can you run distributed systems: tracing, on-call, CI per service?"}
    A5 -- No --> MODMONO
    A5 -- Yes --> MICRO["Microservices along bounded contexts"]
```

Links: [Architectural Patterns](../l4-senior/architectural-patterns.md) · [DDD Strategic](../l4-senior/ddd-strategic.md) · [Microservice Patterns](../l4-senior/microservice-patterns.md) · [Evolutionary Architecture](../l5-staff-principal/evolutionary-architecture.md)

## Modernising legacy code

```mermaid
flowchart TD
    L0["Legacy system must change"] --> L1{"Do you have tests around the behaviour?"}
    L1 -- No --> CHAR["Add characterisation tests and seams first"]
    L1 -- Yes --> L2
    CHAR --> L2{"Can you route traffic per feature?"}
    L2 -- Yes --> STRANGLER["Strangler fig"]
    L2 -- No --> BBA["Branch by abstraction inside the code"]
    STRANGLER --> L3{"Is the new result risky?"}
    BBA --> L3
    L3 -- Yes --> PARALLEL["Parallel run and compare"]
    L3 -- No --> TOGGLE["Feature toggle rollout"]
    PARALLEL --> TOGGLE
```

Links: [Legacy Modernisation Patterns](../l5-staff-principal/legacy-modernisation.md) · [Refactoring to Patterns](../l3-professional/refactoring-to-patterns.md)
