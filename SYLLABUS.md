# Syllabus: Java Design Patterns Playbook

This is Phase 0 and Phase 1 of [the master prompt](PLAYBOOK_MASTER_PROMPT.md), run with TOPIC = Java Design Patterns. Reply `approve`, or say what to add, remove or move.

## Phase 0: resolved inputs

| Field | Value | Source |
|-------|-------|--------|
| TOPIC | Java Design Patterns | given |
| VERSION | Java 21 (LTS) | chosen: the LTS every example is tested on in CI |
| EXAMPLE_LANGUAGE | Java 21 | default |
| COMPANION_STACK | JDK 21, Maven, JUnit 5, AssertJ, Mockito, Spring Boot 3, Resilience4j, JMH, ArchUnit | default (common production stack) |
| TARGET_LEVELS | L0–L5 | default |
| AUDIENCE_EMPHASIS | balanced | default |
| DEPTH | exhaustive | default |
| RUNNING_DOMAIN | grocery delivery ("FreshCart") | chosen, to match your grocery-startup work |
| INCLUDE_INTERVIEW | yes | default |
| INCLUDE_CAPSTONE | yes | default |
| EXTRA_TOPICS | UI and mobile patterns (MVC/MVP/MVVM/MVI, offline-first) | chosen, because of the repo name |
| EXCLUDE_TOPICS | none | default |
| SITE_NAME | Java Design Patterns Playbook | default |
| SITE_URL | https://khiruashish.github.io/java-design-pattern-mobile-app/ | derived from the repo |
| REPO_URL | https://github.com/khiruashish/java-design-pattern-mobile-app | derived from the repo |
| PRIMARY_COLOR / ACCENT_COLOR | indigo / deep purple | default |
| OUTPUT_MODE | write-to-disk | default (file tools available) |
| PAGES_PER_RESPONSE | 2 | default |

Topic slug: `java-design-patterns`. Java 21 is well within reliable knowledge; anything about Java 22+ (for example structured concurrency and scoped values, which were still previews in 21) will carry a "Verify against the official docs" warning.

## Phase 1: curriculum map

Sources and lenses used: the GoF catalogue (all 23 patterns), Effective Java, Fowler's *Patterns of Enterprise Application Architecture*, *Enterprise Integration Patterns*, *Domain-Driven Design*, *Java Concurrency in Practice*, microservices.io patterns, the JDK API (java.util, java.io, java.lang.reflect, java.util.concurrent), Spring Framework internals, JEPs for records (395), sealed classes (409), pattern matching for switch (441) and virtual threads (444), testing, performance (JMH, JIT), security, operations and organisational practice.

### L0 · Orientation (Day 0): 4 pages

The reader can explain what design patterns are, why they exist, and run the examples.

- **What Are Design Patterns** (`l0-orientation/what-are-design-patterns.md`, core, ~15 min): Where patterns come from and how a pattern is described.
    - Christopher Alexander and the GoF book (1994)
    - Anatomy of a pattern: intent, problem, solution, consequences
    - Creational, structural and behavioural categories
    - Patterns vs. idioms vs. architectural styles vs. principles
    - The 23 GoF patterns at a glance
- **Why Patterns Matter (and When They Don't)** (`l0-orientation/why-patterns-matter.md`, core, ~12 min): Shared vocabulary, design reuse, and the cost of over-engineering.
    - Shared vocabulary in code review
    - Designing for change
    - Pattern fever and speculative generality
    - Patterns that modern Java made smaller
- **Setting Up Java to Run the Examples** (`l0-orientation/java-setup.md`, tooling, ~10 min): Install JDK 21, run single-file programs, use an IDE and Maven.
    - Installing JDK 21 (SDKMAN!, package managers)
    - Running single-file programs with `java File.java`
    - Maven project layout for the examples/ folder
    - IDE setup: IntelliJ IDEA, VS Code, Eclipse
    - JShell for quick experiments
- **Reading UML for Patterns** (`l0-orientation/reading-uml.md`, core, ~15 min): Read the class and sequence diagrams used on every page.
    - Class diagrams: classes, interfaces, abstract classes
    - Relationships: association, aggregation, composition, inheritance, realisation, dependency
    - Sequence diagrams
    - State diagrams
    - How Mermaid draws them in this playbook

### L1 · Foundations (0–1 yr): 12 pages

The reader can use OOP principles and the most common patterns with guidance.

- **OOP Refresher for Patterns** (`l1-foundations/oop-refresher.md`, core, ~25 min): The four OOP pillars every pattern is built from.
    - Encapsulation
    - Abstraction
    - Inheritance
    - Polymorphism and dynamic dispatch
    - Interfaces vs. abstract classes
    - Composition over inheritance
    - Programming to an interface
- **SOLID Principles** (`l1-foundations/solid-principles.md`, core, ~30 min): The five principles that tell you when a pattern helps.
    - Single Responsibility
    - Open/Closed
    - Liskov Substitution
    - Interface Segregation
    - Dependency Inversion
- **DRY, KISS, YAGNI and Friends** (`l1-foundations/other-design-principles.md`, core, ~20 min): The other principles that keep patterns honest.
    - DRY
    - KISS
    - YAGNI
    - Law of Demeter
    - Tell, Don't Ask
    - Cohesion and coupling
    - GRASP in brief
- **Singleton** (`l1-foundations/singleton.md`, creational, ~25 min): One instance, global access, and why it's often a smell.
    - Eager initialisation
    - Lazy holder idiom
    - Enum singleton
    - Double-checked locking with volatile
    - Breaking singletons: reflection, serialization, class loaders
    - Singleton vs. dependency-injected single instance
- **Factory Method** (`l1-foundations/factory-method.md`, creational, ~25 min): Let subclasses or functions decide which class to create.
    - Classic subclass-based factory method
    - Parameterised factory method
    - Static factory methods (Effective Java item 1)
    - Simple factory vs. Factory Method
    - Supplier<T> as a factory
- **Builder** (`l1-foundations/builder.md`, creational, ~25 min): Build complex or immutable objects step by step.
    - Telescoping constructors problem
    - Classic GoF builder with director
    - Fluent builder for immutable objects
    - Builders for records
    - Step builder (type-safe required fields)
    - Lombok @Builder trade-offs
- **Strategy** (`l1-foundations/strategy.md`, behavioural, ~25 min): Swap algorithms at runtime behind one interface.
    - Interface-based strategy
    - Lambdas and functional interfaces as strategies
    - Enum strategies
    - Selecting a strategy (map, factory, DI)
    - Strategy vs. State vs. Template Method
- **Observer** (`l1-foundations/observer.md`, behavioural, ~25 min): Notify many listeners when something changes.
    - Subject and observer interfaces
    - Push vs. pull model
    - Listener management and memory leaks
    - Synchronous vs. asynchronous notification
    - java.beans.PropertyChangeSupport and Flow API
    - Observer vs. Pub/Sub
- **Template Method** (`l1-foundations/template-method.md`, behavioural, ~20 min): Fix the algorithm's skeleton, let subclasses fill the steps.
    - Abstract class with final template method
    - Hook methods
    - Hollywood principle
    - Template method with lambdas instead of subclasses
    - Template Method in the JDK and Spring (JdbcTemplate)
- **Adapter** (`l1-foundations/adapter.md`, structural, ~20 min): Make an incompatible interface fit the one you need.
    - Object adapter
    - Class adapter
    - Two-way adapter
    - Adapting legacy and third-party APIs
    - Adapter vs. Facade vs. Decorator
- **Decorator** (`l1-foundations/decorator.md`, structural, ~25 min): Add behaviour to an object without subclassing.
    - Decorator structure
    - Stacking decorators
    - java.io streams as decorators
    - Functional decorators with Function.andThen
    - Decorator vs. Proxy vs. inheritance
- **Facade** (`l1-foundations/facade.md`, structural, ~15 min): Give a complex subsystem one simple entry point.
    - Facade structure
    - Facade vs. service layer
    - Avoiding the god facade
    - Facades at module boundaries

### L2 · Practitioner (1–3 yrs): 19 pages

The reader can pick and implement every GoF pattern independently and test it.

- **Abstract Factory** (`l2-practitioner/abstract-factory.md`, creational, ~25 min): Create families of related objects that must match.
    - Product families
    - Abstract Factory vs. Factory Method
    - Choosing the factory at startup
    - Adding a new product type
- **Prototype** (`l2-practitioner/prototype.md`, creational, ~20 min): Create new objects by copying an existing one.
    - Shallow vs. deep copy
    - Cloneable and why to avoid it
    - Copy constructors and copy factories
    - Prototype registry
- **Object Pool** (`l2-practitioner/object-pool.md`, creational, ~20 min): Reuse expensive objects instead of creating them.
    - Pool structure and lifecycle
    - Connection pools (HikariCP)
    - Thread pools as object pools
    - When pooling hurts on the modern JVM
    - Virtual threads and pooling
- **Dependency Injection** (`l2-practitioner/dependency-injection.md`, creational, ~30 min): Give objects their dependencies instead of letting them find them.
    - Constructor, setter and field injection
    - Composition root
    - Service Locator and why it's an anti-pattern
    - DI containers: Spring, Guice, Dagger
    - Scopes and lifecycles
- **Composite** (`l2-practitioner/composite.md`, structural, ~20 min): Treat single objects and groups the same way.
    - Component, leaf and composite
    - Safety vs. transparency
    - Recursive operations
    - Composite with sealed interfaces
- **Proxy** (`l2-practitioner/proxy.md`, structural, ~25 min): Control access to an object through a stand-in.
    - Virtual, protection, remote and caching proxies
    - Static proxies
    - JDK dynamic proxies (java.lang.reflect.Proxy)
    - Byte-code proxies (ByteBuddy, CGLIB)
    - Proxy pitfalls: self-invocation
- **Bridge** (`l2-practitioner/bridge.md`, structural, ~20 min): Separate an abstraction from its implementation so both can vary.
    - Abstraction and implementor
    - Avoiding class explosion
    - Bridge vs. Adapter vs. Strategy
    - JDBC as a bridge
- **Flyweight** (`l2-practitioner/flyweight.md`, structural, ~20 min): Share common state to save memory.
    - Intrinsic vs. extrinsic state
    - Flyweight factory
    - Integer cache and String interning
    - Measuring the memory win
- **Command** (`l2-practitioner/command.md`, behavioural, ~25 min): Turn a request into an object you can queue, log or undo.
    - Command, invoker and receiver
    - Undo/redo
    - Macro commands
    - Commands as lambdas
    - Command queues and jobs
- **Iterator** (`l2-practitioner/iterator.md`, behavioural, ~20 min): Traverse a collection without exposing its internals.
    - Iterator and Iterable
    - Fail-fast vs. weakly consistent iterators
    - Custom iterators
    - Spliterator and streams
- **State** (`l2-practitioner/state.md`, behavioural, ~25 min): Change behaviour when internal state changes.
    - State interface per state
    - Enum-based state machines
    - State with sealed types and switch
    - Transition tables
    - State vs. Strategy
- **Chain of Responsibility** (`l2-practitioner/chain-of-responsibility.md`, behavioural, ~20 min): Pass a request along handlers until one handles it.
    - Linked handlers
    - List-based pipelines
    - Servlet filters and Spring interceptors
    - Ending the chain and fallbacks
- **Mediator** (`l2-practitioner/mediator.md`, behavioural, ~20 min): Centralise how objects talk to each other.
    - Mediator and colleagues
    - Event bus as a mediator
    - Mediator vs. Observer
    - Avoiding the god mediator
- **Memento** (`l2-practitioner/memento.md`, behavioural, ~20 min): Capture and restore state without breaking encapsulation.
    - Originator, memento and caretaker
    - Immutable snapshots with records
    - Undo history
    - Memory cost of snapshots
- **Visitor** (`l2-practitioner/visitor.md`, behavioural, ~30 min): Add operations to a class hierarchy without changing it.
    - Double dispatch
    - Classic visitor
    - Visitor with sealed interfaces and pattern matching
    - Expression problem
    - Acyclic visitor
- **Interpreter** (`l2-practitioner/interpreter.md`, behavioural, ~25 min): Represent and evaluate a small language.
    - Grammar to class hierarchy
    - Abstract syntax trees
    - Interpreter with records and switch
    - When to use a parser generator instead
- **Null Object** (`l2-practitioner/null-object.md`, behavioural, ~15 min): Replace null checks with an object that does nothing.
    - Null Object structure
    - Null Object vs. Optional
    - Special Case pattern
- **Specification** (`l2-practitioner/specification.md`, behavioural, ~20 min): Combine business rules as composable predicates.
    - Specification interface
    - and/or/not composition
    - Predicate<T> as specification
    - Specifications in Spring Data JPA
- **Testing Patterns** (`l2-practitioner/testing-patterns.md`, testing, ~30 min): Test pattern-based code and use the patterns of testing.
    - Test doubles: dummy, stub, fake, spy, mock
    - Object Mother and Test Data Builder
    - Arrange-Act-Assert
    - Testing through seams created by patterns
    - Contract tests for interface implementations

### L3 · Professional (3–6 yrs): 11 pages

The reader can apply patterns idiomatically in modern Java, frameworks and concurrent code.

- **Patterns in Modern Java (17–21)** (`l3-professional/patterns-in-modern-java.md`, core, ~35 min): How records, sealed types, pattern matching and lambdas reshape the GoF catalogue.
    - Records for value objects and mementos
    - Sealed interfaces for closed hierarchies
    - Pattern matching for switch replacing Visitor
    - Lambdas replacing Strategy, Command and Template Method
    - Enums for Singleton and State
    - Default and static interface methods
- **Functional Patterns in Java** (`l3-professional/functional-patterns.md`, core, ~30 min): Use higher-order functions, composition and immutability as patterns.
    - Higher-order functions
    - Function composition
    - Optional as a container
    - Result/Either types
    - Currying and partial application
    - Streams as pipelines
    - Immutability and persistent collections
- **Patterns in the JDK** (`l3-professional/patterns-in-the-jdk.md`, core, ~25 min): Recognise the patterns you already use every day.
    - Collections and Iterator
    - java.io decorators
    - Runtime and singletons
    - Comparator strategies
    - ServiceLoader
    - Flow and reactive streams
- **Patterns in Spring** (`l3-professional/patterns-in-spring.md`, core, ~30 min): See how Spring is built from patterns and how to use them well.
    - IoC container and DI
    - Proxies and AOP
    - Template classes
    - Application events as Observer
    - Factory beans
    - Strategy beans with List/Map injection
- **Concurrency Patterns** (`l3-professional/concurrency-patterns.md`, concurrency, ~40 min): Write correct concurrent code with proven patterns.
    - Immutability
    - Thread confinement and ThreadLocal
    - Producer-consumer with BlockingQueue
    - Thread pool and executor
    - Future and CompletableFuture
    - Read-write lock
    - Active Object
    - Balking and guarded suspension
    - Virtual threads and structured concurrency
    - Scoped values
- **Enterprise Application Patterns** (`l3-professional/enterprise-application-patterns.md`, data, ~35 min): Organise business logic and data access (Fowler's PoEAA).
    - Transaction Script vs. Domain Model
    - Service Layer
    - Repository
    - Data Mapper vs. Active Record
    - Unit of Work
    - Identity Map
    - Lazy Load
    - DTO and assemblers
- **UI and Mobile Patterns** (`l3-professional/ui-and-mobile-patterns.md`, ui, ~35 min): Structure user-facing apps, including Android and other mobile clients.
    - MVC
    - MVP
    - MVVM
    - MVI and unidirectional data flow
    - Repository for offline-first apps
    - Navigation and coordinator patterns
    - Backend for Frontend
- **Error Handling Patterns** (`l3-professional/error-handling-patterns.md`, core, ~25 min): Handle failure in a consistent, testable way.
    - Checked vs. unchecked exceptions
    - Exception translation
    - Result types
    - Fail fast
    - Notification pattern for validation
- **Anti-Patterns and Code Smells** (`l3-professional/anti-patterns-and-code-smells.md`, core, ~30 min): Spot the designs patterns are meant to fix, and the ones patterns cause.
    - God object
    - Anaemic domain model
    - Spaghetti and lava flow
    - Golden hammer
    - Singletonitis
    - Poltergeists
    - Code smells that point to patterns
- **Refactoring to Patterns** (`l3-professional/refactoring-to-patterns.md`, core, ~30 min): Move existing code toward (and away from) patterns safely.
    - Refactoring workflow with tests
    - Replace conditional with polymorphism
    - Replace constructors with creation methods
    - Extract strategy
    - Inline a pattern that doesn't pay
- **Performance Cost of Patterns** (`l3-professional/performance-of-patterns.md`, performance, ~30 min): Know what indirection costs on the JVM and how to measure it.
    - Virtual calls, inlining and megamorphic call sites
    - Allocation and escape analysis
    - Proxy and reflection overhead
    - Measuring with JMH
    - Startup cost of DI containers

### L4 · Senior / Lead (6–10 yrs): 9 pages

The reader can choose architecture and distributed-system patterns and defend the trade-offs.

- **Architectural Patterns** (`l4-senior/architectural-patterns.md`, architecture, ~40 min): Structure whole applications.
    - Layered architecture
    - Hexagonal (ports and adapters)
    - Clean and onion architecture
    - Modular monolith
    - Microkernel / plugin architecture
    - Event-driven architecture
- **Domain-Driven Design: Tactical Patterns** (`l4-senior/ddd-tactical.md`, architecture, ~40 min): Model a rich domain in code.
    - Entities
    - Value objects
    - Aggregates and invariants
    - Domain events
    - Domain services
    - Factories and repositories in DDD
- **Domain-Driven Design: Strategic Patterns** (`l4-senior/ddd-strategic.md`, architecture, ~35 min): Split a large domain into models and teams.
    - Ubiquitous language
    - Bounded contexts
    - Context mapping
    - Anti-corruption layer
    - Shared kernel and published language
- **Microservice Patterns** (`l4-senior/microservice-patterns.md`, architecture, ~40 min): Decompose and connect services.
    - Decomposition by capability and subdomain
    - Database per service
    - API Gateway
    - Service discovery
    - Strangler fig
    - Sidecar
- **Resilience Patterns** (`l4-senior/resilience-patterns.md`, operations, ~35 min): Keep systems up when dependencies fail.
    - Timeout
    - Retry with backoff and jitter
    - Circuit breaker
    - Bulkhead
    - Rate limiter
    - Fallback
    - Resilience4j
- **Distributed Data Patterns** (`l4-senior/distributed-data-patterns.md`, data, ~40 min): Keep data consistent across services.
    - Saga (choreography and orchestration)
    - Transactional outbox
    - CQRS
    - Event sourcing
    - Idempotent consumer
    - Change data capture
- **Messaging and Integration Patterns** (`l4-senior/messaging-patterns.md`, integration, ~35 min): Connect systems with messages (Enterprise Integration Patterns).
    - Point-to-point and publish-subscribe
    - Competing consumers
    - Message router and content-based router
    - Dead letter channel
    - Message translator
    - Request-reply and correlation
- **Caching Patterns** (`l4-senior/caching-patterns.md`, performance, ~30 min): Choose where and how to cache.
    - Cache-aside
    - Read-through and write-through
    - Write-behind
    - Cache invalidation strategies
    - Caffeine and Redis
- **API Design Patterns** (`l4-senior/api-design-patterns.md`, integration, ~30 min): Design APIs that stay stable as they grow.
    - Fluent interfaces
    - Versioning
    - Pagination
    - Idempotency keys
    - Backward compatibility rules

### L5 · Staff / Principal (10–15+ yrs): 6 pages

The reader can set pattern standards, evolve architecture and know the internals.

- **Choosing Patterns and Trade-offs** (`l5-staff-principal/pattern-selection.md`, architecture, ~30 min): Decide when a pattern pays off, and say no when it doesn't.
    - Cost of indirection
    - Decision frameworks
    - Architecture Decision Records
    - Reversibility
- **Evolutionary Architecture** (`l5-staff-principal/evolutionary-architecture.md`, architecture, ~30 min): Keep architecture healthy as code and teams change.
    - Fitness functions
    - ArchUnit rules
    - Modularity with JPMS
    - Measuring coupling
- **Legacy Modernisation Patterns** (`l5-staff-principal/legacy-modernisation.md`, architecture, ~35 min): Move old systems forward without a rewrite.
    - Strangler fig in practice
    - Branch by abstraction
    - Parallel run
    - Anti-corruption layers for legacy
    - Feature toggles
- **JVM Internals Behind Patterns** (`l5-staff-principal/jvm-internals-behind-patterns.md`, performance, ~40 min): Understand how the JVM implements what patterns rely on.
    - Class loading and singleton guarantees
    - Dynamic proxies and bytecode generation
    - invokedynamic and lambda metafactory
    - Reflection, MethodHandles and VarHandles
    - Java Memory Model and safe publication
- **Designing Frameworks and Libraries** (`l5-staff-principal/framework-and-library-design.md`, architecture, ~35 min): Build extension points other teams can rely on.
    - Service Provider Interface and ServiceLoader
    - Plugin architectures
    - Inversion of control in your own framework
    - Hiding internals with modules
    - API evolution and binary compatibility
- **Governing Patterns Across an Organisation** (`l5-staff-principal/governing-patterns-at-scale.md`, organisation, ~25 min): Set standards, review designs and grow engineers.
    - Coding standards and pattern catalogues
    - Design review checklists
    - Reference implementations
    - Technical debt strategy
    - Teaching patterns to teams

### Cross-cutting pages

- Home, How to use, Roadmap, and one index page per level (6)
- Reference (8): Cheat Sheet, Build & Tooling Configuration, Pattern Comparisons, Decision Guide, Anti-Pattern Catalogue, Production Checklist, Migration Guide, Java Version History for Patterns
- Projects (2): index, Capstone "FreshCart" (grocery delivery backend plus mobile client contract)
- Interview (4): Interview Guide, Questions by Level, Scenario Questions, Hands-on Tasks
- Glossary, Further reading, Tags

### Deliberately excluded

- **Kotlin/Android code samples:** the playbook is Java-first. The UI and mobile patterns page explains MVVM/MVI with Java and maps them to Android's architecture components; a Kotlin tab can be added with the "second language" add-on.
- **Reactive frameworks in depth (Reactor, RxJava):** covered only as Observer/Flow and in concurrency patterns; a full reactive playbook is its own topic.
- **Cloud-provider-specific patterns (AWS/Azure):** out of scope; patterns are shown vendor-neutral.
- **Cloneable-based Prototype as a recommended approach:** covered only as something to avoid.

### Extra and excluded topics

- EXTRA_TOPICS: "UI and mobile patterns" landed in L3 (`l3-professional/ui-and-mobile-patterns.md`), with Backend for Frontend repeated in L4 microservice patterns.
- EXCLUDE_TOPICS: none given.

### Depth and size

DEPTH = exhaustive: 61 topic pages, plus 6 level indexes, 8 reference pages, 2 project pages, 4 interview pages and 6 site pages, so **87 files** in total.

### Batch plan (Phase 4)

1. Scaffold (done): every page exists as a valid stub and `mkdocs build --strict` passes.
2. Reference page (done): the first L1 topic, `OOP Refresher for Patterns`, written in full. Reply `continue` or ask for template changes.
3. Two topic pages per batch: L0 (2 batches) → L1 (6) → L2 (10) → L3 (6) → L4 (5) → L5 (3) → reference (4) → capstone (1) → interview (2) → glossary and further reading (1). About 40 batches.

## Completeness check (1D)

"Could a 15-year expert point at a pattern, decision or failure mode this syllabus misses?" Added because of this check:

- Object Pool, Null Object, Specification and Dependency Injection (common non-GoF patterns asked about in reviews).
- Breaking singletons through reflection, serialization and class loaders.
- Self-invocation with proxies (the classic Spring `@Transactional` trap).
- Performance cost of patterns on the JVM (megamorphic calls, escape analysis, JMH).
- JVM internals: lambda metafactory, dynamic proxies, safe publication under the Java Memory Model.
- Idempotent consumer, transactional outbox and change data capture in distributed data.
- API evolution and binary compatibility for library authors.
- Governance: ADRs, fitness functions, ArchUnit, review checklists.
