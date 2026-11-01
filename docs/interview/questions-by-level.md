---
tags: [interview]
---
# Questions by Level

!!! abstract "TL;DR"
    Graded questions from L1 to L5 with model answers. Try to answer out loud before opening each answer, then follow the link to the page that covers it in depth.

**How to use this page.** Each answer is the core of a good response, about 30 to 60 seconds spoken. In a real interview, add an example from your own work. Topic pages have five more questions per level in their Interview Corner.

## L1 Junior

1. **What is a design pattern, and what is it not?**

    ??? success "Model answer"
        A named, reusable solution to a recurring design problem in a context, described by intent, structure and trade-offs. It is not a library or copy-paste code; you adapt it. Patterns give teams a shared vocabulary: saying "wrap it in a Decorator" communicates a whole design. ([Read more](../l0-orientation/what-are-design-patterns.md))

2. **Explain the Single Responsibility Principle with an example.**

    ??? success "Model answer"
        A class should have one reason to change. If `OrderService` calculates prices, saves orders and sends SMS, a change to SMS templates risks breaking pricing. Split it into `PricingService`, `OrderRepository` and `Notifier`, each owned by the people who change it. ([Read more](../l1-foundations/solid-principles.md))

3. **How do you make a thread-safe Singleton in Java?**

    ??? success "Model answer"
        Use an enum with one constant, or the holder idiom (a nested static class holding the instance). Both rely on class initialisation being thread-safe and lazy. In applications, prefer a DI container's singleton scope so the object stays replaceable in tests. ([Read more](../l1-foundations/singleton.md))

4. **Strategy vs Template Method?**

    ??? success "Model answer"
        Both vary part of an algorithm. Strategy uses composition: the caller passes an object (often a lambda) that implements the varying algorithm. Template Method uses inheritance: a base class fixes the steps and subclasses override some. Prefer Strategy in modern Java; it is easier to test and combine. ([Read more](../l1-foundations/strategy.md))

5. **When would you use a Builder?**

    ??? success "Model answer"
        When an object has many optional parameters, needs validation across fields, or must be immutable once built. For a few required fields, a record or constructor is simpler. ([Read more](../l1-foundations/builder.md))

6. **What problem does Observer solve, and what is a common bug?**

    ??? success "Model answer"
        It lets one object notify many dependents without knowing them. A common bug is forgetting to unregister listeners, which leaks memory and causes duplicate notifications; another is a slow listener blocking the publisher. ([Read more](../l1-foundations/observer.md))

7. **Adapter vs Facade?**

    ??? success "Model answer"
        Adapter converts one interface into another that callers expect (a ParcelGo SDK behind your `DeliveryPartner`). Facade gives a simpler interface to a whole subsystem (one `checkout` method over stock, payment and delivery). ([Read more](../reference/comparisons.md))

## L2 Mid

1. **Decorator vs Proxy: they have the same structure, so what differs?**

    ??? success "Model answer"
        Intent. Decorator adds behaviour and is usually composed by the caller in stacks (logging, caching). Proxy controls access to the real object (lazy loading, security, remote calls, transactions) and often creates or locates it itself. ([Read more](../l2-practitioner/proxy.md))

2. **Why is constructor injection preferred over field injection?**

    ??? success "Model answer"
        Dependencies are explicit and final, objects are always fully initialised, tests can construct them without a container, and circular dependencies fail fast instead of being hidden. ([Read more](../l2-practitioner/dependency-injection.md))

3. **How would you model an order lifecycle?**

    ??? success "Model answer"
        With the State pattern or, in Java 21, a sealed interface of state records whose methods return the next state and throw for illegal transitions. Transitions are tested as a table: from state, action, expected result. ([Read more](../l2-practitioner/state.md))

4. **When is Visitor a good idea, and what replaces it in Java 21?**

    ??? success "Model answer"
        When the type hierarchy is stable and you keep adding operations over it. In Java 21, a sealed hierarchy with exhaustive pattern-matching `switch` gives the same benefit without `accept` methods, and the compiler reports missing cases. ([Read more](../l2-practitioner/visitor.md))

5. **What's the difference between a fake, a stub and a mock?**

    ??? success "Model answer"
        A stub returns canned answers; a mock also verifies interactions; a fake is a working lightweight implementation (an in-memory repository). Prefer fakes for domain logic and mocks only at boundaries where the interaction is the behaviour. ([Read more](../l2-practitioner/testing-patterns.md))

6. **Composite: what makes it tricky?**

    ??? success "Model answer"
        Deciding where child-management methods live (on the component or only the composite), avoiding cycles, and controlling traversal cost for deep trees. Sealed interfaces help by making leaf and composite explicit. ([Read more](../l2-practitioner/composite.md))

7. **Why might an Object Pool make performance worse?**

    ??? success "Model answer"
        Allocation of short-lived objects is very cheap on modern JVMs, while pools add synchronisation, contention and bugs from returning dirty objects. Pool only expensive resources such as connections. ([Read more](../l2-practitioner/object-pool.md))

## L3 Senior

1. **Why doesn't `@Transactional` work when a method calls another method in the same class?**

    ??? success "Model answer"
        Spring applies it through a proxy. A call on `this` goes directly to the target object, bypassing the proxy, so no transaction starts. Move the method to another bean or use `TransactionTemplate`. ([Read more](../l3-professional/patterns-in-spring.md))

2. **Where does the JDK itself use patterns?**

    ??? success "Model answer"
        Iterator (`Iterator`), Strategy (`Comparator`), Decorator (`java.io` streams, `Collections.unmodifiableList`), Factory (`List.of`, `Executors`), Builder (`StringBuilder`, `HttpRequest.newBuilder`), Observer (`Flow`), Template Method (`AbstractList`), Proxy (`java.lang.reflect.Proxy`). ([Read more](../l3-professional/patterns-in-the-jdk.md))

3. **How do you handle errors consistently in a layered service?**

    ??? success "Model answer"
        Domain exceptions express business failures; adapters translate infrastructure exceptions into domain or application exceptions; one boundary handler maps them to HTTP Problem Details; logs happen once at the boundary; retries only for transient failures. ([Read more](../l3-professional/error-handling-patterns.md))

4. **What does the producer-consumer pattern look like with virtual threads?**

    ??? success "Model answer"
        A bounded `BlockingQueue` between producers and consumers still provides back-pressure. Virtual threads make blocking cheap, so consumers can be simple blocking loops; limit concurrency to protect downstream resources with semaphores, not thread pool size. ([Read more](../l3-professional/concurrency-patterns.md))

5. **How do you measure the performance cost of a pattern?**

    ??? success "Model answer"
        With JMH microbenchmarks for hot paths and profiling under realistic load for the whole service. Most pattern indirection is inlined by the JIT; allocation in hot loops, megamorphic call sites and reflection are the usual real costs. ([Read more](../l3-professional/performance-of-patterns.md))

6. **How would you refactor a 500-line `switch` on order type?**

    ??? success "Model answer"
        Add characterisation tests, extract each branch into a method, introduce an interface (Strategy) or sealed type per branch, move the methods, then replace the switch with polymorphism or one exhaustive switch in a factory. ([Read more](../l3-professional/refactoring-to-patterns.md))

7. **MVVM on mobile: what goes in the ViewModel?**

    ??? success "Model answer"
        Screen state and presentation logic, exposed as observable state; no Android/Flutter UI types. Repositories behind it handle data sources and caching, which makes ViewModels unit-testable. ([Read more](../l3-professional/ui-and-mobile-patterns.md))

## L4 Senior / Lead

1. **How do you publish an event reliably after saving to the database?**

    ??? success "Model answer"
        Use a transactional outbox: write the event to an outbox table in the same transaction as the state change, then a relay (polling or CDC) publishes it. Consumers must be idempotent because delivery is at least once. ([Read more](../l4-senior/distributed-data-patterns.md))

2. **Orchestration vs choreography for sagas?**

    ??? success "Model answer"
        Orchestration has a coordinator that tells each step what to do; it's easier to understand, monitor and change. Choreography lets services react to events; it has fewer central parts but the flow is implicit. Use orchestration for business-critical flows with many steps. ([Read more](../l4-senior/distributed-data-patterns.md))

3. **In what order do you apply timeout, retry and circuit breaker?**

    ??? success "Model answer"
        Fallback outside, then circuit breaker, then retry, then timeout around each attempt, with a bulkhead limiting concurrency. Then the breaker counts final outcomes, each attempt has its own timeout, and an open breaker stops retries immediately. ([Read more](../l4-senior/resilience-patterns.md))

4. **How do you prevent a cache stampede?**

    ??? success "Model answer"
        Single-flight loading (one load per key), refresh-ahead so hot keys are reloaded before expiry, jittered TTLs, and serving stale values while refreshing. ([Read more](../l4-senior/caching-patterns.md))

5. **What makes a good microservice boundary?**

    ??? success "Model answer"
        A business capability or bounded context with its own data, few synchronous dependencies, and a team that owns it. Entity-based services (ProductService, OrderService) usually produce chatty, coupled systems. ([Read more](../l4-senior/microservice-patterns.md))

6. **How do you make a payment API safe to retry?**

    ??? success "Model answer"
        Require an idempotency key; store the key with the request hash and the response; return the stored response for repeats and reject a different payload with the same key. ([Read more](../l4-senior/api-design-patterns.md))

7. **What is an aggregate, and how big should it be?**

    ??? success "Model answer"
        A consistency boundary: invariants inside it are enforced in one transaction, and outside objects reference it by ID. Keep it as small as the invariants allow; large aggregates cause contention. ([Read more](../l4-senior/ddd-tactical.md))

## L5 Staff / Principal

1. **How do you decide whether a pattern is worth introducing?**

    ??? success "Model answer"
        Compare the cost of indirection (more types, harder navigation) with the cost of change it removes, using evidence: how often this area changed, how many variants exist now. Prefer reversible choices, record the decision in an ADR, and revisit it. ([Read more](../l5-staff-principal/pattern-selection.md))

2. **How do you keep an architecture from eroding?**

    ??? success "Model answer"
        Fitness functions in CI: ArchUnit or JPMS for dependencies, performance and API compatibility checks, with a ratchet baseline for existing violations. Review them like product code. ([Read more](../l5-staff-principal/evolutionary-architecture.md))

3. **How would you replace a legacy billing system without a big-bang rewrite?**

    ??? success "Model answer"
        Characterisation tests and seams first, then a strangler fig or branch by abstraction, an anti-corruption layer to keep the legacy model out, a parallel run comparing results, and a toggle-based cutover per segment with rollback. ([Read more](../l5-staff-principal/legacy-modernisation.md))

4. **What breaks binary compatibility in a library?**

    ??? success "Model answer"
        Removing or renaming anything, changing parameter or return types (including widening int to long), adding abstract methods to interfaces others implement, making classes or methods final. Additions and default methods are safe. Enforce with japicmp and semantic versioning. ([Read more](../l5-staff-principal/framework-and-library-design.md))

5. **Why can there be two instances of a Singleton in one JVM?**

    ??? success "Model answer"
        Class identity is name plus class loader. Two web applications or plugins loading the same class with different loaders each get their own static state. ([Read more](../l5-staff-principal/jvm-internals-behind-patterns.md))

6. **How do you govern patterns across 10 teams without slowing them down?**

    ??? success "Model answer"
        A catalogue with owners and ADOPT/TRIAL/HOLD statuses enforced by lint, risk-based design review, reference implementations, a funded debt strategy, and scorecards used to offer help, with guards against gaming. ([Read more](../l5-staff-principal/governing-patterns-at-scale.md))
