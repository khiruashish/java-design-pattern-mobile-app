---
tags: [reference]
---
# Production Checklist

!!! abstract "TL;DR"
    A go-live checklist for a Java service built with the patterns in this playbook. Tick each item or write down why it doesn't apply. It's grouped by concern, and each item links to the page that explains it.

**How to use it.** Copy this list into the service's launch ticket or design doc. Items marked **(must)** are blockers for a customer-facing FreshCart service; the rest are strongly recommended.

## Design and code

- [ ] **(must)** Dependencies are injected through constructors; no service locator or mutable singletons ([Dependency Injection](../l2-practitioner/dependency-injection.md)).
- [ ] **(must)** Domain invariants are enforced inside aggregates and value objects, not only in controllers ([DDD Tactical](../l4-senior/ddd-tactical.md)).
- [ ] Money uses integer minor units or `BigDecimal`, never `double` ([Anti-Pattern Catalogue](anti-patterns.md)).
- [ ] Each pattern in use has a reason that a reviewer can state; no speculative abstractions ([Choosing Patterns](../l5-staff-principal/pattern-selection.md)).
- [ ] Module or package boundaries are enforced by ArchUnit or JPMS ([Evolutionary Architecture](../l5-staff-principal/evolutionary-architecture.md)).
- [ ] Shared state is immutable or uses concurrent structures; no double-checked locking without `volatile` ([Concurrency Patterns](../l3-professional/concurrency-patterns.md)).

## Remote calls and resilience

- [ ] **(must)** Every remote call has a connect timeout and a response timeout ([Resilience Patterns](../l4-senior/resilience-patterns.md)).
- [ ] **(must)** Retries only for idempotent or keyed operations, with exponential backoff and jitter, at one layer only.
- [ ] Circuit breakers for dependencies that can fail for minutes, with thresholds tuned for real traffic volumes.
- [ ] Bulkheads keep one slow dependency from using all threads or connections.
- [ ] Fallbacks are defined and tested, or the service fails fast with a clear error.
- [ ] Partner quotas are respected with a rate limiter.

## Data and messaging

- [ ] **(must)** No dual writes: events are published through a transactional outbox or CDC ([Distributed Data Patterns](../l4-senior/distributed-data-patterns.md)).
- [ ] **(must)** Every message consumer is idempotent ([Messaging Patterns](../l4-senior/messaging-patterns.md)).
- [ ] Poison messages go to a dead letter channel after bounded retries, with an alert and a replay procedure.
- [ ] Multi-step business transactions have a saga with compensations, and stuck sagas are detected.
- [ ] Database migrations are backward compatible (expand, migrate, contract).
- [ ] Connection pool sizes are set from database capacity ([Build & Tooling Configuration](configuration.md)).

## Caching

- [ ] Every cache is bounded by size and has a TTL or explicit invalidation ([Caching Patterns](../l4-senior/caching-patterns.md)).
- [ ] Hot keys are protected from stampedes (single-flight or refresh-ahead).
- [ ] Cache hit rate, evictions and load time are exported as metrics.
- [ ] The service works, slower, when the shared cache is down.

## APIs

- [ ] **(must)** Write endpoints that can be retried accept an idempotency key ([API Design Patterns](../l4-senior/api-design-patterns.md)).
- [ ] Errors use a consistent format (Problem Details) without leaking internals.
- [ ] Lists are paginated with a maximum page size.
- [ ] A versioning and deprecation policy is published; contract tests protect consumers.
- [ ] Mobile screens get aggregated endpoints instead of many chatty calls ([UI and Mobile Patterns](../l3-professional/ui-and-mobile-patterns.md)).

## Security

- [ ] **(must)** Inputs are validated at the boundary; queries are parameterised.
- [ ] **(must)** Secrets come from a secret store, never from code or images.
- [ ] Authorisation is checked in the service, not only in the gateway.
- [ ] Deserialization of untrusted data uses allow-lists; no Java native serialization from outside.
- [ ] Dependencies are scanned; an SBOM is published.

## Observability

- [ ] **(must)** Structured logs with a correlation or trace ID on every line.
- [ ] **(must)** Metrics for request rate, errors and latency (RED) per endpoint and per dependency.
- [ ] Distributed tracing across service and message boundaries.
- [ ] Circuit breaker state, retry counts, pool usage and queue depths are visible on a dashboard.
- [ ] Alerts are based on SLOs, and each has a runbook link.

## Testing

- [ ] **(must)** Unit tests for domain rules; integration tests for adapters (database, broker, partners) with real containers where possible ([Testing Patterns](../l2-practitioner/testing-patterns.md)).
- [ ] Contract tests for APIs and events shared with other teams.
- [ ] Failure tests: dependency timeouts, broker unavailable, duplicate messages.
- [ ] A load test at expected peak plus headroom, with profiles checked ([Performance Cost of Patterns](../l3-professional/performance-of-patterns.md)).

## Operations

- [ ] Health and readiness endpoints reflect real dependencies without cascading failures.
- [ ] Graceful shutdown: stop accepting work, finish in-flight requests, commit offsets.
- [ ] Feature toggles for risky features, with owners and removal dates ([Legacy Modernisation](../l5-staff-principal/legacy-modernisation.md)).
- [ ] Rollback is tested; database changes don't block rollback.
- [ ] JVM settings: heap sized for the container, heap dump on OOM ([Build & Tooling Configuration](configuration.md)).
- [ ] On-call owners, runbooks and an escalation path are documented.
