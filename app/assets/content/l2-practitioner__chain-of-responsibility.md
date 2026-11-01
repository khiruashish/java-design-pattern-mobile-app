# Chain of Responsibility

L2 🔵 Practitioner · ⏱ 35 min read · Prerequisites: [Decorator](../l1-foundations/decorator.md), [Command](command.md)

> 📌 **TL;DR**  
> Chain of Responsibility passes a request along a sequence of handlers; each one either handles it, passes it on, or (in the filter style) does something before and after the rest of the chain. It decouples the sender from whoever ends up handling the request. You use it every day in Java: servlet filters, Spring `HandlerInterceptor`s and Spring Security's filter chain. Order matters, and every chain needs a defined ending for requests nobody handles.

## 1. What It Is

**Plain English.** A FreshCart refund request goes to a chatbot first. Small refunds the bot approves itself; bigger ones go to an agent, then a supervisor. The customer doesn't know or care who approves; the request moves along the line until someone can deal with it.

**Definition (GoF).** "Avoid coupling the sender of a request to its receiver by giving more than one object a chance to handle the request. Chain the receiving objects and pass the request along the chain until an object handles it." Parts:

- **Handler:** an interface with a method to handle the request (and, in the linked form, a reference to the next handler).
- **Concrete handlers:** decide whether to handle, pass on, or both.
- **Client:** sends the request to the first handler.

Two variants are common in Java:

- **"First one wins"** (GoF): exactly one handler handles the request (approvals, fallbacks, routing).
- **Pipeline / filter chain:** every handler may act and then call the next (logging, auth, compression), possibly stopping early.

## 2. Why It Exists

Without a chain, the sender needs one big method with every rule (`if amount <= 200 then bot else if ...`), and changing the order, adding a level, or reusing a step means editing that method. With a chain, each rule is a small handler, the order is configuration, and new handlers plug in without touching the others or the sender. Cross-cutting request concerns (authentication, logging, rate limits) can be stacked around business endpoints without the endpoints knowing.

## 3. Mental Model / Analogy

**Analogy.** A call centre's escalation path: first-line support, then a specialist, then a manager. Each level resolves what it can and escalates the rest. Or airport security: each checkpoint (ID, bag scan, body scan) can stop you or wave you on to the next.

**Technical model.** A linked list (or `List`) of handlers. In "first wins" chains, iterate until one returns a result. In filter chains, each handler receives the request and a `next` callback; calling `next` continues the chain, not calling it short-circuits, and code after `next` runs on the way back.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Linked handlers

**Description.** The GoF form: each handler holds a reference to the next one. `handle` either processes the request or forwards it. The chain is assembled by linking handlers; the client only knows the first.

**Advantages / features.** Each rule is isolated; levels can be added, removed or reordered by relinking; the sender doesn't know who handles the request.

**Before (one method with every level) and after (linked handlers):**

**LinkedHandlersDemo.java**

```java
/** Chain of Responsibility: a request travels along linked handlers until one handles it. */
public class LinkedHandlersDemo {

    record RefundRequest(String orderId, long paise) { }

    // ===================== Before: one method knows every level and every limit =====================
    static String approveBefore(RefundRequest r) {
        if (r.paise() <= 20_000) return "bot";
        else if (r.paise() <= 100_000) return "agent";
        else if (r.paise() <= 500_000) return "supervisor";
        else return "finance";
    }

    // ===================== After: each handler decides, or passes on =====================
    abstract static class Approver {
        private Approver next;
        Approver then(Approver next) { this.next = next; return next; }
        final String handle(RefundRequest r) {
            if (canApprove(r)) return name() + " approved Rs " + r.paise() / 100 + " for " + r.orderId();
            if (next == null) return "nobody could approve " + r.orderId();
            return next.handle(r);
        }
        abstract boolean canApprove(RefundRequest r);
        abstract String name();
    }
    static final class Bot extends Approver { boolean canApprove(RefundRequest r) { return r.paise() <= 20_000; } String name() { return "bot"; } }
    static final class Agent extends Approver { boolean canApprove(RefundRequest r) { return r.paise() <= 100_000; } String name() { return "agent"; } }
    static final class Supervisor extends Approver { boolean canApprove(RefundRequest r) { return r.paise() <= 500_000; } String name() { return "supervisor"; } }

    public static void main(String[] args) {
        System.out.println("before: " + approveBefore(new RefundRequest("FC-1", 50_000)));
        Approver chain = new Bot();
        chain.then(new Agent()).then(new Supervisor());
        for (long p : new long[] {15_000, 64_900, 300_000, 900_000}) System.out.println(chain.handle(new RefundRequest("FC-" + p / 100, p)));
    }
}
```

1. Before: every limit and level is hard-coded in one method.
2. `then` links the next handler and returns it, so chains read left to right.
3. The end of the chain is explicit: an unhandled request gets a clear answer, not a `NullPointerException`.
4. The chain is built once; here there's no finance level, and the largest refund falls off the end.

**Run it:** `java LinkedHandlersDemo.java`

```text
before: agent
bot approved Rs 150 for FC-150
agent approved Rs 649 for FC-649
supervisor approved Rs 3000 for FC-3000
nobody could approve FC-9000
```

| Pros | Cons |
|------|------|
| Each handler small and independent | Hard to see the whole chain in one place |
| Order changed by relinking | A broken link silently drops requests |
| Sender decoupled from handlers | Long chains are slow to debug |

**Use it** for escalation, approvals and fallbacks where exactly one handler should act. **Avoid** hand-linked chains when a `List` would be clearer (most cases in modern Java).
**Real-world Java:** `java.util.logging.Logger` passes records to parent loggers; class loaders delegate to their parent before loading themselves.
**Best practice:** build the chain in one place (a factory or configuration) and log which handler handled each request. **Gotcha:** a cycle in the links (A → B → A) loops forever; build chains from a list to avoid it.

### List-based pipelines

**Description.** In modern Java a chain is often just a `List` of functional handlers iterated by a small loop. You choose the semantics in the loop: stop at the first result, or run all handlers and collect results (e.g. every validation error).

**Advantages / features.** The whole chain is visible in one place; easy to reorder, test and configure; no linking code.

**ListPipelineDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A modern chain: a List of handlers. Choose "stop at first problem" or "run all and collect". */
public class ListPipelineDemo {

    record Order(String pincode, long paise, int items, boolean hasAlcohol, int customerAge) { }

    interface Check { Optional<String> problem(Order o); }

    static final List<Check> CHECKS = List.of(
            o -> o.items() == 0 ? Optional.of("cart is empty") : Optional.empty(),
            o -> o.paise() < 9_900 ? Optional.of("minimum order is Rs 99") : Optional.empty(),
            o -> o.pincode().startsWith("560") ? Optional.empty() : Optional.of("we don't deliver to " + o.pincode()),
            o -> o.hasAlcohol() && o.customerAge() < 21 ? Optional.of("age check failed") : Optional.empty());

    static Optional<String> firstProblem(Order o) {
        for (Check c : CHECKS) { Optional<String> p = c.problem(o); if (p.isPresent()) return p; }
        return Optional.empty();
    }

    static List<String> allProblems(Order o) {
        List<String> problems = new ArrayList<>();
        CHECKS.forEach(c -> c.problem(o).ifPresent(problems::add));
        return problems;
    }

    public static void main(String[] args) {
        Order bad = new Order("110001", 5_000, 2, true, 19);
        System.out.println("stop at first: " + firstProblem(bad).orElse("ok"));
        System.out.println("collect all:   " + allProblems(bad));
        System.out.println("good order:    " + allProblems(new Order("560034", 45_000, 6, false, 30)));
    }
}
```

1. Each check is a lambda returning an optional problem.
2. "Stop at first": cheapest; good for fast failure.
3. "Collect all": better for users, who can fix everything at once.

**Run it:** `java ListPipelineDemo.java`

```text
stop at first: minimum order is Rs 99
collect all:   [minimum order is Rs 99, we don't deliver to 110001, age check failed]
good order:    []
```

| Semantics | Use for | Example |
|-----------|---------|---------|
| First result wins | Routing, fallbacks, coupons | Price lookup: cache, DB, supplier |
| Stop at first failure | Security checks, cheap fail-fast | Auth then rate limit then validation |
| Run all, collect | User-facing validation | All form errors at once |

**Use** list pipelines by default. **Avoid** hiding the list inside many classes; keep its definition in one readable place.
**Real-world Java:** Bean Validation runs all constraints and collects violations; Spring's `HandlerMethodArgumentResolver` list picks the first resolver that supports a parameter.
**Best practice:** order cheap and most-likely-to-fail checks first. **Gotcha:** if checks have side effects (reserving stock), "collect all" may run effects you didn't want; keep checks pure.

### Servlet filters and Spring interceptors

**Description.** Web frameworks run requests through filter chains. A servlet `Filter` receives the request and a `FilterChain`; calling `chain.doFilter` continues, not calling it stops the request, and code after the call runs on the way out. Spring adds `HandlerInterceptor` (`preHandle`, `postHandle`, `afterCompletion`) and Spring Security builds its own ordered `SecurityFilterChain`.

**Advantages / features.** Cross-cutting concerns (auth, logging, CORS, compression, request IDs) wrap every endpoint without endpoint code; filters can be reused across applications.

**FiltersInterceptorsDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Servlet filters and Spring interceptors are chains: each filter can act before, after, or stop the request. */
public class FiltersInterceptorsDemo {

    record Request(String path, Map<String, String> headers) { }
    record Response(int status, String body) { }

    interface Filter { Response doFilter(Request req, Chain chain); }
    interface Chain { Response proceed(Request req); }

    static Chain build(List<Filter> filters, Chain endpoint) {
        Chain chain = endpoint;
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter f = filters.get(i); Chain next = chain;
            chain = req -> f.doFilter(req, next);
        }
        return chain;
    }

    static final List<String> LOG = new ArrayList<>();

    static final Filter LOGGING = (req, chain) -> {
        LOG.add("-> " + req.path());
        Response r = chain.proceed(req);
        LOG.add("<- " + r.status());
        return r;
    };
    static final Filter AUTH = (req, chain) -> req.headers().containsKey("Authorization")
            ? chain.proceed(req)
            : new Response(401, "login required");

    public static void main(String[] args) {
        Chain app = build(List.of(LOGGING, AUTH), req -> new Response(200, "orders for " + req.headers().get("Authorization")));
        System.out.println(app.proceed(new Request("/orders", Map.of("Authorization", "asha"))));
        System.out.println(app.proceed(new Request("/orders", Map.of())));
        System.out.println("log: " + LOG);

        LOG.clear();
        Chain wrongOrder = build(List.of(AUTH, LOGGING), req -> new Response(200, "ok"));
        wrongOrder.proceed(new Request("/orders", Map.of()));
        System.out.println("auth before logging: rejected request not logged at all -> log " + LOG);
    }
}
```

1. The servlet shape: a filter gets the request and the rest of the chain.
2. The chain is built from the inside out, so the first filter in the list runs first.
3. Code before `proceed` runs on the way in; code after runs on the way out.
4. Not calling `proceed` short-circuits: the endpoint never runs.

**Run it:** `java FiltersInterceptorsDemo.java`

```text
Response[status=200, body=orders for asha]
Response[status=401, body=login required]
log: [-> /orders, <- 200, -> /orders, <- 401]
auth before logging: rejected request not logged at all -> log []
```

| Mechanism | Level | Typical use |
|-----------|-------|-------------|
| Servlet `Filter` (`@Order`, `FilterRegistrationBean`) | Servlet container | Request IDs, logging, CORS, compression |
| Spring Security `SecurityFilterChain` | Before the DispatcherServlet | Authentication, authorisation, CSRF |
| Spring `HandlerInterceptor` | Around controller methods | Per-handler checks, timing, locale |
| WebFlux `WebFilter` | Reactive pipeline | The same, non-blocking |

**Use** filters for concerns that apply to many endpoints. **Avoid** business logic in filters; it becomes invisible to whoever reads the controller.
**Real-world Java:** Spring Security's ~15 default filters, `OncePerRequestFilter`, Micrometer's HTTP server metrics filter, gRPC `ServerInterceptor`.
**Best practice:** set explicit orders and put request IDs and error handling first, so every later failure is logged with an ID. **Gotcha:** filter order mistakes are silent: an auth filter before logging means rejected requests are never logged, as the demo shows.

### Ending the chain and fallbacks

**Description.** Every chain must say what happens when no handler takes the request: return a default, return "not found", throw a clear exception, or queue for later. Fallback chains also handle **failures**: if a handler throws, decide whether to try the next one or stop.

**Advantages / features.** Predictable behaviour for the "nobody handled it" case; graceful degradation when sources fail.

**EndingChainFallbacksDemo.java**

```java
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Every chain needs a defined ending: what happens when no handler takes the request? Here: a fallback chain for prices. */
public class EndingChainFallbacksDemo {

    interface PriceSource { Optional<Long> price(String sku); String name(); }

    record Source(String name, Function<String, Optional<Long>> lookup) implements PriceSource {
        public Optional<Long> price(String sku) { return lookup.apply(sku); }
    }

    static String lookup(List<PriceSource> chain, String sku) {
        for (PriceSource s : chain) {
            try {
                Optional<Long> p = s.price(sku);
                if (p.isPresent()) return sku + " = Rs " + p.get() / 100 + " (from " + s.name() + ")";
            } catch (RuntimeException e) {
                System.out.println("  " + s.name() + " failed: " + e.getMessage() + ", trying next");
            }
        }
        return sku + " = price unavailable, item hidden from the app";
    }

    public static void main(String[] args) {
        List<PriceSource> chain = List.of(
                new Source("cache", sku -> sku.equals("MILK-1L") ? Optional.of(6_800L) : Optional.empty()),
                new Source("database", sku -> { if (sku.equals("RICE-5KG")) throw new IllegalStateException("db timeout"); return sku.startsWith("BREAD") ? Optional.of(4_500L) : Optional.empty(); }),
                new Source("GreenValley supplier", sku -> sku.startsWith("MANGO") ? Optional.of(18_000L) : Optional.empty()));
        for (String sku : List.of("MILK-1L", "BREAD-WHITE", "MANGO-1KG", "RICE-5KG")) System.out.println(lookup(chain, sku));
    }
}
```

1. Sources are tried in order of cost: cache, database, supplier API.
2. A failing source is logged and skipped, not fatal.
3. The defined end: hide the item rather than show a wrong price.

**Run it:** `java EndingChainFallbacksDemo.java`

```text
MILK-1L = Rs 68 (from cache)
BREAD-WHITE = Rs 45 (from database)
MANGO-1KG = Rs 180 (from GreenValley supplier)
  database failed: db timeout, trying next
RICE-5KG = price unavailable, item hidden from the app
```

| Ending | When | Risk |
|--------|------|------|
| Default value | A safe default exists | Default hides real problems |
| Empty result / 404 | "Not found" is a valid answer | Callers must handle it |
| Exception | Unhandled means a bug or misconfiguration | Crashes if not caught at the edge |
| Queue / retry later | Temporary lack of capacity | Needs a durable queue and monitoring |

**Use** an explicit terminal handler or a clear post-loop decision. **Avoid** `null` as "nobody handled it".
**Real-world Java:** Spring's `DispatcherServlet` returns 404 when no handler mapping matches; class loading throws `ClassNotFoundException` when no loader in the chain finds the class.
**Best practice:** count how often the end of the chain is reached; a rising count is an early warning. **Gotcha:** catching exceptions to try the next handler can hide bugs; only fall through on expected failures (timeouts), not on programming errors.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** checkout applies the first coupon rule that matches the cart.

**CouponChainDemo.java**

```java
import java.util.List;
import java.util.Optional;

/** Minimal Chain of Responsibility: the first coupon rule that applies to the cart wins. */
public class CouponChainDemo {

    record Cart(long paise, boolean firstOrder, String code) { }
    interface CouponRule { Optional<String> apply(Cart c); }

    static final List<CouponRule> RULES = List.of(
            c -> "FRESH50".equals(c.code()) && c.firstOrder() ? Optional.of("Rs 50 off first order") : Optional.empty(),
            c -> "FRUIT10".equals(c.code()) ? Optional.of("10% off fruit") : Optional.empty(),
            c -> c.paise() >= 99_900 ? Optional.of("free delivery over Rs 999") : Optional.empty());

    static String discount(Cart c) {
        return RULES.stream().map(r -> r.apply(c)).flatMap(Optional::stream).findFirst().orElse("no discount");
    }

    public static void main(String[] args) {
        System.out.println(discount(new Cart(40_000, true, "FRESH50")));
        System.out.println(discount(new Cart(40_000, false, "FRESH50")));
        System.out.println(discount(new Cart(120_000, false, null)));
    }
}
```

1. Each rule either returns a discount or passes.
2. A stream finds the first rule that applies, with a defined default.

**Run it:** `java CouponChainDemo.java`

**Output:**

```text
Rs 50 off first order
no discount
free delivery over Rs 999
```

**What to notice:**

- Rule order is the business priority; changing it is a one-line move.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** each new order needs a rider. Try the cheapest option first (an idle rider nearby), then batching onto a route that ends nearby, then the ParcelGo partner fleet (more expensive), and finally queue the order to retry.

**RiderAssignmentChainDemo.java**

```java
import java.util.List;
import java.util.Optional;

/** Realistic chain: assign a new order to a rider, trying cheaper options first and falling back to partners and a queue. */
public class RiderAssignmentChainDemo {

    record Order(String id, double kmFromStore, boolean bulky) { }
    record Assignment(String order, String who, String why) { }

    interface AssignStep { Optional<Assignment> tryAssign(Order o); String name(); }

    record IdleRiderNearby(List<String> idleRiders) implements AssignStep {
        public Optional<Assignment> tryAssign(Order o) {
            return o.kmFromStore() <= 3 && !o.bulky() && !idleRiders.isEmpty()
                    ? Optional.of(new Assignment(o.id(), idleRiders.get(0), "idle rider within 3 km")) : Optional.empty();
        }
        public String name() { return "idle rider"; }
    }
    record BatchWithRoute(String routeId, double routeEndKm) implements AssignStep {
        public Optional<Assignment> tryAssign(Order o) {
            return Math.abs(o.kmFromStore() - routeEndKm) < 1.0 ? Optional.of(new Assignment(o.id(), routeId, "added to a route ending nearby")) : Optional.empty();
        }
        public String name() { return "batching"; }
    }
    record PartnerFleet(String partner) implements AssignStep {
        public Optional<Assignment> tryAssign(Order o) {
            return o.kmFromStore() <= 12 ? Optional.of(new Assignment(o.id(), partner, "partner fleet (costs more)")) : Optional.empty();
        }
        public String name() { return "partner"; }
    }

    static Assignment assign(List<AssignStep> chain, Order o) {
        for (AssignStep s : chain) { Optional<Assignment> a = s.tryAssign(o); if (a.isPresent()) return a.get(); }
        return new Assignment(o.id(), "queue", "no option now; retry in 2 minutes");
    }

    public static void main(String[] args) {
        List<AssignStep> chain = List.of(new IdleRiderNearby(List.of("R-42")), new BatchWithRoute("ROUTE-7", 5.5), new PartnerFleet("ParcelGo"));
        for (Order o : List.of(new Order("FC-9201", 1.2, false), new Order("FC-9202", 5.1, false), new Order("FC-9203", 2.0, true), new Order("FC-9204", 18, false)))
            System.out.println(assign(chain, o));
    }
}
```

1. Each step tries to assign and returns an assignment or nothing.
2. Partners cost more, so they come after in-house options.
3. The end of the chain queues the order instead of failing it.

**Run it:** `java RiderAssignmentChainDemo.java`

**Output:**

```text
Assignment[order=FC-9201, who=R-42, why=idle rider within 3 km]
Assignment[order=FC-9202, who=ROUTE-7, why=added to a route ending nearby]
Assignment[order=FC-9203, who=ParcelGo, why=partner fleet (costs more)]
Assignment[order=FC-9204, who=queue, why=no option now; retry in 2 minutes]
```

**What to notice:**

- The bulky order skipped the bike rider and went to the partner.
- The 18 km order fell to the queue with a reason, not an exception.

### Example 3: Production-grade

**Scenario:** the order API needs request IDs, error handling, authentication and idempotency in front of every endpoint. Requirements: handlers declare an explicit order and the pipeline sorts them; duplicate names or orders fail at startup; any handler can short-circuit; exceptions become a 500 response that includes the request ID; retried POSTs with the same `Idempotency-Key` replay the stored response; per-handler metrics count calls and short-circuits, safely under concurrency.

**RequestPipelineDemo.java**

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Production-grade request pipeline (what servlet filters / Spring interceptors / gRPC interceptors give you):
 * - handlers declare an explicit order; the chain is sorted, validated (no duplicate names or orders) and built ONCE
 * - each handler can enrich the request, short-circuit with a response, or call next and post-process
 * - an error handler near the front turns exceptions into a 500 with the request id; per-handler metrics
 * - the idempotency handler replays the stored response for a repeated Idempotency-Key
 */
public class RequestPipelineDemo {

    record Request(String method, String path, Map<String, String> headers, Map<String, String> attrs) {
        Request with(String k, String v) { Map<String, String> a = new HashMap<>(attrs); a.put(k, v); return new Request(method, path, headers, a); }
    }
    record Response(int status, String body) { }

    interface Next { Response call(Request r); }
    interface Handler { String name(); int order(); Response handle(Request r, Next next); }

    static final class Pipeline {
        private final Next entry; final Map<String, AtomicIntegerArray> metrics = new TreeMap<>();   // name -> [calls, shortCircuits]

        Pipeline(List<Handler> handlers, Next endpoint) {
            Set<String> names = new HashSet<>(); Set<Integer> orders = new HashSet<>();
            for (Handler h : handlers)
                if (!names.add(h.name()) || !orders.add(h.order())) throw new IllegalArgumentException("duplicate handler name or order: " + h.name());
            List<Handler> sorted = new ArrayList<>(handlers);
            sorted.sort(Comparator.comparingInt(Handler::order));
            Next chain = endpoint;
            for (int i = sorted.size() - 1; i >= 0; i--) {
                Handler h = sorted.get(i); Next next = chain; AtomicIntegerArray m = new AtomicIntegerArray(2);
                metrics.put(String.format("%02d-%s", h.order(), h.name()), m);
                chain = req -> {
                    m.incrementAndGet(0);
                    boolean[] calledNext = {false};                                              // per call: safe under concurrency
                    Response r = h.handle(req, x -> { calledNext[0] = true; return next.call(x); });
                    if (!calledNext[0]) m.incrementAndGet(1);
                    return r;
                };
            }
            this.entry = chain;
        }

        Response serve(Request r) {
            try { return entry.call(r); }
            catch (RuntimeException e) { return new Response(500, "internal error"); }        // last resort if a handler itself fails
        }
    }

    record RequestId(int[] seq) implements Handler {
        public String name() { return "request-id"; } public int order() { return 10; }
        public Response handle(Request r, Next next) { return next.call(r.with("requestId", "req-" + (++seq[0]))); }
    }
    record Errors() implements Handler {
        public String name() { return "errors"; } public int order() { return 15; }
        public Response handle(Request r, Next next) {
            try { return next.call(r); }
            catch (RuntimeException e) { return new Response(500, "internal error, ref " + r.attrs().get("requestId")); }
        }
    }
    record Auth(Map<String, String> tokens) implements Handler {
        public String name() { return "auth"; } public int order() { return 20; }
        public Response handle(Request r, Next next) {
            String user = tokens.get(r.headers().getOrDefault("Authorization", ""));
            return user == null ? new Response(401, "invalid token") : next.call(r.with("user", user));
        }
    }
    record Idempotency(Map<String, Response> seen) implements Handler {
        public String name() { return "idempotency"; } public int order() { return 30; }
        public Response handle(Request r, Next next) {
            String key = r.headers().get("Idempotency-Key");
            if (key == null || !r.method().equals("POST")) return next.call(r);
            Response earlier = seen.get(key);
            if (earlier != null) return earlier;
            Response fresh = next.call(r);
            if (fresh.status() < 500) seen.put(key, fresh);
            return fresh;
        }
    }

    public static void main(String[] args) {
        int[] orderSeq = {9300};
        Next endpoint = r -> {
            if (r.path().equals("/boom")) throw new IllegalStateException("bug in handler");
            return r.method().equals("POST") ? new Response(201, "order FC-" + (++orderSeq[0]) + " for " + r.attrs().get("user")) : new Response(200, "hello " + r.attrs().get("user"));
        };
        Pipeline p = new Pipeline(List.of(new Idempotency(new HashMap<>()), new Auth(Map.of("t-asha", "asha")), new Errors(), new RequestId(new int[1])), endpoint);

        Map<String, String> auth = Map.of("Authorization", "t-asha", "Idempotency-Key", "k-1");
        System.out.println(p.serve(new Request("POST", "/orders", auth, Map.of())));
        System.out.println(p.serve(new Request("POST", "/orders", auth, Map.of())) + "  <- retry replayed, no second order");
        System.out.println(p.serve(new Request("GET", "/me", Map.of("Authorization", "bad"), Map.of())));
        System.out.println(p.serve(new Request("GET", "/boom", Map.of("Authorization", "t-asha"), Map.of())));
        p.metrics.forEach((k, v) -> System.out.println("  " + k + ": calls=" + v.get(0) + " shortCircuits=" + v.get(1)));

        try { new Pipeline(List.of(new Auth(Map.of()), new Auth(Map.of())), endpoint); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
```

1. A handler has a name, an explicit order, and a `handle(request, next)` method.
2. Configuration mistakes (two handlers with the same order) fail at startup.
3. A handler that didn't call `next` short-circuited; the pipeline counts it.
4. The error handler sits right after request IDs, so every 500 carries a reference support can search for.
5. Idempotency short-circuits a repeated POST by replaying the stored response.

**Run it:** `java RequestPipelineDemo.java`

**Output:**

```text
Response[status=201, body=order FC-9301 for asha]
Response[status=201, body=order FC-9301 for asha]  <- retry replayed, no second order
Response[status=401, body=invalid token]
Response[status=500, body=internal error, ref req-4]
  10-request-id: calls=4 shortCircuits=0
  15-errors: calls=4 shortCircuits=0
  20-auth: calls=4 shortCircuits=1
  30-idempotency: calls=3 shortCircuits=1
duplicate handler name or order: auth
```

**What to notice:**

- The handlers were listed in the wrong order in code, but ran by their declared order.
- The retried POST returned the same order number; only one order was created.
- The crashing endpoint produced `500 ... ref req-4`, and the metrics show auth and idempotency each short-circuited once.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Decoupling | Sender doesn't know the handler | Hard to know who handled a request |
| Flexibility | Add, remove, reorder handlers | Order bugs are silent |
| Reuse | Filters shared across apps | Generic handlers can grow complex |
| Robustness | Fallbacks degrade gracefully | Unhandled requests if no terminal case |
| Performance | Early short-circuits save work | Long chains add latency to every request |
| Testability | Each handler unit-testable | Need chain-level tests for ordering |

## 8. When to Use / When NOT to Use

**Use it when:**

- several handlers might handle a request and the choice depends on the request;
- cross-cutting steps must wrap many endpoints (filters, interceptors);
- you need ordered fallbacks (cache, database, remote).

**Don't use it when:**

- exactly one handler is always known up front (call it directly);
- every handler must always run with no ordering concerns (an Observer or a simple loop);
- the decision is a clean lookup by key (use a `Map`).

## 9. Best Practices

1. **Define the chain in one place** with explicit order. *(Consensus.)*
2. **Always define the end** of the chain. *(Consensus.)*
3. **Put request IDs and error handling first**, auth before business handlers. *(Consensus.)*
4. **Keep handlers small and side-effect free** where possible. *(Consensus.)*
5. **Log or measure which handler handled a request** and how often the end is reached. *(Consensus.)*
6. **Test the assembled chain** for order, not just each handler. *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Filter order exposed private data (production failure mode)**  
> **Symptom:** an internal admin endpoint returned order data to unauthenticated users for two hours after a release.
> **Root cause:** a new caching filter was registered with a higher priority than the security filter chain, so cached admin responses were served before authentication ran.
> **Fix:** explicit orders for all filters (security first); a startup check that the security filter is at the front; an integration test calling admin endpoints without credentials.

> 🚨 **Swallowed requests**  
> **Symptom:** some refunds were never processed and nothing was logged.
> **Root cause:** the last handler returned `null` for amounts above its limit; callers treated `null` as "done".
> **Fix:** an explicit terminal handler that escalates to a queue and logs.

> 🚨 **Fallback hides an outage**  
> **Symptom:** for a week, every price came from the slow supplier API; nobody noticed until the bill arrived.
> **Root cause:** the cache handler was failing on every call and the chain silently fell through.
> **Fix:** metrics per handler (hit, miss, error); alert when an early handler stops handling.

> 🚨 **Business logic in filters**  
> **Symptom:** discounts applied twice, and nobody could find where.
> **Root cause:** a "promo filter" modified request bodies before the controller.
> **Fix:** keep filters for cross-cutting concerns; put business rules in services.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `@Order` / `FilterRegistrationBean.setOrder` | lowest precedence | Position of a servlet filter | Every custom filter | Wrong order: security or logging gaps |
| `spring.security.filter.order` | -100 | Position of the security chain | Rarely | Filters before it run unauthenticated |
| `SecurityFilterChain` `securityMatcher` | all requests | Which chain handles which paths | Multiple chains | Overlapping matchers: first match wins |
| Handler timeouts | none | Max time per fallback source | Remote handlers | Too short: unnecessary fallbacks |

## 12. Security Considerations

- Authentication and authorisation must run before anything that reads or caches business data.
- Every path must go through the security chain; test unauthenticated access to sensitive endpoints.
- Don't let a fallback bypass a security check ("if auth service is down, allow"); fail closed.
- Filters that log requests must mask secrets (tokens, passwords, card numbers).

## 13. Performance & Scalability

Each handler adds a little latency to every request, so keep filters fast and short-circuit early (reject bad tokens before parsing bodies). Fallback chains should order handlers by cost and success rate, and give remote handlers timeouts so a slow source doesn't hold the whole request. Measure per-handler latency; one slow filter slows every endpoint.

## 14. Testing It

Test order, short-circuiting, error mapping, startup validation and idempotency of the assembled pipeline:

**RequestPipelineTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestPipelineTest {

    record Recording(String name, int order, List<String> log) implements RequestPipelineDemo.Handler {
        public RequestPipelineDemo.Response handle(RequestPipelineDemo.Request r, RequestPipelineDemo.Next next) {
            log.add(name + " before");
            var resp = next.call(r);
            log.add(name + " after");
            return resp;
        }
    }

    private static RequestPipelineDemo.Request get(Map<String, String> headers) {
        return new RequestPipelineDemo.Request("GET", "/x", headers, Map.of());
    }

    @Test
    void runsHandlersInDeclaredOrderRegardlessOfListOrder() {
        List<String> log = new ArrayList<>();
        var p = new RequestPipelineDemo.Pipeline(List.of(new Recording("b", 2, log), new Recording("a", 1, log)),
                r -> new RequestPipelineDemo.Response(200, "ok"));
        p.serve(get(Map.of()));
        assertEquals(List.of("a before", "b before", "b after", "a after"), log);
    }

    @Test
    void authShortCircuitsBeforeTheEndpoint() {
        int[] endpointCalls = {0};
        var p = new RequestPipelineDemo.Pipeline(List.of(new RequestPipelineDemo.Auth(Map.of())),
                r -> { endpointCalls[0]++; return new RequestPipelineDemo.Response(200, "ok"); });
        assertEquals(401, p.serve(get(Map.of())).status());
        assertEquals(0, endpointCalls[0]);
    }

    @Test
    void exceptionsBecome500() {
        var p = new RequestPipelineDemo.Pipeline(List.of(), r -> { throw new IllegalStateException("x"); });
        assertEquals(500, p.serve(get(Map.of())).status());
    }

    @Test
    void duplicateOrdersAreRejected() {
        List<String> log = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> new RequestPipelineDemo.Pipeline(
                List.of(new Recording("a", 1, log), new Recording("b", 1, log)), r -> null));
    }

    @Test
    void idempotencyReplaysPostResponses() {
        int[] created = {0};
        var p = new RequestPipelineDemo.Pipeline(List.of(new RequestPipelineDemo.Idempotency(new HashMap<>())),
                r -> new RequestPipelineDemo.Response(201, "order " + (++created[0])));
        var post = new RequestPipelineDemo.Request("POST", "/orders", Map.of("Idempotency-Key", "k"), Map.of());
        assertEquals(p.serve(post), p.serve(post));
        assertEquals(1, created[0]);
    }
}
```

In Spring, `MockMvc` tests run the real filter chain; include at least one test per security rule.

## 15. Observability & Operations

Add the request ID in the first filter and put it in the logging context (MDC) so every log line carries it. Export per-handler metrics: calls, short-circuits, errors and latency. Spring Boot's `/actuator/mappings` and the security debug log (`logging.level.org.springframework.security=DEBUG`) show filter chains and their order when debugging.

## 16. Version Notes

| Version | Change relevant to Chain of Responsibility |
|---------|--------------------------------------------|
| Servlet 2.3 | `Filter` and `FilterChain` |
| Java 8 | Lambdas and `Optional`: list-based chains of functions |
| Spring Security 5.7 | `SecurityFilterChain` beans replace `WebSecurityConfigurerAdapter` |
| Spring Boot 3 / Jakarta EE 9+ | `javax.servlet` → `jakarta.servlet` |
| Java 21 | Virtual threads: blocking filters are cheap per request |

## 17. Expert Deep Dive

- **Middleware everywhere.** The `handle(request, next)` shape is the same in servlet filters, gRPC interceptors, OkHttp interceptors, Netty pipelines and Kafka consumer interceptors. Learn it once.
- **Chain vs Decorator.** A filter chain is a list of decorators around an endpoint built at runtime. Decorator emphasises adding behaviour to one object; Chain emphasises passing a request through optional handlers that may stop it.
- **Ordering as a contract.** In large codebases, give filters named order constants (`ORDER_REQUEST_ID = 0`, `ORDER_SECURITY = 100`) and validate at startup; ordering bugs are among the hardest to spot in review.
- **Resilience chains.** Fallbacks combine with timeouts, circuit breakers and bulkheads; see [Resilience Patterns](../l4-senior/resilience-patterns.md).

> 📖 **War story: the rate limiter that limited everyone**  
> A grocery API added a rate-limiting filter keyed by client IP to stop scraping. It was registered before the filter that reads the `X-Forwarded-For` header from the load balancer, so every request appeared to come from the load balancer's IP. At the evening peak, the whole country shared one rate limit, and most customers got `429 Too Many Requests`. **Fixes:** the forwarded-headers filter got the first position by explicit order; the rate limiter keys by authenticated user, falling back to the real client IP; and a test asserts the filter order. **Lesson:** in a chain, a handler only sees what earlier handlers have prepared.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is Chain of Responsibility?** Passing a request along **handlers until one handles it** (or each processes it).
2. **Give a Java example.** **Servlet filters**, Spring Security's filter chain.
3. **What happens if no handler handles the request?** It must be **defined**: default, error, or queue.
4. **How do you stop a filter chain?** **Don't call `chain.doFilter`** (return a response).
5. **Give a FreshCart example.** **Refund approval**: bot, agent, supervisor.

**▸ 🟡 L3**

1. **Linked handlers vs list pipeline?** Links are GoF; lists are **clearer, easier to reorder and test**.
2. **Why does filter order matter?** Later filters depend on **what earlier ones prepared** (auth, request IDs, forwarded headers).
3. **Filter vs HandlerInterceptor?** Filters wrap the **whole servlet**; interceptors wrap **controller handling**.
4. **Chain vs Decorator?** Similar shape; chain handlers **may stop** the request, decorators always delegate.
5. **How do you test a chain?** Unit-test handlers, then **test the assembled chain** for order and short-circuits.

**▸ 🔴 L4–L5**

1. **Design the request pipeline for a public API.** **Request ID, error mapping, forwarded headers, auth, rate limit, idempotency, validation**, with explicit order and metrics.
2. **How do you prevent ordering regressions?** **Named order constants, startup validation, integration tests** for security and headers.
3. **Design a price-lookup fallback chain.** **Cache → DB → supplier**, timeouts, circuit breakers, metrics, safe terminal behaviour.
4. **When is a chain the wrong tool?** When routing is a **key lookup** or **all handlers must run** (events).
5. **How do you debug "who handled this request?"** **Per-handler metrics and logs with request IDs**, security debug logs.

**▸ 💻 Hands-on**

**Task:** Add a timing handler to `RequestPipelineDemo` that records the latency of the rest of the chain.

> ✅ **Solution**  
> ```java
> record Timing(Map<String, Long> lastNanos) implements Handler {
>     public String name() { return "timing"; }
>     public int order() { return 12; }
>     public Response handle(Request r, Next next) {
>         long start = System.nanoTime();
>         try { return next.call(r); }
>         finally { lastNanos.put(r.path(), System.nanoTime() - start); }
>     }
> }
> ```

## 19. Practice

- [ ] Add a `Finance` approver to `LinkedHandlersDemo` that approves anything but requires a reason.
    > 💡 **Hint**  
    > Add a `reason` field to `RefundRequest`.

- [ ] Add a rate-limit handler to `RequestPipelineDemo` keyed by user, at an order after auth.
    > 💡 **Hint**  
    > Return 429 when a user exceeds N requests in the current minute; use an injected clock.

- [ ] Make `EndingChainFallbacksDemo` stop falling through on `IllegalArgumentException` (a bug), but continue on timeouts.
    > 💡 **Hint**  
    > Catch only a `TimeoutException`-like type for fallthrough.

- [ ] List the filters in a Spring Boot application and their order.
    > 💡 **Hint**  
    > Set `logging.level.org.springframework.security=DEBUG` and read the startup log.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: pass a request along handlers; decouple sender from handler.
> - Variants: first-one-wins (approvals, fallbacks) and filter pipelines (`handle(request, next)`).
> - Order matters: request ID and errors first, then security, then business.
> - Always define the end: default, 404, exception, or queue.
> - Measure per handler; test the assembled chain.
>
> ```java
> interface Filter { Response doFilter(Request req, Chain next); }
> Filter auth = (req, next) -> req.hasToken() ? next.proceed(req) : new Response(401, "login required");
> ```

## 21. Further Reading

- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994), "Chain of Responsibility".
- [Jakarta Servlet Filter API](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/filter)
- [Spring Security reference: Architecture (the filter chain)](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring MVC reference: Interception](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html)
- [gRPC Java interceptors](https://grpc.io/docs/guides/interceptors/)

---

[← Previous: State](state.md) · [Next: Mediator →](mediator.md)
