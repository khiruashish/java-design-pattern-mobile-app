# How to Use This Playbook

## Learning paths

| You are… | Read | Skip or skim |
|----------|------|--------------|
| New to Java design | L0 → L1 → L2 in order, doing the Practice section on every page | Expert Deep Dive until your second pass |
| A working Java developer (1–3 yrs) | L1 quickly, then L2 and L3 | L0 |
| A senior engineer or tech lead | L3 → L4 → L5, and the Reference section | L0–L1, except the Expert Deep Dive on each page |
| Preparing for interviews | The Interview Corner on each page, then the [Interview guide](interview/index.md) | Long examples |

## Anatomy of a page

Each topic page has the same numbered sections: TL;DR, What It Is, Why It Exists, Mental Model, How It Works, Subtopics, Examples, Pros/Cons & Trade-offs, When to Use / Not Use, Best Practices, Pitfalls, Configuration & Tuning, Security, Performance, Testing, Observability, Version Notes, Expert Deep Dive, Interview Corner, Practice, Quick Revision Card and Further Reading. Sections that don't apply to a topic are left out rather than left empty.

## Running the examples

All examples target **Java 21** and are single-file programs, so you don't need a build tool:

```bash
java ShippingCostDemo.java
```

The same programs live in [`examples/`](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/main/examples) in the repository. CI runs every example and fails if its output no longer matches what the page shows.

## The running domain

Realistic examples use **FreshCart**, an imaginary grocery delivery company with customers, orders, baskets, products, stores, riders and delivery slots. The same names appear on every page and in the [capstone project](projects/capstone.md).

## Conventions

- `!!! warning "Verify against the official docs"` marks anything you should double-check for your exact JDK or library version.
- Opinions are labelled as opinions; industry consensus is labelled as consensus.
