---
tags: [interview]
---
# Scenario Questions

!!! abstract "TL;DR"
    "Production is down, what do you do?" style questions on pattern-heavy systems. Each scenario gives the symptoms; the strong answer shows how to **stabilise first, then diagnose, then fix the design** so it can't happen again. Most root causes are a missing or misused pattern.

**How to answer.** Interviewers want a calm sequence: (1) limit customer impact, (2) form hypotheses from the symptoms and check them with data, (3) fix the immediate cause, (4) prevent recurrence with a design or process change. Name the patterns you'd use and why.

## Incidents

### 1. Customers charged twice

**Scenario:** After a mobile app release, about 0.5% of FreshCart orders show two PayLink charges. The new app retries `POST /checkout` when a request takes more than 5 seconds.

??? success "Strong answer"
    **Stabilise:** stop automatic retries in the app via a remote config flag; refund duplicates using PayLink's ledger joined with orders.
    **Diagnose:** the checkout is not idempotent. A slow first request still completes on the server while the retry creates a second order and charge.
    **Fix:** require an `Idempotency-Key` header generated once per checkout attempt; store key, request hash and response; return the stored response for repeats; pass the key to PayLink if it supports one.
    **Prevent:** contract tests that send the same request twice; make retry policies part of the API contract.
    **Patterns:** idempotency key ([API Design Patterns](../l4-senior/api-design-patterns.md)), idempotent consumer.

### 2. Checkout down because a partner is slow

**Scenario:** ParcelGo's API latency jumps from 200 ms to 30 seconds. Within minutes, all of FreshCart's checkout endpoints time out, even for Bengaluru orders that use QuickDrop.

??? success "Strong answer"
    **Stabilise:** disable ParcelGo routing with a feature toggle so Pune falls back to QuickDrop.
    **Diagnose:** no timeout on the ParcelGo client, and it shares the request thread pool. Threads pile up waiting on ParcelGo, starving all requests.
    **Fix:** connect and response timeouts; a bulkhead (separate limited concurrency) per partner; a circuit breaker with a fallback partner.
    **Prevent:** "every remote call has a timeout" in the production checklist; chaos tests that inject partner latency.
    **Patterns:** timeout, bulkhead, circuit breaker, fallback ([Resilience Patterns](../l4-senior/resilience-patterns.md)).

### 3. Order events lost

**Scenario:** Some orders are paid but the warehouse never receives `OrderPaid`. It happens a few times a day, more during deploys.

??? success "Strong answer"
    **Stabilise:** a reconciliation job compares paid orders with warehouse records and republishes missing events.
    **Diagnose:** dual write: the service commits the database transaction and then publishes to Kafka. A crash or deploy between the two loses the event.
    **Fix:** transactional outbox (event row written in the same transaction, relay publishes) or CDC from the database log; consumers deduplicate by event ID.
    **Prevent:** ban direct publishing after commits with a lint rule or ArchUnit check.
    **Patterns:** transactional outbox, CDC, idempotent consumer ([Distributed Data Patterns](../l4-senior/distributed-data-patterns.md)).

### 4. Database overloaded every hour

**Scenario:** At the top of every hour, the catalogue database CPU hits 100% for about a minute and home-screen latency spikes.

??? success "Strong answer"
    **Diagnose:** all cached category menus were loaded at startup with the same one-hour TTL, so they expire together and thousands of requests miss at once (cache stampede).
    **Fix:** single-flight loading per key, refresh-ahead (`refreshAfterWrite`) so hot keys reload in the background, and jittered TTLs.
    **Prevent:** cache metrics (miss rate, load time) with alerts; load tests that include expiry.
    **Patterns:** cache-aside with refresh-ahead ([Caching Patterns](../l4-senior/caching-patterns.md)).

### 5. Kafka consumer stuck

**Scenario:** The loyalty service stopped awarding points two hours ago. Consumer lag on `order-events` keeps growing; logs show the same exception repeating.

??? success "Strong answer"
    **Stabilise:** move the failing message aside so the partition can progress.
    **Diagnose:** a poison message (a new event version with a field the consumer can't parse) is retried forever, blocking the partition.
    **Fix:** bounded retries, then a dead letter topic with alerting and a replay tool; a tolerant reader that ignores unknown fields; schema compatibility checks in the producer's CI.
    **Patterns:** dead letter channel, message translator, tolerant reader ([Messaging Patterns](../l4-senior/messaging-patterns.md)).

### 6. Memory grows after every redeploy

**Scenario:** A legacy app server hosts three FreshCart web apps. After each hot redeploy, metaspace grows by about 150 MB until the server crashes.

??? success "Strong answer"
    **Diagnose:** a class loader leak: something in a parent class loader (a JDBC driver registration, a `ThreadLocal`, a static listener registry) holds a reference to the old web app's classes.
    **Fix:** deregister drivers and listeners on shutdown, clear `ThreadLocal`s, avoid static registries shared across apps; confirm with a heap dump (GC roots of the old class loader).
    **Prevent:** move to one app per JVM or container.
    **Patterns:** Singleton and registry pitfalls across class loaders ([JVM Internals](../l5-staff-principal/jvm-internals-behind-patterns.md)).

### 7. A library upgrade breaks a partner

**Scenario:** ParcelGo reports `NoSuchMethodError` in their integration after they upgraded an unrelated library that pulls in FreshCart's SDK 1.4. FreshCart's own services work fine.

??? success "Strong answer"
    **Stabilise:** release 1.4.1 restoring the old method signature, delegating to the new one.
    **Diagnose:** 1.4 changed a parameter from `int` to `long`. Source-compatible for FreshCart (recompiled), binary-incompatible for ParcelGo's compiled code.
    **Prevent:** japicmp or revapi in CI with semantic versioning enforcement; deprecate before removing; changes like this only in a major release.
    **Patterns:** API evolution and binary compatibility ([Designing Frameworks and Libraries](../l5-staff-principal/framework-and-library-design.md)).

## Design scenarios

### 8. Add a new delivery partner in two days

**Scenario:** Business signs a third partner for Hyderabad and wants it live by Friday. Today, partner logic is spread across checkout, tracking and billing code with `if (partner == ...)` checks.

??? success "Strong answer"
    Introduce a `DeliveryPartner` interface for the operations checkout needs (quote, book, track, cancel). Wrap each partner SDK in an Adapter, select the adapter in one factory or router by city, and move the `if` branches into the adapters. Add contract tests that every adapter must pass. The new partner is then one adapter plus one routing entry. Mention that you'd do this as a strangler-style refactor behind tests, not a rewrite. ([Adapter](../l1-foundations/adapter.md), [Factory Method](../l1-foundations/factory-method.md), [Designing Frameworks and Libraries](../l5-staff-principal/framework-and-library-design.md))

### 9. Split the monolith?

**Scenario:** A new CTO wants FreshCart's monolith split into 20 microservices this year. The team is 15 engineers.

??? success "Strong answer"
    Ask what problem the split solves: deploy contention, scaling hotspots, team autonomy. With 15 engineers, 20 services would mean more operational load than benefit. Propose a modular monolith first: enforce module boundaries along bounded contexts with ArchUnit or JPMS, separate data ownership per module, then extract the one or two modules with a proven need (for example search for scale) using a strangler fig. Record the decision in an ADR with criteria for further extraction. ([Architectural Patterns](../l4-senior/architectural-patterns.md), [Microservice Patterns](../l4-senior/microservice-patterns.md), [Choosing Patterns](../l5-staff-principal/pattern-selection.md))

### 10. Read-heavy order history

**Scenario:** The "My orders" screen needs filters by status, partner and month, and it slows down checkout because both use the same tables.

??? success "Strong answer"
    Separate the read model: publish order events through the outbox, build a denormalised order-history projection (CQRS) in a store suited to the queries, and serve the screen from it. Accept a few seconds of staleness and show it in the UI. Keep checkout on the write model. Rebuild projections from events when the schema changes. ([Distributed Data Patterns](../l4-senior/distributed-data-patterns.md))

### 11. Price rules are changing weekly

**Scenario:** Marketing changes discount and fee rules every week, and each change needs a deploy and touches `PricingService` in several places.

??? success "Strong answer"
    Model rules as Strategies and Specifications composed from configuration, with a small rule language (Interpreter) only if marketing must author rules themselves. Version rule sets, validate them before activation, and roll out with feature toggles. Keep a test suite of example baskets with expected prices that every rule change must pass. ([Strategy](../l1-foundations/strategy.md), [Specification](../l2-practitioner/specification.md), [Interpreter](../l2-practitioner/interpreter.md))
