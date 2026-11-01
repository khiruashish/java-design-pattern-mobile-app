# Projects

> 📌 **TL;DR**  
> Hands-on projects that combine many patterns. The main one is the [FreshCart capstone](capstone.md): six milestones from a domain model to production hardening, with a runnable reference solution and tests.

## Why projects

Reading about one pattern at a time teaches the shapes. Combining them teaches the judgement: which pattern earns its place, how they interact (a Decorator around an Adapter behind a Facade), and where the trade-offs land. The capstone uses the same FreshCart domain as every page, so you can jump back to the deep dive for any piece.

## Project list

| Project | Levels | Time | Patterns |
|---------|--------|------|----------|
| [Capstone: FreshCart](capstone.md) | L1 to L5 | 6 to 20 hours | Value objects, State, Strategy, Specification, Decorator, Adapter, Factory, circuit breaker, Facade, saga, outbox, idempotency key, Observer, aggregator |

## Smaller project ideas

Each of these fits in an evening and reuses the examples from the level pages:

| Idea | Start from |
|------|------------|
| Notification service with SMS, push and email channels, retries and templates | [Bridge](../l2-practitioner/bridge.md), [Template Method](../l1-foundations/template-method.md), [Resilience Patterns](../l4-senior/resilience-patterns.md) |
| Coupon rules engine with a small expression language | [Interpreter](../l2-practitioner/interpreter.md), [Specification](../l2-practitioner/specification.md) |
| Undoable cart editor for the mobile app | [Command](../l2-practitioner/command.md), [Memento](../l2-practitioner/memento.md) |
| Supplier price feed with routing and dead letters | [Messaging Patterns](../l4-senior/messaging-patterns.md) |
| Plugin-based delivery partner SDK | [Designing Frameworks and Libraries](../l5-staff-principal/framework-and-library-design.md) |

## How to work through a project

1. Read the milestone and write down which patterns you think fit, and why.
2. Build the smallest version that passes the acceptance checks.
3. Write the tests before comparing with the reference solution.
4. Compare, then note one thing you'd keep from yours and one from the reference.
5. Run `bash examples/run-all.sh` to see that every example, including the capstone, still produces its documented output.
