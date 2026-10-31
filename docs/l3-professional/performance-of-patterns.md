---
tags: [L3, performance]
---
# Performance Cost of Patterns

<span class="badge-l3">L3 🟡 Professional</span> · ⏱ 30 min read

!!! abstract "TL;DR"
    Know what indirection costs on the JVM and how to measure it.

!!! info "This page is being written"
    The full page follows the playbook template (description, how it works, three runnable
    examples, pros and cons, best practices, pitfalls, expert deep dive, interview corner).
    It will cover these subtopics:

    - Virtual calls, inlining and megamorphic call sites
    - Allocation and escape analysis
    - Proxy and reflection overhead
    - Measuring with JMH
    - Startup cost of DI containers


---

[← Previous: Refactoring to Patterns](refactoring-to-patterns.md) · [Next: L4 Senior / Lead →](../l4-senior/index.md)
