---
tags: [reference]
---
# Glossary

!!! abstract "TL;DR"
    Every term used in the playbook, alphabetically, with a one-line definition and a link to the page that explains it. Acronyms also show as tooltips when you hover over them on any page.

## A

Abstract Factory
: A creational pattern that creates families of related objects through one interface. [Page](l2-practitioner/abstract-factory.md)

ACL (anti-corruption layer)
: A translation layer that keeps another system's model from leaking into yours. [DDD Strategic](l4-senior/ddd-strategic.md)

Adapter
: Converts one interface into another that clients expect. [Page](l1-foundations/adapter.md)

ADR (architecture decision record)
: A short document recording a decision, its context, the options and the consequences. [Choosing Patterns](l5-staff-principal/pattern-selection.md)

Aggregate
: A cluster of domain objects treated as one unit for consistency, changed only through its root. [DDD Tactical](l4-senior/ddd-tactical.md)

Anemic domain model
: Domain classes with data but no behaviour, with all logic in services. Usually an anti-pattern. [Anti-Pattern Catalogue](reference/anti-patterns.md)

AOP (aspect-oriented programming)
: Applying cross-cutting behaviour (transactions, logging) around methods, in Java usually through proxies. [Patterns in Spring](l3-professional/patterns-in-spring.md)

API gateway
: A single entry point that routes, authenticates and aggregates requests to backend services. [Microservice Patterns](l4-senior/microservice-patterns.md)

ArchUnit
: A Java library for writing architecture rules as unit tests. [Evolutionary Architecture](l5-staff-principal/evolutionary-architecture.md)

At-least-once delivery
: A messaging guarantee where a message may arrive more than once but is never lost; consumers must be idempotent.

## B

BFF (backend for frontend)
: An API layer tailored to one client, such as the mobile app. [UI and Mobile Patterns](l3-professional/ui-and-mobile-patterns.md)

Binary compatibility
: Whether code compiled against an older library version still links and runs with a newer one. [Designing Frameworks and Libraries](l5-staff-principal/framework-and-library-design.md)

Bounded context
: A boundary within which one domain model and its language apply consistently. [DDD Strategic](l4-senior/ddd-strategic.md)

Branch by abstraction
: Replacing an implementation behind an interface inside one codebase, switching gradually. [Legacy Modernisation](l5-staff-principal/legacy-modernisation.md)

Bridge
: Separates an abstraction from its implementation so both can vary independently. [Page](l2-practitioner/bridge.md)

Builder
: Constructs complex objects step by step. [Page](l1-foundations/builder.md)

Bulkhead
: Isolates resources (threads, connections) per dependency so one failure can't exhaust everything. [Resilience Patterns](l4-senior/resilience-patterns.md)

## C

Cache stampede
: Many requests loading the same expired key at once, overloading the source. [Caching Patterns](l4-senior/caching-patterns.md)

Cache-aside
: The application reads from the cache, loads from the database on a miss and stores the result. [Caching Patterns](l4-senior/caching-patterns.md)

CDC (change data capture)
: Streaming database changes from the transaction log as events. [Distributed Data Patterns](l4-senior/distributed-data-patterns.md)

Chain of Responsibility
: Passes a request along a chain of handlers until one handles it. [Page](l2-practitioner/chain-of-responsibility.md)

Characterisation test
: A test that records what existing code does today, used as a safety net before refactoring. [Legacy Modernisation](l5-staff-principal/legacy-modernisation.md)

Circuit breaker
: Stops calling a failing dependency for a while after too many failures, then probes it. [Resilience Patterns](l4-senior/resilience-patterns.md)

Class loader
: The JVM component that loads classes; a class's identity is its name plus its loader. [JVM Internals](l5-staff-principal/jvm-internals-behind-patterns.md)

Command
: Encapsulates a request as an object, enabling queues, logging and undo. [Page](l2-practitioner/command.md)

Competing consumers
: Several consumers reading from one queue to share the work. [Messaging Patterns](l4-senior/messaging-patterns.md)

Composite
: Treats individual objects and groups of objects uniformly. [Page](l2-practitioner/composite.md)

Context map
: A diagram of bounded contexts and the relationships between them. [DDD Strategic](l4-senior/ddd-strategic.md)

CQRS
: Separating the model that handles writes from the model that serves reads. [Distributed Data Patterns](l4-senior/distributed-data-patterns.md)

## D

Dead letter channel
: A queue or topic for messages that can't be processed after retries. [Messaging Patterns](l4-senior/messaging-patterns.md)

Decorator
: Adds behaviour to an object by wrapping it with the same interface. [Page](l1-foundations/decorator.md)

Dependency injection
: Supplying an object's collaborators from outside instead of creating them inside. [Page](l2-practitioner/dependency-injection.md)

Domain event
: A record of something that happened in the domain, such as OrderPaid. [DDD Tactical](l4-senior/ddd-tactical.md)

Double-checked locking
: Lazy initialisation that checks a field before and after locking; correct in Java only with volatile. [Concurrency Patterns](l3-professional/concurrency-patterns.md)

DRY
: Don't Repeat Yourself: every piece of knowledge has one authoritative representation. [DRY, KISS, YAGNI](l1-foundations/other-design-principles.md)

## E

Entity
: A domain object defined by its identity rather than its attributes. [DDD Tactical](l4-senior/ddd-tactical.md)

Event sourcing
: Storing state as the sequence of events that produced it. [Distributed Data Patterns](l4-senior/distributed-data-patterns.md)

## F

Facade
: A simplified interface to a complex subsystem. [Page](l1-foundations/facade.md)

Factory Method
: Lets a method or subclass decide which class to instantiate. [Page](l1-foundations/factory-method.md)

Fallback
: A degraded response used when a dependency fails. [Resilience Patterns](l4-senior/resilience-patterns.md)

Feature toggle
: A switch that enables or disables a code path at run time. [Legacy Modernisation](l5-staff-principal/legacy-modernisation.md)

Fitness function
: An automated check that an architectural characteristic still holds. [Evolutionary Architecture](l5-staff-principal/evolutionary-architecture.md)

Flyweight
: Shares immutable state between many objects to save memory. [Page](l2-practitioner/flyweight.md)

## G

Gang of Four
: Gamma, Helm, Johnson and Vlissides, authors of *Design Patterns* (1994), which catalogued 23 patterns. [What Are Design Patterns](l0-orientation/what-are-design-patterns.md)

God class
: A class that knows or does too much. [Anti-Patterns and Code Smells](l3-professional/anti-patterns-and-code-smells.md)

## H

Happens-before
: The Java Memory Model relation that guarantees one action's effects are visible to another. [JVM Internals](l5-staff-principal/jvm-internals-behind-patterns.md)

Hexagonal architecture
: Ports and adapters: the domain at the centre, with infrastructure plugged in through interfaces. [Architectural Patterns](l4-senior/architectural-patterns.md)

## I

Idempotency key
: A client-supplied key that lets a server recognise and safely ignore repeated requests. [API Design Patterns](l4-senior/api-design-patterns.md)

Idempotent consumer
: A message consumer that produces the same result if a message is delivered more than once. [Messaging Patterns](l4-senior/messaging-patterns.md)

Interpreter
: Represents a grammar as classes and evaluates sentences in it. [Page](l2-practitioner/interpreter.md)

Inversion of control
: The framework calls your code, instead of your code calling the framework. [Designing Frameworks and Libraries](l5-staff-principal/framework-and-library-design.md)

invokedynamic
: A JVM instruction whose call target is linked at run time by a bootstrap method; used for lambdas. [JVM Internals](l5-staff-principal/jvm-internals-behind-patterns.md)

Iterator
: Accesses elements of a collection sequentially without exposing its structure. [Page](l2-practitioner/iterator.md)

## J

JPMS
: The Java Platform Module System (Java 9+): modules declare what they export and require. [Evolutionary Architecture](l5-staff-principal/evolutionary-architecture.md)

## K

KISS
: Keep It Simple: prefer the simplest design that works. [DRY, KISS, YAGNI](l1-foundations/other-design-principles.md)

## M

Mediator
: Centralises communication between objects so they don't refer to each other directly. [Page](l2-practitioner/mediator.md)

Memento
: Captures an object's state so it can be restored later without exposing internals. [Page](l2-practitioner/memento.md)

Modular monolith
: One deployable application divided into modules with enforced boundaries. [Architectural Patterns](l4-senior/architectural-patterns.md)

MVVM
: Model-View-ViewModel: the ViewModel exposes screen state and logic that the view observes. [UI and Mobile Patterns](l3-professional/ui-and-mobile-patterns.md)

## N

Null Object
: A do-nothing implementation used instead of null. [Page](l2-practitioner/null-object.md)

## O

Object Pool
: Reuses expensive objects instead of creating new ones. [Page](l2-practitioner/object-pool.md)

Observer
: Notifies dependents automatically when a subject changes. [Page](l1-foundations/observer.md)

## P

Parallel run
: Running old and new implementations side by side and comparing results before switching. [Legacy Modernisation](l5-staff-principal/legacy-modernisation.md)

Prototype
: Creates objects by copying an existing instance. [Page](l2-practitioner/prototype.md)

Proxy
: A placeholder that controls access to another object. [Page](l2-practitioner/proxy.md)

## R

Ratchet
: A check that allows existing violations but fails on new ones, so the count only goes down. [Evolutionary Architecture](l5-staff-principal/evolutionary-architecture.md)

Rate limiter
: Limits how many calls are made in a period. [Resilience Patterns](l4-senior/resilience-patterns.md)

Record
: A Java class (16+) for immutable data with generated constructor, accessors, equals, hashCode and toString. [Patterns in Modern Java](l3-professional/patterns-in-modern-java.md)

Reference implementation
: A real, running example of the approved way to build something. [Governing Patterns](l5-staff-principal/governing-patterns-at-scale.md)

Repository
: A collection-like interface for loading and saving aggregates. [DDD Tactical](l4-senior/ddd-tactical.md)

Retry with backoff and jitter
: Retrying failed calls with growing, randomised delays so clients don't retry in sync. [Resilience Patterns](l4-senior/resilience-patterns.md)

Rule of three
: Extract an abstraction only when the third real variation appears. [Choosing Patterns](l5-staff-principal/pattern-selection.md)

## S

Safe publication
: Making an object visible to other threads only after it's fully constructed, via final fields, volatile, locks or static initialisation. [JVM Internals](l5-staff-principal/jvm-internals-behind-patterns.md)

Saga
: A sequence of local transactions with compensating actions instead of one distributed transaction. [Distributed Data Patterns](l4-senior/distributed-data-patterns.md)

Sealed type
: A class or interface (Java 17+) that lists its permitted subtypes, enabling exhaustive switches. [Patterns in Modern Java](l3-professional/patterns-in-modern-java.md)

Semantic versioning
: MAJOR.MINOR.PATCH, where MAJOR means breaking changes. [Designing Frameworks and Libraries](l5-staff-principal/framework-and-library-design.md)

Service discovery
: Finding the network location of service instances at run time. [Microservice Patterns](l4-senior/microservice-patterns.md)

Service locator
: Looking up dependencies from a global registry; usually replaced by dependency injection. [Dependency Injection](l2-practitioner/dependency-injection.md)

ServiceLoader
: The JDK mechanism that discovers SPI implementations at run time. [Designing Frameworks and Libraries](l5-staff-principal/framework-and-library-design.md)

Sidecar
: A helper process deployed next to a service to add capabilities such as proxies or log shipping. [Microservice Patterns](l4-senior/microservice-patterns.md)

Singleton
: Ensures a class has exactly one instance with a global access point. [Page](l1-foundations/singleton.md)

SOLID
: Five object-oriented design principles: SRP, OCP, LSP, ISP, DIP. [Page](l1-foundations/solid-principles.md)

Specification
: A business rule as a composable predicate object. [Page](l2-practitioner/specification.md)

SPI (service provider interface)
: An interface a library defines for others to implement. [Designing Frameworks and Libraries](l5-staff-principal/framework-and-library-design.md)

State
: Lets an object change behaviour when its internal state changes. [Page](l2-practitioner/state.md)

Strangler fig
: Replacing a system piece by piece by routing features to the new implementation. [Legacy Modernisation](l5-staff-principal/legacy-modernisation.md)

Strategy
: Defines interchangeable algorithms behind one interface. [Page](l1-foundations/strategy.md)

## T

Technical debt
: Design or code shortcuts whose ongoing cost (interest) grows until fixed (principal). [Governing Patterns](l5-staff-principal/governing-patterns-at-scale.md)

Template Method
: Defines an algorithm's skeleton and lets subclasses fill in steps. [Page](l1-foundations/template-method.md)

Test double
: Any stand-in for a real dependency in tests: dummy, stub, spy, mock or fake. [Testing Patterns](l2-practitioner/testing-patterns.md)

Timeout
: An upper limit on how long to wait for a response. [Resilience Patterns](l4-senior/resilience-patterns.md)

Tolerant reader
: A consumer that ignores unknown fields so producers can add data without breaking it. [API Design Patterns](l4-senior/api-design-patterns.md)

Transactional outbox
: Writing events to an outbox table in the same transaction as the state change, then publishing them. [Distributed Data Patterns](l4-senior/distributed-data-patterns.md)

## U

Ubiquitous language
: Shared vocabulary between developers and domain experts within a bounded context. [DDD Strategic](l4-senior/ddd-strategic.md)

## V

Value object
: An immutable object defined by its attributes, such as Money. [DDD Tactical](l4-senior/ddd-tactical.md)

VarHandle
: A typed reference to a variable with atomic and memory-ordering operations (Java 9+). [JVM Internals](l5-staff-principal/jvm-internals-behind-patterns.md)

Virtual thread
: A lightweight JVM-managed thread (Java 21) that makes blocking code cheap. [Concurrency Patterns](l3-professional/concurrency-patterns.md)

Visitor
: Adds operations to a class hierarchy without changing its classes. [Page](l2-practitioner/visitor.md)

## W

Write-behind
: Writing to the cache immediately and to the database later in batches. [Caching Patterns](l4-senior/caching-patterns.md)

## Y

YAGNI
: You Aren't Gonna Need It: don't build for imagined future needs. [DRY, KISS, YAGNI](l1-foundations/other-design-principles.md)
