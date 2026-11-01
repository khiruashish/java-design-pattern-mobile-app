---
tags: [reference]
---
# Further Reading

!!! abstract "TL;DR"
    The books, specifications and references behind this playbook, grouped by level. Start with *Head First Design Patterns* or *Effective Java* if you're new, *Refactoring* and *Java Concurrency in Practice* as you grow, and the architecture and DDD books at L4 and L5. Every item here is a real, published work.

## Foundations (L0 to L2)

| Resource | Why read it |
|----------|-------------|
| Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides, *Design Patterns: Elements of Reusable Object-Oriented Software* (1994) | The original catalogue of 23 patterns; read the intent and applicability sections |
| Eric Freeman and Elisabeth Robson, *Head First Design Patterns*, 2nd ed. (2020) | The friendliest introduction, with Java examples |
| Joshua Bloch, *Effective Java*, 3rd ed. (2018) | Idiomatic Java: static factories, builders, immutability, enums, lambdas |
| Robert C. Martin, *Clean Architecture* (2017) | SOLID principles and dependency rules explained by their author |
| Andrew Hunt and David Thomas, *The Pragmatic Programmer*, 20th anniversary ed. (2019) | DRY, orthogonality and everyday design judgement |
| Steve Freeman and Nat Pryce, *Growing Object-Oriented Software, Guided by Tests* (2009) | Designing with tests and test doubles |
| [Refactoring.Guru: Design Patterns](https://refactoring.guru/design-patterns) | Illustrated pattern catalogue with Java code |

## Professional Java (L3)

| Resource | Why read it |
|----------|-------------|
| Martin Fowler, *Refactoring*, 2nd ed. (2018) | Safe, small steps from smells to better design |
| Joshua Kerievsky, *Refactoring to Patterns* (2004) | Moving towards, and away from, patterns through refactoring |
| Brian Goetz et al., *Java Concurrency in Practice* (2006) | Thread safety, publication and the java.util.concurrent patterns |
| Martin Fowler, *Patterns of Enterprise Application Architecture* (2002) | Repository, unit of work, data mapper, service layer |
| Michael Feathers, *Working Effectively with Legacy Code* (2004) | Seams and characterisation tests |
| [Spring Framework reference documentation](https://docs.spring.io/spring-framework/reference/) | How Spring uses proxies, DI, templates and events |
| [JEP index](https://openjdk.org/jeps/0) | The design notes behind records, sealed types, pattern matching and virtual threads |

## Distributed systems and architecture (L4)

| Resource | Why read it |
|----------|-------------|
| Eric Evans, *Domain-Driven Design* (2003) | Bounded contexts, aggregates, ubiquitous language |
| Vaughn Vernon, *Implementing Domain-Driven Design* (2013) | Practical DDD with code |
| Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns* (2003) | The messaging pattern language: channels, routers, translators, dead letters |
| Chris Richardson, *Microservices Patterns* (2018) | Sagas, outbox, API gateway, CQRS with Java examples |
| Sam Newman, *Building Microservices*, 2nd ed. (2021) | Service boundaries and the cost of distribution |
| Michael T. Nygard, *Release It!*, 2nd ed. (2018) | Stability patterns: timeouts, circuit breakers, bulkheads |
| Martin Kleppmann, *Designing Data-Intensive Applications* (2017) | Replication, consistency, streams and the theory behind the patterns |
| [Resilience4j documentation](https://resilience4j.readme.io/) | Configuration of the resilience patterns used here |
| [Caffeine wiki](https://github.com/ben-manes/caffeine/wiki) | Eviction, refresh and caching behaviour in Java |

## Staff and principal (L5)

| Resource | Why read it |
|----------|-------------|
| Neal Ford, Rebecca Parsons, Patrick Kua, Pramod Sadalage, *Building Evolutionary Architectures*, 2nd ed. (2022) | Fitness functions and guided change |
| Mark Richards and Neal Ford, *Fundamentals of Software Architecture* (2020) | Architecture characteristics and trade-off analysis |
| Neal Ford et al., *Software Architecture: The Hard Parts* (2021) | Decomposition and distributed data trade-offs |
| Jaroslav Tulach, *Practical API Design* (2008) | Designing Java APIs that can evolve |
| Matthew Skelton and Manuel Pais, *Team Topologies* (2019) | Aligning teams and architecture |
| Tanya Reilly, *The Staff Engineer's Path* (2022) | Technical leadership beyond one team |
| *The Java Language Specification* and *The Java Virtual Machine Specification* ([docs.oracle.com/javase/specs](https://docs.oracle.com/javase/specs/)) | Binary compatibility (JLS 13), the memory model (JLS 17), class initialisation (JLS 12.4) |
| Aleksey Shipilëv, [JMM Pragmatics](https://shipilev.net/blog/2014/jmm-pragmatics/) | A practical tour of the Java Memory Model |
| Michael Nygard, [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) (2011) | The original ADR format |

## Interview preparation

| Resource | Why read it |
|----------|-------------|
| Alex Xu, *System Design Interview*, volumes 1 and 2 (2020, 2022) | Structured approach to high-level design rounds |
| This playbook's [Interview Guide](interview/index.md) | Round types, level expectations and practice tasks |

## How to keep learning

- Re-read one GoF pattern a week and find it in code you use (the JDK, Spring, your own services).
- Write an ADR for your next design decision and review it three months later.
- Run the playbook's examples, then break them: remove a timeout, drop `volatile`, publish before commit, and watch what happens.
