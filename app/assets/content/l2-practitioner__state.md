# State

L2 🔵 Practitioner · ⏱ 40 min read · Prerequisites: [Strategy](../l1-foundations/strategy.md), [Command](command.md)

> 📌 **TL;DR**  
> State lets an object change its behaviour when its internal state changes, so "what does cancel do?" is answered by the current state instead of `if`/`switch` chains in every method. Java gives you several ways to build it: one class per state (classic GoF), an enum with allowed transitions, sealed records with an exhaustive `switch` (modern Java), or a transition table (state machine as data). In production, add guards, optimistic versioning, idempotent events and a history.

## 1. What It Is

**Plain English.** A FreshCart order is placed, packed, out for delivery, delivered, or cancelled. "Cancel" means a free cancellation when placed, a restock plus refund when packed, and "too late, refuse at the door" when the rider is on the way. Instead of one method full of `if (status == ...)`, each state knows what each action means.

**Definition (GoF).** "Allow an object to alter its behavior when its internal state changes. The object will appear to change its class." Parts:

- **Context:** the object clients use (`Order`); holds the current state and delegates to it.
- **State:** an interface with one method per event or action (`pack`, `dispatch`, `cancel`).
- **Concrete states:** implement the behaviour for that state and decide the next state.

A related, more general idea is the **finite state machine**: a set of states, events, and transitions (from, event → to), often with guards (conditions) and actions (side effects).

## 2. Why It Exists

Status fields plus conditionals spread state logic across every method: `pack()`, `dispatch()`, `cancel()` each switch on status, and adding a state (`RETURNED`) means editing all of them and hoping nothing is missed. Invalid transitions (cancel after delivery) slip through as silent bugs. The State pattern groups behaviour by state, makes illegal transitions explicit, and turns "what can happen next?" into something you can read, test, and draw.

## 3. Mental Model / Analogy

**Analogy.** A traffic light. The same "timer expired" event means "go to yellow" when green and "go to red" when yellow. The light's behaviour depends entirely on which state it's in, and some transitions (green straight to red) never happen.

**Technical model.** A directed graph: nodes are states, edges are transitions labelled by events. The context holds a pointer to the current node. Handling an event follows the matching edge (if any), runs its action, and moves the pointer. Everything else is an illegal transition.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### State interface per state

**Description.** The classic GoF form: an interface with one method per action, one class per state, and a context that delegates and stores whatever state the method returns. Default methods can reject every action, so each state only overrides what it allows.

**Advantages / features.** Behaviour grouped by state; adding a state is adding a class; illegal actions handled in one place.

**Before (status switches in every method) and after (state classes):**

**StatePerClassDemo.java**

```java
/** State: an object changes behaviour when its internal state changes, by delegating to a state object. */
public class StatePerClassDemo {

    // ===================== Before: every method switches on a status string =====================
    static final class OrderBefore {
        String status = "PLACED";
        String dispatch() {
            switch (status) {
                case "PLACED": return "can't dispatch: not packed yet";
                case "PACKED": status = "OUT_FOR_DELIVERY"; return "dispatched";
                case "CANCELLED": return "can't dispatch: cancelled";
                default: return "can't dispatch from " + status;
            }
        }
        // ...and the same switch again in pack(), deliver(), cancel()
    }

    // ===================== After: one class per state =====================
    interface OrderState {
        default OrderState pack(Order o) { return reject(o, "pack"); }
        default OrderState dispatch(Order o) { return reject(o, "dispatch"); }
        default OrderState deliver(Order o) { return reject(o, "deliver"); }
        default OrderState cancel(Order o) { return reject(o, "cancel"); }
        private OrderState reject(Order o, String action) { o.log("  can't " + action + " when " + name()); return this; }
        default String name() { return getClass().getSimpleName(); }
    }
    static final class Placed implements OrderState {
        public OrderState pack(Order o) { o.log("  packed"); return new Packed(); }
        public OrderState cancel(Order o) { o.log("  cancelled, nothing to refund yet"); return new Cancelled(); }
    }
    static final class Packed implements OrderState {
        public OrderState dispatch(Order o) { o.log("  handed to rider"); return new OutForDelivery(); }
        public OrderState cancel(Order o) { o.log("  cancelled, items returned to shelf"); return new Cancelled(); }
    }
    static final class OutForDelivery implements OrderState {
        public OrderState deliver(Order o) { o.log("  delivered"); return new Delivered(); }
    }
    static final class Delivered implements OrderState { }
    static final class Cancelled implements OrderState { }

    static final class Order {
        private OrderState state = new Placed();
        void log(String s) { System.out.println(s); }
        void pack() { state = state.pack(this); }
        void dispatch() { state = state.dispatch(this); }
        void deliver() { state = state.deliver(this); }
        void cancel() { state = state.cancel(this); }
        String state() { return state.name(); }
    }

    public static void main(String[] args) {
        System.out.println("before: " + new OrderBefore().dispatch());
        Order o = new Order();
        o.dispatch(); o.pack(); o.dispatch(); o.cancel(); o.deliver();
        System.out.println("final state: " + o.state());
    }
}
```

1. Before: every action repeats a switch on the status string; a new status edits all of them.
2. The state interface: actions are rejected by default, so states override only what they allow.
3. Each state decides its next state and its side effects ("cancel" differs between `Placed` and `Packed`).
4. The context delegates and swaps its state; callers never see a status check.

**Run it:** `java StatePerClassDemo.java`

```text
before: can't dispatch: not packed yet
  can't dispatch when Placed
  packed
  handed to rider
  can't cancel when OutForDelivery
  delivered
final state: Delivered
```

| Pros | Cons |
|------|------|
| Behaviour grouped by state | Many small classes |
| Illegal actions rejected by default | The overall flow is spread across classes |
| States can hold state-specific logic | Persisting the state needs a mapping to a column |

**Use it** when states have substantial, different behaviour. **Avoid** it when states only differ in which transitions are allowed; an enum or table is simpler.
**Real-world Java:** `java.util.Iterator` implementations are tiny state machines; TCP connection handling in network stacks; game entities.
**Best practice:** make states stateless singletons or records, keeping data in the context. **Gotcha:** if states call back into the context and the context calls states, it's easy to create loops; let states return the next state instead of setting it.

### Enum-based state machines

**Description.** When states mostly differ in **which transitions are allowed**, an enum is the most compact model: each constant declares its allowed next states, and one `moveTo` method enforces them. It maps directly to a database column.

**Advantages / features.** One small type, easy persistence (`@Enumerated(STRING)`), exhaustive `switch` over constants, `EnumSet`/`EnumMap` performance.

**EnumStateMachineDemo.java**

```java
import java.util.EnumSet;
import java.util.Set;

/** A compact state machine: an enum where each constant declares which states it may move to. */
public class EnumStateMachineDemo {

    enum OrderStatus {
        PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

        Set<OrderStatus> allowedNext() {
            return switch (this) {
                case PLACED -> EnumSet.of(PACKED, CANCELLED);
                case PACKED -> EnumSet.of(OUT_FOR_DELIVERY, CANCELLED);
                case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
                case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
            };
        }
        boolean isTerminal() { return allowedNext().isEmpty(); }

        OrderStatus moveTo(OrderStatus next) {
            if (!allowedNext().contains(next)) throw new IllegalStateException(this + " -> " + next + " is not allowed");
            return next;
        }
    }

    public static void main(String[] args) {
        OrderStatus s = OrderStatus.PLACED;
        s = s.moveTo(OrderStatus.PACKED);
        s = s.moveTo(OrderStatus.OUT_FOR_DELIVERY);
        System.out.println("now " + s);
        try { s.moveTo(OrderStatus.CANCELLED); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        s = s.moveTo(OrderStatus.DELIVERED);
        System.out.println("now " + s + ", terminal: " + s.isTerminal());
    }
}
```

1. The enum is both the state and the transition rules.
2. Terminal states allow nothing; adding a constant without a `case` won't compile.
3. Every change goes through `moveTo`, which rejects illegal transitions.

**Run it:** `java EnumStateMachineDemo.java`

```text
now OUT_FOR_DELIVERY
OUT_FOR_DELIVERY -> CANCELLED is not allowed
now DELIVERED, terminal: true
```

| Pros | Cons |
|------|------|
| Compact, readable, persistable | Behaviour per state gets crowded in one file |
| Exhaustive switches | No state-specific data (a rider ID only when dispatched) |
| `EnumSet`/`EnumMap` are fast | Guards and actions need extra structure |

**Use** enums for status workflows (orders, tickets, payments). **Avoid** putting large behaviours inside enum constants; move them to services keyed by state.
**Real-world Java:** `Thread.State`, `java.time.DayOfWeek` (cyclic), JPA entities with `@Enumerated` status columns.
**Best practice:** persist enums by name (`EnumType.STRING`), never by ordinal. **Gotcha:** reordering enum constants with `EnumType.ORDINAL` silently corrupts every stored row.

### State with sealed types and switch

**Description.** With Java 21, each state can be a `record` in a sealed hierarchy, carrying only the data that exists in that state (`OutForDelivery` has a rider ID; `Delivered` has who received it). Transitions are an exhaustive `switch` over (state, event) with record patterns and guards. Impossible combinations, like a delivered order without a delivery time, can't be represented.

**Advantages / features.** "Make illegal states unrepresentable"; state-specific data is type-checked; the whole machine fits in one readable function.

**SealedStateDemo.java**

```java
import java.time.Instant;

/** States as sealed records: each state carries ONLY the data that exists in that state; transitions are an exhaustive switch. */
public class SealedStateDemo {

    sealed interface OrderState permits Placed, Packed, OutForDelivery, Delivered, Cancelled { }
    record Placed(Instant at) implements OrderState { }
    record Packed(Instant at, int bags) implements OrderState { }
    record OutForDelivery(String riderId) implements OrderState { }
    record Delivered(Instant at, String receivedBy) implements OrderState { }
    record Cancelled(String reason) implements OrderState { }

    sealed interface Event permits Pack, Dispatch, Deliver, Cancel { }
    record Pack(int bags) implements Event { }
    record Dispatch(String riderId) implements Event { }
    record Deliver(String receivedBy) implements Event { }
    record Cancel(String reason) implements Event { }

    static OrderState apply(OrderState s, Event e, Instant now) {
        return switch (s) {
            case Placed p when e instanceof Pack(int bags) -> new Packed(now, bags);
            case Placed p when e instanceof Cancel(String r) -> new Cancelled(r);
            case Packed p when e instanceof Dispatch(String rider) -> new OutForDelivery(rider);
            case Packed p when e instanceof Cancel(String r) -> new Cancelled(r + " (restock " + p.bags() + " bags)");
            case OutForDelivery o when e instanceof Deliver(String who) -> new Delivered(now, who);
            default -> throw new IllegalStateException(e.getClass().getSimpleName() + " not allowed in " + s.getClass().getSimpleName());
        };
    }

    public static void main(String[] args) {
        Instant t = Instant.parse("2026-11-02T10:00:00Z");
        OrderState s = new Placed(t);
        s = apply(s, new Pack(3), t.plusSeconds(600));
        s = apply(s, new Dispatch("R-42"), t.plusSeconds(900));
        System.out.println(s);
        try { apply(s, new Cancel("customer changed mind"), t); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        s = apply(s, new Deliver("security desk"), t.plusSeconds(2400));
        System.out.println(s);
        if (s instanceof Delivered(Instant at, String who)) System.out.println("delivered to " + who + " at " + at);
    }
}
```

1. The sealed interface lists every state.
2. Each state record holds only its own data: no nullable `riderId` on a placed order.
3. A `switch` with `when` guards and record patterns matches (state, event) pairs.
4. Everything else is an illegal transition with a clear message.

**Run it:** `java SealedStateDemo.java`

```text
OutForDelivery[riderId=R-42]
Cancel not allowed in OutForDelivery
Delivered[at=2026-11-02T10:40:00Z, receivedBy=security desk]
delivered to security desk at 2026-11-02T10:40:00Z
```

| Pros | Cons |
|------|------|
| State-specific data is type-safe | Persistence needs mapping (JSON or columns per state) |
| One function shows all transitions | `default` branch weakens exhaustiveness checks |
| Immutable states: easy to test and log | Requires Java 21 pattern matching |

**Use** sealed states when states carry different data. **Avoid** them when you need the machine to be configurable at runtime; use a table.
**Real-world Java:** modern domain models in Java 21 codebases; the same style as Kotlin sealed classes and Scala ADTs.
**Best practice:** switch on the state and match the event inside each case for full exhaustiveness on states. **Gotcha:** a `default ->` branch means a new state compiles even if you forgot it; keep the default only for "illegal event" handling, and review it when adding states.

### Transition tables

**Description.** The whole state machine as data: a map from (state, event) to next state, optionally with guards and actions. The table can be printed as documentation, validated (unreachable states, dead ends), loaded from configuration, and tested exhaustively.

**Advantages / features.** Reviewable by non-developers; validation at startup; easy to generate diagrams from.

**TransitionTableDemo.java**

```java
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** A transition table: the whole state machine as data. Easy to print, review, validate and test. */
public class TransitionTableDemo {

    enum S { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURNED, REFUNDED }   // REFUNDED: someone forgot its transition
    enum E { PACK, DISPATCH, DELIVER, CANCEL, RETURN }

    static final Map<S, Map<E, S>> TABLE = new EnumMap<>(S.class);
    static void on(S from, E event, S to) { TABLE.computeIfAbsent(from, k -> new EnumMap<>(E.class)).put(event, to); }
    static {
        on(S.PLACED, E.PACK, S.PACKED);
        on(S.PLACED, E.CANCEL, S.CANCELLED);
        on(S.PACKED, E.DISPATCH, S.OUT_FOR_DELIVERY);
        on(S.PACKED, E.CANCEL, S.CANCELLED);
        on(S.OUT_FOR_DELIVERY, E.DELIVER, S.DELIVERED);
        on(S.DELIVERED, E.RETURN, S.RETURNED);
    }

    static S fire(S state, E event) {
        S next = TABLE.getOrDefault(state, Map.of()).get(event);
        if (next == null) throw new IllegalStateException(event + " not allowed in " + state);
        return next;
    }

    static Set<S> reachableFrom(S start) {
        Set<S> seen = EnumSet.of(start); ArrayDeque<S> todo = new ArrayDeque<>(Set.of(start));
        while (!todo.isEmpty()) for (S n : TABLE.getOrDefault(todo.pop(), Map.of()).values()) if (seen.add(n)) todo.push(n);
        return seen;
    }

    public static void main(String[] args) {
        TABLE.forEach((from, row) -> row.forEach((ev, to) -> System.out.println(String.format("%-17s --%-8s--> %s", from, ev, to))));
        System.out.println("fire PLACED+PACK = " + fire(S.PLACED, E.PACK));
        try { fire(S.DELIVERED, E.CANCEL); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        System.out.println("validation, unreachable states: " + EnumSet.complementOf(EnumSet.copyOf(reachableFrom(S.PLACED))));
    }
}
```

1. `EnumMap` of `EnumMap`: a fast, ordered table.
2. Firing an event is a lookup; a missing entry is an illegal transition.
3. Validation finds states nobody can reach, a classic forgotten-transition bug.

**Run it:** `java TransitionTableDemo.java`

```text
PLACED            --PACK    --> PACKED
PLACED            --CANCEL  --> CANCELLED
PACKED            --DISPATCH--> OUT_FOR_DELIVERY
PACKED            --CANCEL  --> CANCELLED
OUT_FOR_DELIVERY  --DELIVER --> DELIVERED
DELIVERED         --RETURN  --> RETURNED
fire PLACED+PACK = PACKED
CANCEL not allowed in DELIVERED
validation, unreachable states: [REFUNDED]
```

| Approach | Pros | Cons |
|----------|------|------|
| Table in code | Reviewable, validatable, printable | Actions and guards need a structure |
| Table in configuration | Changeable without deploys | Untyped; risky without validation |
| State machine library (Spring Statemachine) | Hierarchical states, persistence, listeners | Heavy; learning curve |

**Use** tables when there are many states and events, or business people review the flow. **Avoid** configuration-driven machines unless the flow really changes without code changes.
**Real-world Java:** Spring Statemachine, workflow engines (Camunda, Temporal workflows), parsers' lexer tables.
**Best practice:** generate the diagram from the table in CI so documentation can't drift. **Gotcha:** a table with actions that call external services makes "fire" slow and failure-prone; record the transition and run side effects asynchronously (outbox).

### State vs. Strategy

**Description.** Both patterns hold an interface and delegate to it. In **Strategy**, the client chooses the implementation and it usually stays the same for the object's life. In **State**, the object (or the state itself) replaces the implementation as events happen, and states know about each other's existence.

**Advantages / features (of knowing the difference).** Clear names and designs: "fee strategy" vs "order state".

**StateVsStrategyDemo.java**

```java
/** Same structure, different intent: a Strategy is chosen from outside and stays; a State replaces itself as things happen. */
public class StateVsStrategyDemo {

    // ===================== Strategy: the caller picks how delivery fees are computed =====================
    interface FeeStrategy { long fee(long orderPaise); }
    record Checkout(FeeStrategy fees) { long total(long p) { return p + fees.fee(p); } }

    // ===================== State: the rider's own state decides what "next job" does, and changes itself =====================
    interface RiderState { RiderState nextJob(Rider r); String name(); }
    record Available() implements RiderState {
        public RiderState nextJob(Rider r) { r.jobs++; return r.jobs >= 2 ? new OnBreak() : this; }
        public String name() { return "AVAILABLE"; }
    }
    record OnBreak() implements RiderState {
        public RiderState nextJob(Rider r) { System.out.println("  on break: job goes to another rider"); r.jobs = 0; return new Available(); }
        public String name() { return "ON_BREAK"; }
    }
    static final class Rider { RiderState state = new Available(); int jobs; void offerJob() { state = state.nextJob(this); } }

    public static void main(String[] args) {
        System.out.println("strategy: standard " + new Checkout(p -> 2_500).total(30_000) + ", member " + new Checkout(p -> 0).total(30_000));
        Rider r = new Rider();
        for (int i = 1; i <= 3; i++) { r.offerJob(); System.out.println("state after offer " + i + ": " + r.state.name()); }
    }
}
```

1. Strategy: chosen from outside at construction; `Checkout` never changes it.
2. State: the current state decides the next one (two jobs in a row means a break).
3. The context swaps its own state after every event.

**Run it:** `java StateVsStrategyDemo.java`

```text
strategy: standard 32500, member 30000
state after offer 1: AVAILABLE
state after offer 2: ON_BREAK
  on break: job goes to another rider
state after offer 3: AVAILABLE
```

| | Strategy | State |
|---|----------|-------|
| Who chooses | The client | The context or states themselves |
| Changes during life | Rarely | Constantly, driven by events |
| Implementations know each other | No | Yes (they return the next state) |
| Typical question | "How should this be done?" | "What does this mean right now?" |

**Use** the name that reflects who controls the switch. **Avoid** State when nothing ever changes it after construction; that's Strategy.
**Real-world Java:** Strategy: `Comparator`, `RetryPolicy`; State: connection lifecycles, order workflows.
**Best practice:** if a "strategy" field is reassigned inside the class based on events, rename it as state. **Gotcha:** states holding references to each other as fields create cycles that complicate serialisation; return new states instead.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** a rider's app has one big button whose meaning depends on the shift state.

**RiderShiftDemo.java**

```java
/** Minimal State: a rider's app behaves differently when off duty, available, or on a delivery. */
public class RiderShiftDemo {

    enum Shift {
        OFF_DUTY { Shift tap(StringBuilder log) { log.append("started shift; "); return AVAILABLE; } },
        AVAILABLE { Shift tap(StringBuilder log) { log.append("accepted order; "); return ON_DELIVERY; } },
        ON_DELIVERY { Shift tap(StringBuilder log) { log.append("marked delivered; "); return AVAILABLE; } };

        abstract Shift tap(StringBuilder log);
    }

    public static void main(String[] args) {
        Shift s = Shift.OFF_DUTY;
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 4; i++) s = s.tap(log);
        System.out.println(log + "now " + s);
    }
}
```

1. Each enum constant overrides `tap` with its own behaviour and next state.
2. The abstract method forces every state to define what a tap means.

**Run it:** `java RiderShiftDemo.java`

**Output:**

```text
started shift; accepted order; marked delivered; accepted order; now ON_DELIVERY
```

**What to notice:**

- One button, three meanings, no `if` in the caller.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** customers can tap "Cancel order" at any time. The effects depend on the state: refund only, restock and refund, refuse (rider on the way), error (delivered), or nothing (already cancelled, a double tap).

**OrderLifecycleDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Realistic State: order states with side effects. What "cancel" does depends on the state: free, restock, or refused. */
public class OrderLifecycleDemo {

    interface Effects { void notifyCustomer(String text); void refund(long paise); void restock(int bags); }

    sealed interface State permits Placed, Packed, OutForDelivery, Delivered, Cancelled { }
    record Placed(long paidPaise) implements State { }
    record Packed(long paidPaise, int bags) implements State { }
    record OutForDelivery(long paidPaise, String rider) implements State { }
    record Delivered() implements State { }
    record Cancelled(String reason) implements State { }

    static State cancel(State s, String reason, Effects fx) {
        return switch (s) {
            case Placed p -> { fx.refund(p.paidPaise()); fx.notifyCustomer("Order cancelled, full refund"); yield new Cancelled(reason); }
            case Packed p -> { fx.restock(p.bags()); fx.refund(p.paidPaise()); fx.notifyCustomer("Order cancelled, full refund"); yield new Cancelled(reason); }
            case OutForDelivery o -> { fx.notifyCustomer("Rider " + o.rider() + " is on the way; cancel at the door instead"); yield o; }
            case Delivered d -> throw new IllegalStateException("delivered orders are returned, not cancelled");
            case Cancelled c -> c;
        };
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        Effects fx = new Effects() {
            public void notifyCustomer(String t) { log.add("sms: " + t); }
            public void refund(long p) { log.add("refund Rs " + p / 100); }
            public void restock(int b) { log.add("restock " + b + " bags"); }
        };
        for (State s : List.<State>of(new Placed(64_900), new Packed(64_900, 2), new OutForDelivery(64_900, "R-42"), new Cancelled("dup"))) {
            log.clear();
            State after = cancel(s, "customer request", fx);
            System.out.println(s.getClass().getSimpleName() + " -> " + after.getClass().getSimpleName() + " " + log);
        }
        try { cancel(new Delivered(), "x", fx); } catch (IllegalStateException e) { System.out.println("Delivered: " + e.getMessage()); }
    }
}
```

1. One exhaustive switch over sealed states decides the effects of "cancel".
2. Some actions are valid but change nothing: the state stays the same and the customer is told why.
3. Cancelling an already cancelled order is a harmless no-op (double taps happen).

**Run it:** `java OrderLifecycleDemo.java`

**Output:**

```text
Placed -> Cancelled [refund Rs 649, sms: Order cancelled, full refund]
Packed -> Cancelled [restock 2 bags, refund Rs 649, sms: Order cancelled, full refund]
OutForDelivery -> OutForDelivery [sms: Rider R-42 is on the way; cancel at the door instead]
Cancelled -> Cancelled []
Delivered: delivered orders are returned, not cancelled
```

**What to notice:**

- Only the packed order triggered a restock; only the delivered order raised an error.
- The side effects are passed in, so tests can record them.

### Example 3: Production-grade

**Scenario:** order state changes come from many places (store app, rider app, payment webhooks), sometimes twice, sometimes at the same moment. Requirements: transitions declared once and validated at startup; guards for business rules; optimistic versioning so two workers can't both change the same version; idempotent events (a redelivered event is ignored); and an audit history.

**OrderStateMachineDemo.java**

```java
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Production-grade state machine for orders:
 * - transitions declared as data (from, event, to, guard), validated at startup
 * - guards check business rules (e.g. can't dispatch without a rider)
 * - optimistic versioning: an update based on a stale version is rejected (two workers racing)
 * - idempotent events: re-delivering the same event id is a no-op, not an error
 * - an append-only history for audit and debugging
 */
public class OrderStateMachineDemo {

    enum S { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED }
    enum E { PACK, DISPATCH, DELIVER, CANCEL }

    record Order(String id, S state, long version, String riderId) { }
    record Event(String eventId, E type, String riderId) { }
    record Transition(S from, E event, S to, Predicate<Event> guard, String guardMessage) { }
    record HistoryEntry(String orderId, S from, S to, String eventId, Instant at) { }

    static final class StateMachine {
        private final Map<S, Map<E, Transition>> table = new EnumMap<>(S.class);
        StateMachine(List<Transition> transitions) {
            for (Transition t : transitions) {
                Transition old = table.computeIfAbsent(t.from(), k -> new EnumMap<>(E.class)).put(t.event(), t);
                if (old != null) throw new IllegalArgumentException("duplicate transition " + t.from() + "+" + t.event());
            }
        }
        Transition find(S from, E event) {
            Transition t = table.getOrDefault(from, Map.of()).get(event);
            if (t == null) throw new IllegalStateException(event + " not allowed in " + from);
            return t;
        }
    }

    static final class OrderStore {                                                            // simulates a table with a version column
        final Map<String, Order> rows = new java.util.HashMap<>();
        final Set<String> processedEvents = new HashSet<>();
        final List<HistoryEntry> history = new ArrayList<>();

        synchronized void save(Order updated, long expectedVersion, HistoryEntry h) {
            Order current = rows.get(updated.id());
            if (current.version() != expectedVersion)
                throw new IllegalStateException("stale update for " + updated.id() + ": expected v" + expectedVersion + ", found v" + current.version());
            rows.put(updated.id(), updated);
            history.add(h);
        }
    }

    static final class OrderService {
        private final StateMachine machine; private final OrderStore store; private final java.util.function.Supplier<Instant> clock;
        OrderService(StateMachine m, OrderStore s, java.util.function.Supplier<Instant> clock) { machine = m; store = s; this.clock = clock; }

        Order handle(String orderId, Event event, Order readEarlier) {
            if (store.processedEvents.contains(event.eventId())) return store.rows.get(orderId);
            Transition t = machine.find(readEarlier.state(), event.type());
            if (!t.guard().test(event)) throw new IllegalStateException(t.guardMessage());
            Order next = new Order(orderId, t.to(), readEarlier.version() + 1,
                    event.riderId() != null ? event.riderId() : readEarlier.riderId());
            store.save(next, readEarlier.version(), new HistoryEntry(orderId, t.from(), t.to(), event.eventId(), clock.get()));
            store.processedEvents.add(event.eventId());
            return next;
        }
    }

    static StateMachine orderMachine() {
        Predicate<Event> always = e -> true;
        return new StateMachine(List.of(
                new Transition(S.PLACED, E.PACK, S.PACKED, always, ""),
                new Transition(S.PLACED, E.CANCEL, S.CANCELLED, always, ""),
                new Transition(S.PACKED, E.DISPATCH, S.OUT_FOR_DELIVERY, e -> e.riderId() != null, "dispatch needs a rider"),
                new Transition(S.PACKED, E.CANCEL, S.CANCELLED, always, ""),
                new Transition(S.OUT_FOR_DELIVERY, E.DELIVER, S.DELIVERED, always, "")));
    }

    public static void main(String[] args) {
        OrderStore store = new OrderStore();
        store.rows.put("FC-9101", new Order("FC-9101", S.PLACED, 1, null));
        Instant[] now = {Instant.parse("2026-11-02T10:00:00Z")};
        OrderService svc = new OrderService(orderMachine(), store, () -> now[0] = now[0].plusSeconds(60));

        Order o = svc.handle("FC-9101", new Event("ev-1", E.PACK, null), store.rows.get("FC-9101"));
        System.out.println("after PACK: " + o);
        try { svc.handle("FC-9101", new Event("ev-2", E.DISPATCH, null), o); } catch (IllegalStateException e) { System.out.println("guard: " + e.getMessage()); }

        Order seenByWorkerA = store.rows.get("FC-9101"), seenByWorkerB = store.rows.get("FC-9101");
        svc.handle("FC-9101", new Event("ev-3", E.DISPATCH, "R-42"), seenByWorkerA);
        try { svc.handle("FC-9101", new Event("ev-4", E.CANCEL, null), seenByWorkerB); } catch (IllegalStateException e) { System.out.println("race: " + e.getMessage()); }

        Order again = svc.handle("FC-9101", new Event("ev-3", E.DISPATCH, "R-42"), seenByWorkerA);
        System.out.println("duplicate ev-3 ignored, still " + again.state() + " v" + again.version());
        try { svc.handle("FC-9101", new Event("ev-5", E.PACK, null), again); } catch (IllegalStateException e) { System.out.println("illegal: " + e.getMessage()); }
        svc.handle("FC-9101", new Event("ev-6", E.DELIVER, null), store.rows.get("FC-9101"));
        store.history.forEach(h -> System.out.println("  history: " + h.from() + " -> " + h.to() + " by " + h.eventId() + " at " + h.at()));
    }
}
```

1. The order is an immutable snapshot with a version, as read from a table.
2. Duplicate transition definitions fail at startup, not in production.
3. Optimistic locking: like `UPDATE ... WHERE id = ? AND version = ?` affecting zero rows.
4. Idempotency: an event ID seen before returns the current order without changing anything.
5. A guard enforces a rule that the table alone can't express.

**Run it:** `java OrderStateMachineDemo.java`

**Output:**

```text
after PACK: Order[id=FC-9101, state=PACKED, version=2, riderId=null]
guard: dispatch needs a rider
race: stale update for FC-9101: expected v2, found v3
duplicate ev-3 ignored, still OUT_FOR_DELIVERY v3
illegal: PACK not allowed in OUT_FOR_DELIVERY
  history: PLACED -> PACKED by ev-1 at 2026-11-02T10:01:00Z
  history: PACKED -> OUT_FOR_DELIVERY by ev-3 at 2026-11-02T10:02:00Z
  history: OUT_FOR_DELIVERY -> DELIVERED by ev-6 at 2026-11-02T10:04:00Z
```

**What to notice:**

- Worker B's cancel was based on version 2 after worker A had moved the order to version 3, so it was rejected instead of cancelling an order already with a rider.
- The redelivered `ev-3` didn't create a second history entry.
- The history shows exactly which event caused each transition.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Clarity | Behaviour grouped by state; flow can be drawn | Flow spread across classes (classic form) |
| Correctness | Illegal transitions explicit | Guards and actions add complexity |
| Extensibility | New states without editing all methods | Every new state needs every event considered |
| Concurrency | Versioning and idempotency fit naturally | Needs persistence design |
| Testability | Each transition testable | Many combinations to cover |
| Simplicity | Enum form is tiny | Classic form can be overkill |

## 8. When to Use / When NOT to Use

**Use it when:**

- an object's behaviour depends on a status that changes over time;
- there are rules about which changes are allowed;
- many methods would otherwise switch on the same status.

**Don't use it when:**

- there are only two states with trivial differences (a boolean is fine);
- behaviour doesn't depend on the state (it's just a label);
- the variation is chosen once and never changes (that's Strategy).

## 9. Best Practices

1. **Make illegal transitions impossible or loud**, never silent. *(Consensus.)*
2. **Pick the lightest form**: enum → sealed records → classes → table/library as complexity grows. *(Opinion, widely shared.)*
3. **Persist states by name**, with a version column for optimistic locking. *(Consensus.)*
4. **Make event handling idempotent** with event IDs. *(Consensus for distributed systems.)*
5. **Keep side effects out of the transition** or run them via an outbox. *(Consensus.)*
6. **Record a history** of transitions with the triggering event. *(Consensus.)*
7. **Generate diagrams from the definition** so documentation matches code. *(Opinion.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Lost update between two apps (production failure mode)**  
> **Symptom:** an order showed "cancelled" while the customer received it, and the customer was refunded for delivered groceries.
> **Root cause:** the store app cancelled based on a "packed" order it had read earlier; meanwhile the rider app had dispatched it. Both wrote without checking, and the last write won.
> **Fix:** optimistic versioning (`WHERE version = ?`); reload and re-check rules on conflict; refunds triggered from the committed transition, not from the request.

> 🚨 **Boolean soup**  
> **Symptom:** an order with `isPacked=true, isCancelled=true, isDelivered=true`.
> **Root cause:** state represented as independent flags that can combine in impossible ways.
> **Fix:** one state field (enum or sealed type) with explicit transitions.

> 🚨 **Status set directly**  
> **Symptom:** a support tool set orders straight to `DELIVERED`, skipping payment capture.
> **Root cause:** a public `setStatus` bypassed the state machine.
> **Fix:** no setters; all changes go through events handled by the machine.

> 🚨 **Ordinal persistence**  
> **Symptom:** after adding `RETURNED` in the middle of the enum, thousands of orders appeared returned.
> **Root cause:** `@Enumerated(EnumType.ORDINAL)` stored positions, which shifted.
> **Fix:** `EnumType.STRING`, and a migration.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `@Enumerated` | `ORDINAL` (JPA default) | How enum states are stored | Always use `STRING` | Changing later needs a data migration |
| `@Version` column | none | Optimistic locking | Any concurrently updated state | Conflicts must be handled (retry or report) |
| Processed-event retention | your choice | Idempotency window | Longer for slow redelivery | Storage growth |
| State timeouts | none | Auto-transition after time (e.g. unpaid → cancelled) | Payment holds, reservations | Scheduler reliability matters |

## 12. Security Considerations

- Check permissions per transition: a customer may cancel, only support may refund after delivery, only riders may mark delivered.
- Never let clients send the target state; send events ("cancel") and let the server decide the result.
- The transition history is an audit log: record the actor, keep it append-only.

## 13. Performance & Scalability

State machines are cheap: a table lookup or a `switch` per event. The costs are in persistence and side effects. Use optimistic locking (no long-held locks), keep transitions in short transactions, and move slow side effects (SMS, refunds) to an outbox so state changes stay fast under load. For very high event rates, partition by order ID so each order's events are processed in order by one consumer.

## 14. Testing It

Test the happy path, illegal transitions, guards, idempotency, stale versions and startup validation:

**OrderStateMachineTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    private OrderStateMachineDemo.OrderStore store;
    private OrderStateMachineDemo.OrderService svc;

    @BeforeEach
    void setUp() {
        store = new OrderStateMachineDemo.OrderStore();
        store.rows.put("O1", new OrderStateMachineDemo.Order("O1", OrderStateMachineDemo.S.PLACED, 1, null));
        svc = new OrderStateMachineDemo.OrderService(OrderStateMachineDemo.orderMachine(), store, () -> Instant.EPOCH);
    }

    private OrderStateMachineDemo.Order fire(String id, OrderStateMachineDemo.E type, String rider) {
        return svc.handle("O1", new OrderStateMachineDemo.Event(id, type, rider), store.rows.get("O1"));
    }

    @Test
    void happyPathRecordsHistory() {
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        fire("e2", OrderStateMachineDemo.E.DISPATCH, "R1");
        fire("e3", OrderStateMachineDemo.E.DELIVER, null);
        assertEquals(OrderStateMachineDemo.S.DELIVERED, store.rows.get("O1").state());
        assertEquals(3, store.history.size());
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThrows(IllegalStateException.class, () -> fire("e1", OrderStateMachineDemo.E.DELIVER, null));
    }

    @Test
    void guardBlocksDispatchWithoutRider() {
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        assertThrows(IllegalStateException.class, () -> fire("e2", OrderStateMachineDemo.E.DISPATCH, null));
    }

    @Test
    void duplicateEventIsANoOp() {
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        assertEquals(2, store.rows.get("O1").version());
    }

    @Test
    void staleVersionIsRejected() {
        var stale = store.rows.get("O1");
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        assertThrows(IllegalStateException.class,
                () -> svc.handle("O1", new OrderStateMachineDemo.Event("e2", OrderStateMachineDemo.E.CANCEL, null), stale));
    }

    @Test
    void duplicateTransitionDefinitionFailsAtStartup() {
        var t = new OrderStateMachineDemo.Transition(OrderStateMachineDemo.S.PLACED, OrderStateMachineDemo.E.PACK, OrderStateMachineDemo.S.PACKED, e -> true, "");
        assertThrows(IllegalArgumentException.class, () -> new OrderStateMachineDemo.StateMachine(List.of(t, t)));
    }
}
```

For tables, a parameterised test over every (state, event) pair checks that each is either a defined transition or rejected, so nothing is accidentally allowed.

## 15. Observability & Operations

Emit a metric per transition (`order_transition_total{from,to}`) and per rejected event; a spike in rejections often means two systems disagree about the flow. Track time spent in each state (orders stuck in `PACKED` for over 30 minutes need attention). Keep the history queryable for support: "why was this order cancelled?" should be one lookup.

## 16. Version Notes

| Java | Change relevant to State |
|------|--------------------------|
| 5 | Enums with constant-specific methods; `EnumSet`, `EnumMap` |
| 8 | Default methods: states reject unknown actions by default |
| 14 | Switch expressions (final) |
| 17 | Sealed classes and interfaces (final) |
| 21 | Pattern matching for `switch` with guards (`when`) and record patterns (final) |

## 17. Expert Deep Dive

- **Make illegal states unrepresentable.** Sealed records per state remove whole classes of bugs (a delivered order without a delivery time can't exist). This is the biggest modern upgrade to the GoF pattern.
- **Statecharts.** Real workflows need hierarchical states (an order "in fulfilment" with sub-states), parallel regions, history states, and timeouts. Harel statecharts (and SCXML, Spring Statemachine) model these; use them when flat machines explode.
- **Event sourcing.** Instead of storing the current state, store events and fold them through the transition function. The State pattern's `apply(state, event)` becomes the core of the aggregate. See [Distributed Data Patterns](../l4-senior/distributed-data-patterns.md).
- **Workflows across services.** When transitions span services (payment, warehouse, delivery), the state machine becomes a saga orchestrator or a durable workflow engine (Temporal), with compensations instead of undo.

> 📖 **War story: the order that was delivered twice**  
> A grocery company's rider app retried "mark delivered" when the network dropped, and the payment service captured payment on each `DELIVERED` event. A handful of customers were charged twice per day. The state machine did reject `DELIVERED → DELIVERED`, but the rejection happened **after** the capture side effect, which ran in the request handler. **Fixes:** event IDs from the rider app made retries idempotent; payment capture moved to an outbox written in the same transaction as the transition, so it happens exactly once per real transition; and a reconciliation job compares captures with deliveries daily. **Lesson:** side effects belong to committed transitions, not to requests.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What does the State pattern do?** Lets an object **change behaviour when its state changes** by delegating to state objects.
2. **Name the parts.** **Context, state interface, concrete states.**
3. **How would you model order statuses simply?** An **enum** with allowed transitions.
4. **What's an illegal transition?** An event **not allowed** in the current state (deliver a cancelled order).
5. **Why not booleans for status?** Flags combine into **impossible states**.

**▸ 🟡 L3**

1. **State vs Strategy?** Strategy is **chosen by the client** and stable; State **changes itself** with events.
2. **How do sealed types improve State?** Each state carries **only its own data**; switches are **exhaustive**.
3. **How do you persist an enum state safely?** **`EnumType.STRING`** plus a **version column**.
4. **What's a guard?** A **condition** that must hold for a transition (rider assigned before dispatch).
5. **Why make event handling idempotent?** Events are **redelivered**; duplicates must not repeat effects.

**▸ 🔴 L4–L5**

1. **Two services update the same order concurrently. Design it.** **Optimistic versioning, events not states, idempotency, outbox side effects, single-writer per order** (partitioning).
2. **When would you use a workflow engine?** **Long-running, cross-service** flows with timeouts, retries and compensations.
3. **How does event sourcing relate to State?** State is a **fold of events** through the transition function.
4. **How do you evolve a state machine in production?** Add states **backward-compatibly**, migrate data, handle **in-flight** instances, version the machine.
5. **How do you test a state machine exhaustively?** **Every (state, event) pair**, property-based tests over random event sequences, invariants.

**▸ 💻 Hands-on**

**Task:** Add a `RETURNED` state reachable from `DELIVERED` within 2 days, to `OrderStateMachineDemo`.

> ✅ **Solution**  
> ```java
> enum S { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURNED }
> enum E { PACK, DISPATCH, DELIVER, CANCEL, RETURN }
>
> new Transition(S.DELIVERED, E.RETURN, S.RETURNED,
>         e -> Duration.between(deliveredAt(e), clock.get()).toDays() < 2,
>         "returns are accepted within 2 days of delivery")
> ```

## 19. Practice

- [ ] Add a `PAYMENT_PENDING` state before `PLACED` in `TransitionTableDemo`, with a timeout event that cancels unpaid orders.
    > 💡 **Hint**  
    > Add `E.PAYMENT_TIMEOUT` from `PAYMENT_PENDING` to `CANCELLED`.

- [ ] Generate a Mermaid `stateDiagram-v2` from `TransitionTableDemo.TABLE`.
    > 💡 **Hint**  
    > Print `FROM --> TO : EVENT` for each entry.

- [ ] Write a parameterised test that every (state, event) pair in `OrderStateMachineDemo` is either allowed or throws.
    > 💡 **Hint**  
    > Nested loops over `S.values()` and `E.values()`.

- [ ] Find a class in a codebase with several boolean status flags and redesign it as a state machine.
    > 💡 **Hint**  
    > List which flag combinations are actually valid; each becomes a state.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: behaviour depends on current state; transitions are explicit.
> - Forms: state classes (GoF), enum with allowed transitions, sealed records + switch, transition table.
> - Illegal transitions rejected loudly; no status setters; events, not target states.
> - Production: guards, optimistic versions, idempotent events, history, side effects via outbox.
> - State changes itself; Strategy is chosen from outside.
>
> ```java
> static State apply(State s, Event e) {
>     return switch (s) {
>         case Placed p when e instanceof Pack(int bags) -> new Packed(bags);
>         case Packed p when e instanceof Dispatch(String rider) -> new OutForDelivery(rider);
>         default -> throw new IllegalStateException(e + " not allowed in " + s);
>     };
> }
> ```

## 21. Further Reading

- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994), "State".
- David Harel, "Statecharts: A Visual Formalism for Complex Systems" (1987).
- [Spring Statemachine reference](https://docs.spring.io/spring-statemachine/docs/current/reference/)
- [JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441)
- Scott Wlaschin, *Domain Modeling Made Functional* (2018), "Making illegal states unrepresentable".

---

[← Previous: Iterator](iterator.md) · [Next: Chain of Responsibility →](chain-of-responsibility.md)
