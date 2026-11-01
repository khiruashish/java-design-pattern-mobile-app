# Interview Guide

> 📌 **TL;DR**  
> Design-pattern interviews rarely ask "define Decorator". They give you a problem and watch how you **find the variation, pick the simplest structure, explain the trade-offs and change your mind when constraints change**. This guide explains the common round types, what each level is judged on, and how to practise with the rest of this section.

## Round types

| Round | Typical prompt | What it tests | Practise with |
|-------|----------------|---------------|---------------|
| Concept questions | "When would you choose Strategy over State?" | Vocabulary and intent | [Questions by Level](questions-by-level.md) |
| Low-level design (LLD) | "Design a parking lot / a coupon engine / a cart" | Class design, patterns, SOLID, testability | [Hands-on Tasks](hands-on-tasks.md) |
| Live coding / refactoring | "Here is a 200-line class, make it extensible" | Refactoring steps, tests, naming | [Hands-on Tasks](hands-on-tasks.md), [Refactoring to Patterns](../l3-professional/refactoring-to-patterns.md) |
| System design (HLD) | "Design grocery delivery checkout for 1M orders per day" | Architecture, data consistency, resilience | [Scenario Questions](scenario-questions.md), [Capstone](../projects/capstone.md) |
| Incident / debugging scenario | "Payments are double-charged after a deploy" | Production reasoning | [Scenario Questions](scenario-questions.md) |
| Staff behavioural / architecture | "Tell me about a design decision you reversed" | Judgement, influence, governance | [L5 pages](../l5-staff-principal/index.md) |

## What each level is judged on

| Level | Interviewers look for | Red flags |
|-------|------------------------|-----------|
| L1 Junior | Knows the common patterns by intent; writes clean classes; explains SOLID with an example | Memorised UML without being able to apply it |
| L2 Mid | Picks a pattern for a stated problem; writes tests; knows when *not* to use one | Patterns everywhere; no tests |
| L3 Senior (individual) | Combines patterns; knows Java and Spring idioms (lambdas, records, DI, proxies); discusses performance and concurrency | Ignores thread safety or failure handling |
| L4 Senior / Lead | Distributed patterns (sagas, outbox, idempotency, resilience); clear trade-offs; designs for operations | Hand-waves consistency; no timeouts or retries discussed |
| L5 Staff / Principal | Chooses between options with evidence; plans evolution and migration; considers teams, governance, reversibility | Only one option considered; no cost or risk discussion |

## A repeatable approach for design questions

1. **Clarify** the use cases, scale and what changes most often. Write them down.
2. **Model the domain** with nouns and verbs; find the invariants.
3. **Find the variation points**: what will differ by city, partner, customer type, or over time?
4. **Choose the simplest structure** for each variation (a lambda before a class hierarchy) and name the pattern.
5. **Walk through a scenario** end to end, including a failure.
6. **State trade-offs** and what would make you change the design.
7. **Show how you would test it.**

> 💡 **Say the trade-off out loud**  
> "I'd use a Strategy here because fee rules change per city every month. If they only ever had two variants, an `if` would be simpler." This one sentence shows more seniority than a perfect UML diagram.

## Preparing in two weeks

| Days | Focus |
|------|-------|
| 1–3 | L1 and L2 patterns: read the TL;DR, run the examples, answer the L1–L3 questions |
| 4–6 | Hands-on tasks with a timer (45 minutes each), then compare with the solutions |
| 7–9 | Modern Java, Spring, concurrency and error-handling pages |
| 10–12 | L4 distributed patterns and scenario questions |
| 13–14 | Capstone milestones 3 to 4; mock interview with a friend |

## How this section is organised

- [Questions by Level](questions-by-level.md): graded questions with model answers.
- [Scenario Questions](scenario-questions.md): production incidents and design scenarios.
- [Hands-on Tasks](hands-on-tasks.md): take-home and live-coding tasks with solutions.

Every topic page also ends with an Interview Corner for that topic.
