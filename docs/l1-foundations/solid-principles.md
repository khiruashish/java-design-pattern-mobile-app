---
tags: [L1, core]
---
# SOLID Principles

<span class="badge-l1">L1 🟢 Foundations</span> · <span class="read-time">⏱ 55 min read</span> · Prerequisites: [OOP Refresher for Patterns](oop-refresher.md)

!!! abstract "TL;DR"
    SOLID is five principles for classes that are easy to change: one reason to change per class, extend without editing, subtypes that keep their parent's promises, small interfaces, and dependencies on abstractions. They are the "why" behind most design patterns: when you can name which principle a design breaks, you usually know which pattern fixes it.

## 1. What It Is

**Plain English.** SOLID is a checklist of five questions to ask about a class: Does it do one job? Can I add behaviour without editing it? Can a subclass stand in for its parent safely? Does it force callers to depend on methods they don't use? Does it depend on details or on abstractions?

**Precise definition.** The acronym, popularised by Robert C. Martin (the name was suggested by Michael Feathers), stands for:

| Letter | Principle | Short statement |
|--------|-----------|-----------------|
| **S** | Single Responsibility (SRP) | A module should be responsible to one, and only one, actor (one reason to change). |
| **O** | Open/Closed (OCP) | Software entities should be open for extension but closed for modification (Bertrand Meyer, 1988). |
| **L** | Liskov Substitution (LSP) | Objects of a subtype must be usable wherever the supertype is expected without breaking correctness (Barbara Liskov, 1987). |
| **I** | Interface Segregation (ISP) | Clients should not be forced to depend on methods they do not use. |
| **D** | Dependency Inversion (DIP) | High-level policy should not depend on low-level details; both should depend on abstractions. |

New terms: an **actor** is a group of people who request changes (marketing, finance, rider operations). **Policy** is the business logic; **details** are databases, frameworks, email providers.

## 2. Why It Exists

Code that's hard to change fails in predictable ways: one change breaks something unrelated (fragility), a small change needs edits in many places (rigidity), and parts can't be reused or tested alone (immobility). Martin described these "design smells" in the early 2000s and collected the principles that prevent them. Before SOLID was a shared checklist, teams learned these lessons separately and painfully, usually through production incidents caused by "small" changes.

## 3. Mental Model / Analogy

**Analogy.** A well-run FreshCart store: each person has one job (the packer packs, the cashier takes payment: **S**). A new payment terminal plugs into the standard socket without rewiring the counter (**O**). Any trained cashier can cover any till (**L**). The rider app shows riders only rider functions, not the store's stock-taking screens (**I**). The store manager's process says "use a delivery partner", not "call Imran on his personal phone" (**D**).

**Technical model.** Each principle limits how change spreads through the dependency graph. SRP and ISP keep each node small, OCP and DIP point the arrows toward stable abstractions, and LSP makes sure that substituting one node for another along those arrows is safe.

## 4. How It Works

DIP is the principle that changes the *shape* of the dependency graph. Without it, business policy depends directly on details. With it, both depend on an interface owned by the policy side:

```mermaid
flowchart LR
    subgraph Before["Before: policy depends on details"]
        S1["OrderService"] --> D1["MySqlOrderDao"]
        S1 --> E1["SmtpEmailClient"]
    end
    subgraph After["After: details depend on abstractions owned by the policy"]
        S2["OrderService"] --> I1["OrderRepository (interface)"]
        S2 --> I2["Notifier (interface)"]
        D2["MySqlOrderRepository"] -.->|implements| I1
        E2["EmailNotifier"] -.->|implements| I2
    end
```

The arrows from the details now point *toward* the policy layer: the dependency is inverted. That's what lets you test `OrderService` with in-memory fakes and swap the email provider without touching it.

## 5. Subtopics

Each principle below gets the same deep dive: what it means, why it helps, a runnable before/after example in FreshCart, a pros/cons table, when to use and avoid it, where you already see it in real Java, a best practice and the most common mistake.

### S: Single Responsibility Principle

**Description.** A class should have one reason to change. Robert C. Martin's sharper version: a module should be *responsible to one, and only one, actor*, meaning one group of people who request changes. An invoice service that calculates tax (Finance), lays out the invoice (Marketing) and sends it (Platform) has three actors, so three groups edit the same file for unrelated reasons, and one group's change can break another's feature.

**Advantages / features.**

- Changes requested by one team can't break another team's behaviour.
- Smaller classes are easier to read, name, review and test.
- Each part can be reused (the calculator also feeds the refund screen).
- Merge conflicts drop, because different teams edit different files.

**Production example: splitting an invoice service by actor.**

```java title="SrpDemo.java"
--8<-- "examples/l1-foundations/solid-principles/SrpDemo.java"
```

1. **Before:** tax rule, text layout and delivery mixed in one method. A layout tweak means re-testing tax.
2. Finance's class: validated tax rate, `BigDecimal` with explicit rounding, rejects empty invoices.
3. Marketing's class: only formatting. It can change freely without touching money.
4. Platform's interface: email today, SMS or PDF-to-S3 tomorrow, without editing the others.

**Run it:** `java SrpDemo.java`

```text
--8<-- "examples/l1-foundations/solid-principles/SrpDemo.expected.txt"
```

| Pros | Cons |
|------|------|
| Changes are isolated per actor | More classes and constructor wiring |
| Smaller, focused unit tests | Logic for one use case is spread over several files |
| Parts are reusable (calculator in refunds, renderer in emails) | Over-splitting creates "one method per class" noise |
| Fewer merge conflicts across teams | Needs judgement to identify the actors |

**Use it when** a class is edited for unrelated reasons, its tests need lots of unrelated setup, or different teams keep touching the same file. **Avoid over-applying it** to small, stable classes with one obvious job; splitting a 30-line class into five adds cost and no benefit.

**Real-world Java.** Spring separates `@Controller` (HTTP), `@Service` (business process) and `@Repository` (persistence) for exactly this reason. In the JDK, `java.time.format.DateTimeFormatter` formats dates, while `LocalDate` only represents them.

**Best practice:** name each class after its single job (`InvoiceCalculator`, not `InvoiceManager`); "Manager", "Helper" and "Util" are SRP warning signs.
**Gotcha:** SRP isn't "one method per class". A class with ten methods that all serve Finance is fine.

### O: Open/Closed Principle

**Description.** Software entities should be *open for extension, closed for modification* (Bertrand Meyer, 1988). In Java: put an interface on the part that keeps changing, and add new behaviour as new implementations, so tested code isn't edited. Every new payment method, promotion or delivery partner should be a new class plus one registration line, not another `case` in a growing `switch`.

**Advantages / features.**

- New features can't break existing ones, because existing code isn't edited.
- Each variant is tested in isolation.
- Several developers can add variants in parallel without conflicts.
- Variants can be enabled per country or per customer through configuration.

**Production example: a payment-method registry.**

```java title="OcpDemo.java"
--8<-- "examples/l1-foundations/solid-principles/OcpDemo.java"
```

1. **Before:** every new method edits this `switch` and risks the existing cases.
2. The extension point: everything that varies per payment method is behind one interface.
3. Variants can carry their own rules: cash on delivery only up to $100.
4. UPI was added for the India launch without touching any line above it.
5. The registry fails fast on duplicates and lists the available methods in its error message.

**Run it:** `java OcpDemo.java`

```text
--8<-- "examples/l1-foundations/solid-principles/OcpDemo.expected.txt"
```

| Pros | Cons |
|------|------|
| Adding a variant can't break existing ones | You must predict the axis of change |
| Variants are tested alone | More types and a registry to understand |
| Parallel work without merge conflicts | Cross-cutting changes (to every variant) touch many files |
| Variants can be selected by configuration | Behaviour is no longer visible in one `switch` |

**Use it when** the same kind of change has happened two or three times and more are coming (payment methods, promotions, notification channels). **Avoid it when** the set is small and stable; a `switch` over a sealed type or an `enum` is simpler and the compiler checks it's complete.

**Real-world Java.** `java.util.Comparator`, JDBC drivers (`java.sql.Driver` found through `ServiceLoader`), Servlet filters and Spring's `HandlerInterceptor` all let you add behaviour without editing the framework.

**Best practice:** combine OCP with a registry or DI (`List<PaymentMethod>` injected by Spring) so adding a variant is truly one new class.
**Gotcha:** OCP is about the code that *uses* the variants. The composition root (where variants are registered) is expected to change.

### L: Liskov Substitution Principle

**Description.** If `S` is a subtype of `T`, code that works with a `T` must keep working when given an `S` (Barbara Liskov, 1987). In practice a subtype must not require more (stricter inputs), promise less (weaker results), throw new exceptions, or break the supertype's invariants. LSP is what makes polymorphism safe: Strategy, Decorator and Proxy all assume any implementation can be swapped in.

**Advantages / features.**

- You can swap implementations (in-memory, database, remote) without surprises.
- Callers don't need `instanceof` checks or special cases.
- Contract tests can verify every implementation the same way.
- Bugs from "almost compatible" subclasses are caught before production.

**Production example: one contract test for every stock implementation.**

```java title="LspContractDemo.java"
--8<-- "examples/l1-foundations/solid-principles/LspContractDemo.java"
```

1. The contract is written down: what callers may rely on, including "never throws for an unknown SKU" and "never negative".
2. A violating implementation: it throws, and lets stock go negative to support backorders.
3. The fix: keep the contract, and expose backorders as a separate, explicit capability.
4. The same checks run against every implementation. In a real project this is an abstract JUnit test class that each implementation's test extends.

**Run it:** `java LspContractDemo.java`

```text
--8<-- "examples/l1-foundations/solid-principles/LspContractDemo.expected.txt"
```

| Pros | Cons |
|------|------|
| Safe substitution: polymorphism you can trust | Forces you to drop "natural" but broken hierarchies |
| No `instanceof` special cases in callers | Contracts must be written down and tested |
| Contract tests catch violations early | Extra interfaces for optional capabilities |
| Enables Strategy, Decorator, Proxy | Non-functional behaviour (speed, blocking) is hard to specify |

**Use it** for every interface with more than one implementation, especially ones other teams implement. **Watch for violations** when an override throws `UnsupportedOperationException`, ignores its input, or adds a precondition the parent didn't have.

**Real-world Java.** The `java.util.Collection` Javadoc marks `add` and `remove` as *optional operations*, so `List.of(...)` throws on `add`. It's a documented, deliberate LSP trade-off, and the reason you should never assume a `List` you receive is mutable.

**Best practice:** write a reusable contract test and run it against every implementation, including fakes used in other tests.
**Gotcha:** LSP also covers behaviour: an implementation that blocks for 30 seconds where the contract implies fast in-memory access breaks callers just as surely as an exception.

### I: Interface Segregation Principle

**Description.** Clients should not be forced to depend on methods they don't use. A fat interface forces implementers to write fake methods that throw, and forces clients to depend on (and recompile for) changes they don't care about. Split it into *role interfaces* shaped by what each client needs; one class can still implement several roles.

**Advantages / features.**

- Implementers only implement what they can really do: no `UnsupportedOperationException`.
- Clients declare exactly what they need, which documents the dependency.
- Test doubles are tiny (often a lambda).
- Least privilege: a component given only `Scale` can't print or scan.

**Production example: store devices with role interfaces.**

```java title="IspDemo.java"
--8<-- "examples/l1-foundations/solid-principles/IspDemo.java"
```

1. **Before:** one interface for every device capability.
2. The printer must "implement" scanning and weighing, and fails at runtime.
3. Three role interfaces, each with one capability.
4. A checkout counter really has all three, so it implements all three.
5. The produce station asks only for a scale and a printer, and validates the scale's reading.

**Run it:** `java IspDemo.java`

```text
--8<-- "examples/l1-foundations/solid-principles/IspDemo.expected.txt"
```

| Pros | Cons |
|------|------|
| No fake or throwing methods | More interfaces to name and discover |
| Clients state exactly what they use | Over-splitting into one-method interfaces with no clear role |
| Tiny test doubles (lambdas) | Some clients need several interfaces injected |
| Least-privilege access by design | Refactoring a published fat interface is a breaking change |

**Use it when** implementers leave methods empty or throwing, or when different clients use clearly different subsets of an interface. **Avoid** splitting interfaces that are always used together.

**Real-world Java.** `java.lang.Readable`, `Appendable`, `AutoCloseable`, `Comparable` and `Iterable` are all small role interfaces that many unrelated classes implement. Spring Data offers `CrudRepository`, `PagingAndSortingRepository` and the minimal `Repository` marker, so you can expose only the operations you want.

**Best practice:** design interfaces from the *client's* side ("what does the produce station need?"), not from the implementation's side ("what can the device do?").
**Gotcha:** ISP doesn't mean every interface has one method; a cohesive set of methods always used together belongs in one interface.

### D: Dependency Inversion Principle

**Description.** High-level modules (business policy) should not depend on low-level modules (SMTP, HTTP, databases); both should depend on abstractions, and the abstractions belong to the high-level side. The concrete classes are chosen in one place, the *composition root* (your `main`, or a DI container such as Spring).

**Advantages / features.**

- Business rules are testable without infrastructure (no SMTP server, no database).
- Infrastructure can be replaced (email → Slack) without editing business code.
- Cross-cutting behaviour (retries, metrics, caching) can be added as decorators around the abstraction.
- Configuration can choose implementations per environment.

**Production example: low-stock alerts with pluggable channels.**

```java title="DipDemo.java"
--8<-- "examples/l1-foundations/solid-principles/DipDemo.java"
```

1. **Before:** the policy creates its own `SmtpClient`, so it can't be tested or switched to Slack.
2. Abstractions owned by the policy: "give me stock levels" and "send an alert".
3. Retries added once, for every channel, as a decorator.
4. The composition root chooses the channel from configuration and fails fast on unknown values.
5. In tests, a method reference (`captured::add`) is the whole test double.

**Run it:** `java DipDemo.java`

```text
--8<-- "examples/l1-foundations/solid-principles/DipDemo.expected.txt"
```

| Pros | Cons |
|------|------|
| Business logic testable without infrastructure | Indirection: you must find the implementation |
| Swap infrastructure without touching policy | Wiring code or a DI container to maintain |
| Decorators add retries, caching, metrics once | Abstractions designed too early can be wrong |
| Configuration chooses implementations per environment | Misconfigured wiring fails only at startup or runtime |

**Use it at real boundaries:** databases, external APIs, messaging, email, file systems, the clock and random numbers. **Avoid** wrapping stable, in-process libraries (`java.util`, `java.time` types) behind your own interfaces.

**Real-world Java.** Spring's whole IoC container exists to implement DIP: you depend on `JavaMailSender` or your own `OrderRepository` interface, and Spring injects the implementation. JDBC code depends on `javax.sql.DataSource`, not on a specific connection pool.

**Best practice:** inject through constructors, keep `new SomeInfrastructureClient()` out of business classes, and have exactly one composition root.
**Gotcha:** an interface in the *infrastructure* package that mirrors the vendor's API (`SmtpClientInterface`) isn't inversion; the abstraction must speak the business's language (`AlertChannel.alert(message)`).

### SOLID at a glance

| Principle | Question to ask in review | Typical fix | Pattern it leads to |
|-----------|---------------------------|-------------|---------------------|
| SRP | Who would ask for changes to this class? | Split by actor | Facade, Service Layer |
| OCP | Where will the next variant go? | Interface + registry | Strategy, Decorator, Plugin |
| LSP | Can every implementation pass the same contract test? | Narrow the contract or split capabilities | Template Method (done right), Composite |
| ISP | Does any client or implementer ignore methods? | Role interfaces | Adapter, Proxy |
| DIP | Can I test this without the database, network and clock? | Inject abstractions owned by the policy | Dependency Injection, Hexagonal architecture |

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L1)

**Scenario:** FreshCart prints shelf labels. A helper written for `Rectangle` sets width then height and expects the area to be width × height. Someone adds `Square extends Rectangle` "because a square is a rectangle". This breaks LSP.

**Code / Config:**

```java title="LiskovDemo.java"
--8<-- "examples/l1-foundations/solid-principles/LiskovDemo.java"
```

1. Each setter changes both sides, to keep the square square.
2. This code is correct for any `Rectangle`, but a `Square` silently breaks its assumption.
3. The fix: immutable values in a sealed hierarchy. Nothing can be "resized into" an invalid state.

**Run it:**

```bash
java LiskovDemo.java
```

**Output:**

```text
--8<-- "examples/l1-foundations/solid-principles/LiskovDemo.expected.txt"
```

**What to notice:**

- No exception, no compiler warning, just a wrong number: LSP violations are quiet.
- The problem is the mutable *setters*, not the geometry. Immutable `Rect` and `Sq` have no contract to break.
- "Is-a" in the real world isn't enough; the subtype must behave like the supertype.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** The original `OrderService` calculated prices, saved to MySQL and sent emails in one class, so pricing campaigns, database migrations and email template changes all edited the same file. The refactored version applies SRP (the service only coordinates), OCP (pricing rules are pluggable) and DIP (the service owns `OrderRepository` and `Notifier`).

**Code / Config:**

```java title="OrderServiceDemo.java"
--8<-- "examples/l1-foundations/solid-principles/OrderServiceDemo.java"
```

1. **OCP:** a new campaign is a new `PricingRule`, applied in order.
2. **DIP:** the abstractions belong to the business side; the database and email code implement them.
3. **SRP:** the service changes only if the *process* of placing an order changes.
4. The composition root: the only place that names concrete classes.

**Run it:**

```bash
java OrderServiceDemo.java
```

**Output:**

```text
--8<-- "examples/l1-foundations/solid-principles/OrderServiceDemo.expected.txt"
```

**What to notice:**

- FC-5001 is under $50, so it pays the $4.99 fee: $34.99. FC-5002 gets free delivery and 10% off: $54.00.
- Swapping `InMemoryOrders` for a JDBC repository changes `main` only.
- Rule order matters (fee before or after discount). That's now an explicit list you can see and test, not code buried in a method.

### Example 3: Production-grade

**Scenario:** FreshCart refunds must be idempotent (a retried request never pays twice), allowed only within 48 hours of delivery, capped at the amount paid, and fully audited. The service must be testable without a payment provider, a database or the real clock. ISP keeps each dependency small; DIP lets tests replace every one of them.

**Code / Config:**

```java title="RefundServiceDemo.java"
--8<-- "examples/l1-foundations/solid-principles/RefundServiceDemo.java"
```

1. **ISP:** the refund flow needs "find an order", not the 20 methods of a full order store.
2. **DIP:** the gateway is an interface the service owns; the provider's SDK hides behind an adapter.
3. Time is a dependency too. A fixed `Clock` makes the 48-hour rule testable and the output deterministic.
4. Idempotency by request key: the duplicate is detected before any money moves.
5. One class can implement several small roles; clients still see only what they need.

**Run it:**

```bash
java RefundServiceDemo.java
```

**Output:**

```text
--8<-- "examples/l1-foundations/solid-principles/RefundServiceDemo.expected.txt"
```

**What to notice:**

- The retry with key `req-1` returns "already refunded" without calling the gateway.
- FC-6002 was delivered on 29 October; the window closed 48 hours later, so the refund on 2 November is rejected.
- Every outcome, including rejections, is written to the audit log.
- `main` builds the gateway from a lambda: a one-method interface is the cheapest possible test double.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Simplicity | Each class is small and focused | More classes and interfaces overall |
| Performance | Usually no measurable cost | Extra indirection on very hot paths |
| Scalability | Teams change their parts without colliding | Wiring grows with the number of components |
| Operability | Clear seams for logging, metrics and auditing | Behaviour spread across classes is harder to trace |
| Security | Narrow interfaces limit what each component can do | More extension points to review |
| Testability | Fakes for every dependency, including time | Over-abstracted code needs many fakes |
| Cost | Cheaper, safer changes over the code base's life | Up-front design effort |
| Learning curve | Five memorable checks | The principles are easy to quote and hard to apply well |

## 8. When to Use / When NOT to Use

**Apply SOLID when:**

- code is long-lived and changed by several people or teams;
- business rules change often (pricing, refunds, promotions);
- you need to test business logic without infrastructure.

**Go easy when:**

- writing scripts, prototypes or one-off migrations;
- the code is simple data plumbing with no rules;
- applying a principle would add abstraction for a change nobody expects.

See [DRY, KISS, YAGNI and Friends](other-design-principles.md) for the principles that balance SOLID.

## 9. Best Practices

1. **Split classes by reason to change (actor), not by size.** *(Consensus.)*
2. **Let the business side own the interfaces it depends on.** *(Consensus: DIP.)*
3. **Inject `Clock`, ID generators and randomness.** *(Consensus.)*
4. **Prefer immutable types to avoid LSP traps in hierarchies.** *(Opinion, widely shared.)*
5. **Design interfaces from the client's needs (role interfaces).** *(Consensus: ISP.)*
6. **Keep one composition root per application.** *(Opinion.)*
7. **Don't apply OCP pre-emptively; wait for the second change on an axis.** *(Opinion.)*

## 10. Pitfalls & Anti-Patterns

!!! danger "God service"
    **Symptom:** `OrderService` has 3,000 lines, and every sprint's changes conflict in it.
    **Root cause:** responsibilities for several actors (pricing, notifications, persistence, fraud) in one class.
    **Fix:** extract one responsibility per change request, behind an interface the service owns.

!!! danger "Refusing subclass"
    **Symptom:** `UnsupportedOperationException` from a subclass method in production.
    **Root cause:** a subtype that can't honour its parent's contract (LSP violation).
    **Fix:** split the interface (ISP) so the subtype only promises what it can do, or use composition.

!!! danger "Double refunds (production failure mode)"
    **Symptom:** customers occasionally got refunded twice after the payment provider timed out.
    **Root cause:** the client retried the refund request, and the service had no idempotency key, so both requests paid out.
    **Fix:** require an idempotency key per refund request, check it before paying (Example 3), and pass the same key to the payment provider so its side is idempotent too.

!!! danger "Interfaces everywhere"
    **Symptom:** `UserServiceImpl implements UserService` for every class, with no second implementation and no fakes.
    **Root cause:** DIP applied mechanically.
    **Fix:** add interfaces at real boundaries (infrastructure, other teams, things you fake in tests).

## 12. Security Considerations

ISP is also a least-privilege tool: a component that receives only `OrderLookup` can't write orders or issue refunds, even if it's compromised or buggy. Audit logs (as in Example 3) belong behind an interface so that every implementation, including tests, records rejections as well as successes.

## 13. Performance & Scalability

SOLID rarely affects runtime performance; interface calls are cheap after JIT inlining. It does affect *team* scalability: SRP and ISP reduce merge conflicts and the blast radius of a change, which matters more than nanoseconds in most business systems.

## 14. Testing It

DIP is what makes this test possible: a fixed clock, an in-memory store and a recording gateway, with no mocking library.

```java title="RefundServiceTest.java"
--8<-- "examples/l1-foundations/solid-principles/RefundServiceTest.java"
```

## 15. Observability & Operations

Give audit and metrics their own interfaces (as `AuditLog` here) so business code can report outcomes without knowing about the logging framework. For refunds, alert on the rate of `refund_duplicate` (clients retrying too aggressively) and `refund_rejected` by reason.

## 16. Version Notes

The principles don't depend on the Java version, but modern Java makes them cheaper: records make immutable values (helping LSP) one line; sealed interfaces (17) let you close a hierarchy deliberately; lambdas make one-method interfaces (DIP/ISP) easy to implement in tests.

## 17. Expert Deep Dive

- **SRP is about people.** Martin's later formulation ("responsible to one actor") is the useful one. Seniors map classes to the teams and stakeholders who request changes; this is the same thinking that later draws module and service boundaries (Conway's Law).
- **OCP is strategic, not total.** You choose which changes to be closed against. Experienced engineers use change history and roadmap knowledge to pick those axes, and accept that other changes will need edits.
- **LSP is about contracts, including non-functional ones.** A subtype that's 100× slower, or that blocks where the parent didn't, can break callers too. Treat timeouts, thread-safety and exceptions as part of the contract.
- **DIP at architecture scale** becomes hexagonal / clean architecture: the domain owns ports, infrastructure provides adapters. See [Architectural Patterns](../l4-senior/architectural-patterns.md).
- **In a design review**, ask: "Who will ask for changes to this class?", "What's the next rule we'll add and where does it go?", and "Can I test this without the database and the clock?". Those three questions catch most SOLID problems.

!!! example "War story: the refund that ran on summer time"
    A grocery company's refund window was implemented with `LocalDateTime.now()` in the service. Twice a year, when the clocks changed, a handful of refunds near the deadline were wrongly rejected or accepted, and support tickets spiked. Nobody could reproduce it in tests, because the tests ran on whatever the machine's clock said. The fix was injecting a `Clock`, storing delivery times as `Instant`, and adding tests at the exact deadline (like `refundWindowEndsExactly48HoursAfterDelivery`). **Lesson:** the system clock is an infrastructure detail; DIP applies to it too.

## 18. Interview Corner

=== "🟢 L1–L2"

    1. **What does SOLID stand for?** **Single responsibility, open/closed, Liskov substitution, interface segregation, dependency inversion.**
    2. **Explain SRP.** A class should have **one reason to change**, serving **one actor**.
    3. **Give an LSP violation.** **Square extending a mutable Rectangle**: setting the width changes the height.
    4. **What is ISP?** Clients **shouldn't depend on methods they don't use**; prefer small role interfaces.
    5. **How does DIP help testing?** Depending on **interfaces** lets tests pass **fakes** instead of real infrastructure.

=== "🟡 L3"

    1. **DIP vs. dependency injection?** DIP is **who owns the abstraction** (the policy side); DI is the **technique of passing dependencies in**.
    2. **How do you decide where to apply OCP?** Use **change history** and the roadmap; apply on the **axis that keeps changing**.
    3. **Is `Collections.unmodifiableList` an LSP violation?** Arguably yes; the JDK documents mutators as **optional operations**, a deliberate trade-off.
    4. **How would you test time-based rules?** Inject a **`Clock`** and use **`Clock.fixed`** in tests, including at the **exact boundary**.
    5. **Signs a class breaks SRP?** **Many unrelated imports**, frequent merge conflicts, changes requested by **different teams**.

=== "🔴 L4–L5"

    1. **How do SOLID principles show up at service level?** SRP → **service boundaries by business capability**; DIP → **ports and adapters**; ISP → **narrow APIs per consumer**.
    2. **When is applying SOLID wrong?** For **throwaway or very stable code**, or when abstractions are added for **imagined change**.
    3. **Design idempotent refunds across retries and provider timeouts.** **Idempotency key** end to end, **ledger before/after** with a unique constraint, and **pass the key to the provider**.
    4. **How do you raise SOLID awareness in 10 teams without dogma?** Review **real incidents** and change costs, add **review questions** rather than rules.
    5. **Now refunds come from three channels (app, support, chat bot). What changes?** A **single refund policy** behind one port, channel-specific **adapters**, and **per-channel limits** as configuration.

=== "💻 Hands-on"

    **Task:** Add a "loyalty points" pricing rule to Example 2 that takes 1% off for customers with more than 1,000 points, without changing `OrderService`.

    ??? success "Solution"
        ```java
        record LoyaltyDiscount(java.util.Map<String, Integer> pointsByEmail) implements PricingRule {
            public long adjust(Order o, long total) {
                int points = pointsByEmail.getOrDefault(o.customerEmail(), 0);
                return points > 1_000 ? total - o.basketInCents() / 100 : total;
            }
        }
        // main: rules.add(new LoyaltyDiscount(Map.of("ravi@example.com", 1_500)));
        ```
        `OrderService` is untouched: the change is one new class and one line in the composition root.

## 19. Practice

- [ ] List the actors for a class in your current project. Does it serve more than one?
    ??? tip "Hint"
        Look at the last ten commits that touched it and who asked for each.
- [ ] Add an SMS notifier to Example 2 that sends alongside email.
    ??? tip "Hint"
        Write a `CompositeNotifier` that holds a list of notifiers: a preview of the Composite pattern.
- [ ] In Example 3, make the ledger check and record atomic.
    ??? tip "Hint"
        Replace `alreadyRefunded` + `record` with one `boolean tryReserve(String key)` that uses `Set.add`'s return value.
- [ ] Find an LSP violation in code you know (an override that throws or ignores its input).
    ??? tip "Hint"
        Search for `UnsupportedOperationException` and empty overrides.

## 20. Quick Revision Card

!!! tip "Remember in 30 seconds"
    - **S:** one actor, one reason to change.
    - **O:** add behaviour by adding classes on the axis that changes.
    - **L:** subtypes keep the parent's promises; immutability helps.
    - **I:** small role interfaces shaped by clients.
    - **D:** policy owns the interfaces; details implement them; wire in one place.

    ```java
    final class RefundService {                     // S: coordinates refunds only
        RefundService(OrderLookup orders,           // I: small role interface
                      PaymentGateway gateway,       // D: owned abstraction
                      Clock clock) { /* ... */ }
    }
    ```

## 21. Further Reading

- Robert C. Martin, *Agile Software Development, Principles, Patterns, and Practices* (2002).
- Robert C. Martin, *Clean Architecture* (2017), part III "Design Principles".
- Barbara Liskov, "Data Abstraction and Hierarchy" (OOPSLA 1987 keynote); Liskov and Wing, "A Behavioral Notion of Subtyping" (1994).
- Bertrand Meyer, *Object-Oriented Software Construction* (1988).

---

[← Previous: OOP Refresher for Patterns](oop-refresher.md) · [Next: DRY, KISS, YAGNI and Friends →](other-design-principles.md)
