# Performance Cost of Patterns

L3 🟡 Professional · ⏱ 30 min read

> 📌 **TL;DR**  
> Know what indirection costs on the JVM and how to measure it.

> ℹ️ **This page is being written**  
> The full page follows the playbook template (description, how it works, three runnable
> examples, pros and cons, best practices, pitfalls, expert deep dive, interview corner).
> It will cover these subtopics:
>
> - Virtual calls, inlining and megamorphic call sites
> - Allocation and escape analysis
> - Proxy and reflection overhead
> - Measuring with JMH
> - Startup cost of DI containers

---

[← Previous: Refactoring to Patterns](refactoring-to-patterns.md) · [Next: L4 Senior / Lead →](../l4-senior/index.md)
