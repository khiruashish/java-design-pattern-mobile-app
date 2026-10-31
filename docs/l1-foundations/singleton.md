---
tags: [L1, creational]
---
# Singleton

<span class="badge-l1">L1 🟢 Foundations</span> · ⏱ 25 min read

!!! abstract "TL;DR"
    One instance, global access, and why it's often a smell.

!!! info "This page is being written"
    The full page follows the playbook template (description, how it works, three runnable
    examples, pros and cons, best practices, pitfalls, expert deep dive, interview corner).
    It will cover these subtopics:

    - Eager initialisation
    - Lazy holder idiom
    - Enum singleton
    - Double-checked locking with volatile
    - Breaking singletons: reflection, serialization, class loaders
    - Singleton vs. dependency-injected single instance


---

[← Previous: DRY, KISS, YAGNI and Friends](other-design-principles.md) · [Next: Factory Method →](factory-method.md)
