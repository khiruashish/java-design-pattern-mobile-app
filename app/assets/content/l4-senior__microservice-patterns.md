# Microservice Patterns

L4 🟠 Senior / Lead · ⏱ 40 min read · Prerequisites: [Domain-Driven Design: Strategic Patterns](ddd-strategic.md), [Architectural Patterns](architectural-patterns.md)

> 📌 **TL;DR**  
> Microservices are independently deployable services, each owning one business capability and its data. **Decompose by capability or subdomain**, not by entity, so a use case touches few services. Give each service **its own database**; share data through APIs and events. Put an **API gateway** in front for routing, authentication, rate limits and request IDs. Use **service discovery** so callers find live instances. Migrate from a monolith with the **strangler fig**: route one slice at a time to new services, shadow-compare first, keep rollback cheap. Move cross-cutting network concerns (mTLS, retries, metrics) into a **sidecar** or service mesh. Microservices buy team autonomy and independent scaling at the price of distributed-systems complexity, so start from a modular monolith unless you have the teams and the need.

## 1. What It Is

**Plain English.** Instead of one big application, FreshCart runs several smaller ones (ordering, inventory, payments, fulfilment), each built and deployed by its own team, talking over the network. These patterns answer the questions that come up immediately: where to cut, who owns which data, how clients reach the services, how services find each other, how to get there from a monolith, and how to handle networking concerns consistently.

**Definition.**

| Pattern | Purpose |
|---------|---------|
| Decomposition by capability / subdomain | Draw service boundaries around business capabilities, not tables |
| Database per service | Each service owns its data; no shared tables |
| API gateway | Single entry point for clients: routing, auth, limits, aggregation |
| Service discovery | Registry of live instances; clients or load balancers look them up |
| Strangler fig | Incrementally replace a legacy system behind a routing facade |
| Sidecar | Helper process alongside each instance for cross-cutting concerns |

## 2. Why It Exists

A monolith worked on by 80 engineers becomes a coordination problem: one deploy pipeline, one release train, one database schema everybody changes, and one hot component forces scaling everything. Microservices let teams deploy independently and scale only what's hot. But the network is not a method call: it fails, it's slow, and data is no longer in one transaction. These patterns exist to get the autonomy without a **distributed monolith**: services that must be deployed together, share a database and call each other synchronously for every request.

## 3. Mental Model / Analogy

**Analogy.** A food court instead of one restaurant kitchen. Each stall (service) has its own cooks, fridge (database) and menu. A front desk (gateway) takes orders and sends them to the right stall. A board shows which stalls are open (discovery). When the old kitchen is replaced, stalls open one by one while the kitchen keeps serving the rest (strangler fig). Every stall gets the same standard card reader bolted on (sidecar), whatever it cooks.

**Technical model.** Services are small, deployable units aligned with bounded contexts, communicating over HTTP/gRPC or messaging, each with its own data store, run on a platform (Kubernetes) that provides discovery, scaling and often a mesh.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Decomposition by capability and subdomain

**Description.** Service boundaries follow **business capabilities** (what the business does: ordering, inventory, payments, fulfilment) or DDD **subdomains** and bounded contexts. The common mistake is decomposing by **entity** (CustomerService, ProductService, PriceService), which makes almost every use case a chain of network calls and every feature a multi-team change.

**Advantages / features.** Fewer cross-service calls per use case; a feature usually changes one service; services map to teams; boundaries are stable because capabilities change slowly.

**DecompositionDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decomposition by business capability vs by entity: count how many network hops each use case would need.
 * Fewer cross-service hops per use case means less latency, fewer partial failures and more team autonomy.
 */
public class DecompositionDemo {

    record UseCase(String name, List<String> steps) {}

    static final List<UseCase> USE_CASES = List.of(
            new UseCase("place order", List.of("cart", "price", "reserve-stock", "order", "payment")),
            new UseCase("track order", List.of("order", "delivery-status", "rider-location")),
            new UseCase("restock shelf", List.of("reserve-stock", "supplier-po")),
            new UseCase("refund", List.of("order", "payment", "refund-policy")));

    /** Maps each step to the service that owns it, for one candidate decomposition. */
    static int crossServiceHops(Map<String, String> owner) {
        int hops = 0;
        for (UseCase uc : USE_CASES)
            for (int i = 1; i < uc.steps().size(); i++)
                if (!owner.get(uc.steps().get(i)).equals(owner.get(uc.steps().get(i - 1)))) hops++;
        return hops;
    }

    static long services(Map<String, String> owner) { return owner.values().stream().distinct().count(); }

    public static void main(String[] args) {
        Map<String, String> byEntity = new LinkedHashMap<>();
        byEntity.put("cart", "CartService"); byEntity.put("price", "PriceService"); byEntity.put("reserve-stock", "ProductService");
        byEntity.put("order", "OrderService"); byEntity.put("payment", "PaymentService"); byEntity.put("delivery-status", "DeliveryService");
        byEntity.put("rider-location", "RiderService"); byEntity.put("supplier-po", "SupplierService"); byEntity.put("refund-policy", "RefundService");

        Map<String, String> byCapability = new LinkedHashMap<>();
        byCapability.put("cart", "Ordering"); byCapability.put("price", "Ordering"); byCapability.put("order", "Ordering");
        byCapability.put("reserve-stock", "Inventory"); byCapability.put("supplier-po", "Inventory");
        byCapability.put("payment", "Payments"); byCapability.put("refund-policy", "Payments");
        byCapability.put("delivery-status", "Fulfilment"); byCapability.put("rider-location", "Fulfilment");

        System.out.println("by entity:     " + services(byEntity) + " services, " + crossServiceHops(byEntity) + " cross-service hops across " + USE_CASES.size() + " use cases");
        System.out.println("by capability: " + services(byCapability) + " services, " + crossServiceHops(byCapability) + " cross-service hops");
        for (UseCase uc : USE_CASES)
            System.out.println("  " + uc.name() + ": " + uc.steps().stream().map(byCapability::get).distinct().toList());
    }
}
```

1. Four real use cases, each as the sequence of steps it needs.
2. A hop is counted whenever consecutive steps live in different services; each hop is latency and a partial-failure point.
3. Entity decomposition gives nine services; capability decomposition gives four with fewer hops.

**Run it:** `java DecompositionDemo.java`

```text
by entity:     9 services, 9 cross-service hops across 4 use cases
by capability: 4 services, 5 cross-service hops
  place order: [Ordering, Inventory, Payments]
  track order: [Ordering, Fulfilment]
  restock shelf: [Inventory]
  refund: [Ordering, Payments]
```

| Boundary heuristic | Good sign | Bad sign |
|--------------------|-----------|----------|
| Business capability | Named with a verb-ish noun (Ordering, Fulfilment) | Named after a table (CustomerService) |
| Change locality | Most features change one service | Every feature changes three |
| Data ownership | Writes for a concept happen in one service | Two services update the same record |
| Team | One team owns it end to end | Ownership split across teams |
| Chattiness | Few synchronous calls per request | Call chains five deep |

**Use** capability decomposition aligned with bounded contexts. **Avoid** entity services and "nano-services" with one endpoint each.
**Real-world Java:** Spring Boot services per capability; Spring Modulith to validate boundaries inside a monolith first.
**Best practice:** prove boundaries as modules in a modular monolith, then extract. **Gotcha:** a wrong boundary is far more expensive to fix across services than across packages.

### Database per service

**Description.** Each service owns its schema or database; no other service reads or writes it directly. Other services get data through the owner's API or by subscribing to its events and keeping a **local copy** of the fields they need.

**Advantages / features.** Services change their schema freely; data store per need (Postgres for orders, Redis for carts, Elasticsearch for search); failures and load stay isolated.

**DatabasePerServiceDemo.java**

```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Database per service: each service owns its data. Others get what they need through APIs or events and keep
 * their own copies, so a schema change in one service can't break another.
 */
public class DatabasePerServiceDemo {

    // ---------- Customer service: owns customers ----------
    record CustomerChanged(String customerId, String displayName) {}
    static final class CustomerService {
        private final Map<String, Map<String, String>> customerTable = new HashMap<>();
        private final List<Consumer<CustomerChanged>> subscribers = new java.util.ArrayList<>();
        void subscribe(Consumer<CustomerChanged> s) { subscribers.add(s); }
        void register(String id, String first, String last) {
            customerTable.put(id, new HashMap<>(Map.of("first_name", first, "last_name", last)));
            subscribers.forEach(s -> s.accept(new CustomerChanged(id, first + " " + last.charAt(0) + ".")));
        }
        void migrateSchemaToFullName() {
            customerTable.values().forEach(row -> row.put("full_name", row.remove("first_name") + " " + row.remove("last_name")));
        }
    }

    // ---------- Order service: owns orders and a local copy of what it needs about customers ----------
    static final class OrderService {
        private final Map<String, String> customerNames = new HashMap<>();
        private final Map<String, String> orders = new HashMap<>();
        OrderService(CustomerService customers) { customers.subscribe(e -> customerNames.put(e.customerId(), e.displayName())); }
        void place(String orderId, String customerId) { orders.put(orderId, customerId); }
        String packingSlip(String orderId) { return orderId + " for " + customerNames.getOrDefault(orders.get(orderId), "(unknown)"); }
    }

    public static void main(String[] args) {
        CustomerService customers = new CustomerService();
        OrderService orders = new OrderService(customers);
        customers.register("C-1", "Asha", "Rao");
        orders.place("FC-1", "C-1");
        System.out.println("before customer schema change: " + orders.packingSlip("FC-1"));
        customers.migrateSchemaToFullName();
        System.out.println("after customer schema change:  " + orders.packingSlip("FC-1"));

        // Shared-database alternative: orders reading customers' table directly would now break.
        Map<String, String> row = customers.customerTable.get("C-1");
        System.out.println("a cross-service SQL join on first_name would now see: " + row.get("first_name"));
    }
}
```

1. Customer publishes an event with only the data others need, in its published language.
2. Customer changes its internal schema; nobody else has to know.
3. Ordering keeps its own copy of the display name, so packing slips keep working.

**Run it:** `java DatabasePerServiceDemo.java`

```text
before customer schema change: FC-1 for Asha R.
after customer schema change:  FC-1 for Asha R.
a cross-service SQL join on first_name would now see: null
```

| Need | Pattern |
|------|---------|
| Read another service's data occasionally | Call its API |
| Read it on every request | Local replica via events |
| Update data in two services together | Saga (see Distributed Data Patterns) |
| Publish changes reliably | Transactional outbox or CDC |
| Report across services | Analytics store fed by events |

**Use** a private schema per service from day one (even on a shared database server). **Avoid** cross-service joins and "just one read-only view".
**Real-world Java:** a Flyway migration folder per service; separate DB users with grants only on their own schema.
**Best practice:** enforce ownership with database permissions, not convention. **Gotcha:** local copies are eventually consistent; design UI and rules for staleness.

### API Gateway

**Description.** A single entry point between clients and services. It routes by path or host, terminates TLS, authenticates tokens, applies rate limits, adds request IDs and may aggregate several services into one response (a BFF is a gateway per client type).

**Advantages / features.** Clients see one stable API; services stay simpler; cross-cutting policies live in one place; internal topology can change without app releases.

**ApiGatewayDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** An API gateway: one entry point that routes, authenticates, rate-limits and adds request IDs for every service. */
public class ApiGatewayDemo {

    record Request(String path, Map<String, String> headers) {}
    record Response(int status, String body) {}

    static final class Gateway {
        private final Map<String, Function<Request, Response>> routes = new HashMap<>();
        private final Map<String, Integer> callsThisMinute = new HashMap<>();
        private final int limitPerMinute; private int requestSeq = 1;
        final List<String> accessLog = new ArrayList<>();
        Gateway(int limitPerMinute) { this.limitPerMinute = limitPerMinute; }
        void route(String prefix, Function<Request, Response> service) { routes.put(prefix, service); }

        Response handle(Request in) {
            String token = in.headers().getOrDefault("Authorization", "");
            if (!token.startsWith("Bearer ")) return log(in, new Response(401, "missing token"));
            String client = token.substring(7);
            if (callsThisMinute.merge(client, 1, Integer::sum) > limitPerMinute) return log(in, new Response(429, "slow down"));
            String prefix = "/" + in.path().split("/")[1];
            Function<Request, Response> service = routes.get(prefix);
            if (service == null) return log(in, new Response(404, "no route"));
            Map<String, String> forwarded = new HashMap<>(in.headers());
            forwarded.put("X-Request-Id", "req-" + requestSeq++);
            forwarded.put("X-Client", client);
            forwarded.remove("Authorization");
            return log(in, service.apply(new Request(in.path(), forwarded)));
        }
        private Response log(Request in, Response r) { accessLog.add(r.status() + " " + in.path()); return r; }
    }

    public static void main(String[] args) {
        Gateway gw = new Gateway(3);
        gw.route("/catalog", r -> new Response(200, "products for " + r.headers().get("X-Client") + " (" + r.headers().get("X-Request-Id") + ")"));
        gw.route("/orders", r -> new Response(200, "orders, saw auth header: " + r.headers().containsKey("Authorization")));

        Map<String, String> asha = Map.of("Authorization", "Bearer app-asha");
        System.out.println(gw.handle(new Request("/catalog/dairy", asha)));
        System.out.println(gw.handle(new Request("/orders/FC-1", asha)));
        System.out.println(gw.handle(new Request("/wallet/balance", asha)));
        System.out.println(gw.handle(new Request("/catalog/fruit", asha)));
        System.out.println(gw.handle(new Request("/catalog/dairy", Map.of())));
        System.out.println("log: " + gw.accessLog);
    }
}
```

1. Authentication happens once, at the edge.
2. Per-client rate limits protect every service behind the gateway.
3. The gateway adds a request ID and client identity and strips the raw token before forwarding.

**Run it:** `java ApiGatewayDemo.java`

```text
Response[status=200, body=products for app-asha (req-1)]
Response[status=200, body=orders, saw auth header: false]
Response[status=404, body=no route]
Response[status=429, body=slow down]
Response[status=401, body=missing token]
log: [200 /catalog/dairy, 200 /orders/FC-1, 404 /wallet/balance, 429 /catalog/fruit, 401 /catalog/dairy]
```

| Gateway responsibility | Keep in gateway? |
|------------------------|------------------|
| Routing, TLS, auth token validation | Yes |
| Rate limiting, request IDs, CORS | Yes |
| Response aggregation for one client | In a BFF |
| Business rules (discounts, stock checks) | No, belongs in services |
| Per-service authorisation decisions | No, services must still check |

**Use** a gateway for any externally exposed service set. **Avoid** turning it into a monolith of business logic owned by a central team.
**Real-world Java:** Spring Cloud Gateway, Netflix Zuul (legacy), Kong, Envoy, AWS API Gateway.
**Best practice:** one gateway (or BFF) per client type, owned close to that client's team. **Gotcha:** the gateway is a single point of failure; run it redundantly and keep it stateless.

### Service discovery

**Description.** Instances come and go (autoscaling, deploys, crashes). A **registry** tracks live instances via registration and heartbeats or health checks. In **client-side** discovery the caller queries the registry and load-balances; in **server-side** discovery a load balancer or the platform (Kubernetes Services, DNS) does it.

**Advantages / features.** No hardcoded addresses; dead instances drop out automatically; enables load balancing and zero-downtime deploys.

**ServiceDiscoveryDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service discovery: instances register with a heartbeat; entries expire if heartbeats stop; clients look up live
 * instances and load-balance across them.
 */
public class ServiceDiscoveryDemo {

    record Instance(String service, String address) {}

    static final class Registry {
        private final Map<String, Long> lastHeartbeat = new TreeMap<>();
        private final long ttlSeconds;
        Registry(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        void heartbeat(Instance i, long now) { lastHeartbeat.put(i.service() + "@" + i.address(), now); }
        List<String> live(String service, long now) {
            List<String> out = new ArrayList<>();
            lastHeartbeat.forEach((key, seen) -> {
                if (key.startsWith(service + "@") && now - seen <= ttlSeconds) out.add(key.substring(service.length() + 1));
            });
            return out;
        }
    }

    static final class RoundRobinClient {
        private final Registry registry; private final String service; private int next;
        RoundRobinClient(Registry registry, String service) { this.registry = registry; this.service = service; }
        String pick(long now) {
            List<String> live = registry.live(service, now);
            if (live.isEmpty()) throw new IllegalStateException("no live instances of " + service);
            return live.get(next++ % live.size());
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry(30);
        var a = new Instance("pricing", "10.0.0.11:8080");
        var b = new Instance("pricing", "10.0.0.12:8080");
        var c = new Instance("pricing", "10.0.0.13:8080");
        for (var i : List.of(a, b, c)) registry.heartbeat(i, 0);
        RoundRobinClient client = new RoundRobinClient(registry, "pricing");

        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 3; i++) picks.add(client.pick(10));
        System.out.println("t=10s picks: " + picks);

        registry.heartbeat(a, 25); registry.heartbeat(c, 25);    // b crashed and stopped sending heartbeats
        System.out.println("t=40s live: " + registry.live("pricing", 40));
        picks.clear();
        for (int i = 0; i < 4; i++) picks.add(client.pick(40));
        System.out.println("t=40s picks: " + picks);
        try { client.pick(100); } catch (IllegalStateException e) { System.out.println("t=100s: " + e.getMessage()); }
    }
}
```

1. The registry records the last heartbeat per instance.
2. An instance is live only while its heartbeat is within the TTL.
3. A client-side load balancer picks round-robin among live instances.

**Run it:** `java ServiceDiscoveryDemo.java`

```text
t=10s picks: [10.0.0.11:8080, 10.0.0.12:8080, 10.0.0.13:8080]
t=40s live: [10.0.0.11:8080, 10.0.0.13:8080]
t=40s picks: [10.0.0.13:8080, 10.0.0.11:8080, 10.0.0.13:8080, 10.0.0.11:8080]
t=100s: no live instances of pricing
```

| Style | Example | Trade-off |
|-------|---------|-----------|
| Client-side | Eureka + Spring Cloud LoadBalancer | Smart clients, per-language libraries |
| Server-side | Kubernetes Service, AWS ALB | Simple clients, extra hop |
| DNS-based | Kubernetes DNS, Consul DNS | Universal; DNS caching delays updates |
| Mesh | Istio/Linkerd via sidecars | Rich routing; operational complexity |

**Use** the platform's discovery (Kubernetes Services) when you have it. **Avoid** running your own registry on Kubernetes without a reason.
**Real-world Java:** Spring Cloud Kubernetes, Netflix Eureka, Consul.
**Best practice:** combine discovery with readiness probes so instances only receive traffic when ready. **Gotcha:** the JVM caches DNS (`networkaddress.cache.ttl`); stale caches send traffic to dead IPs.

### Strangler fig

**Description.** Replace a legacy system incrementally: put a routing facade in front, move one capability at a time to new services, route its traffic there, and leave the rest on the legacy system until it can be switched off. Shadowing (calling both, comparing, serving the old answer) proves the new service before cut-over.

**Advantages / features.** Value delivered continuously; each step is small and reversible; no big-bang rewrite.

**StranglerFigDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Strangler fig: a routing facade sends migrated endpoints to new services and everything else to the legacy system. */
public class StranglerFigDemo {

    static final Function<String, String> LEGACY = path -> "legacy monolith handled " + path;
    static final Function<String, String> CATALOG_SERVICE = path -> "catalog-service handled " + path;
    static final Function<String, String> SLOTS_SERVICE = path -> "slots-service handled " + path;

    static final class Facade {
        private final Map<String, Function<String, String>> migrated = new LinkedHashMap<>();
        void migrate(String prefix, Function<String, String> service) { migrated.put(prefix, service); }
        void rollback(String prefix) { migrated.remove(prefix); }
        String handle(String path) {
            return migrated.entrySet().stream().filter(e -> path.startsWith(e.getKey())).findFirst()
                    .map(e -> e.getValue().apply(path)).orElseGet(() -> LEGACY.apply(path));
        }
    }

    public static void main(String[] args) {
        Facade facade = new Facade();
        List<String> traffic = List.of("/catalog/dairy", "/slots/today", "/checkout", "/account");
        System.out.println("phase 0:"); traffic.forEach(p -> System.out.println("  " + facade.handle(p)));
        facade.migrate("/catalog", CATALOG_SERVICE);
        facade.migrate("/slots", SLOTS_SERVICE);
        System.out.println("phase 1:"); traffic.forEach(p -> System.out.println("  " + facade.handle(p)));
        facade.rollback("/slots");
        System.out.println("slots rolled back: " + facade.handle("/slots/today"));
    }
}
```

1. Everything starts on the legacy monolith.
2. Migrating a slice is a routing change.
3. Unmigrated paths fall through to legacy, and rollback is just removing a route.

**Run it:** `java StranglerFigDemo.java`

```text
phase 0:
  legacy monolith handled /catalog/dairy
  legacy monolith handled /slots/today
  legacy monolith handled /checkout
  legacy monolith handled /account
phase 1:
  catalog-service handled /catalog/dairy
  slots-service handled /slots/today
  legacy monolith handled /checkout
  legacy monolith handled /account
slots rolled back: legacy monolith handled /slots/today
```

| Step | Technique |
|------|-----------|
| Intercept | Gateway, reverse proxy or facade in front of legacy |
| Choose slice | Edge capability with clear data, low coupling |
| Prove | Shadow traffic, compare results, dark launch |
| Cut over | Percentage rollout or feature flag; keep rollback |
| Retire | Delete legacy code and data for the slice |

**Use** for any significant legacy replacement. **Avoid** extracting the most tangled core first.
**Real-world Java:** Spring Cloud Gateway routes, Nginx/Envoy, feature flags (Unleash, LaunchDarkly), CDC to sync data during migration.
**Best practice:** track "percentage strangled" and actually delete legacy code. **Gotcha:** two systems writing the same data during migration need a clear owner per field.

### Sidecar

**Description.** A separate process (container) deployed with each service instance, sharing its network namespace, that handles cross-cutting concerns: mTLS, retries, timeouts, metrics, tracing, log shipping, config. A **service mesh** is sidecars everywhere plus a control plane.

**Advantages / features.** Consistent behaviour across languages; concerns upgraded without redeploying apps; app code stays focused on business logic.

**SidecarDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sidecar: a helper process next to each service instance handles cross-cutting concerns (retries, mTLS identity,
 * metrics) for any language, without changing application code. Here the sidecar wraps the app's outbound calls.
 */
public class SidecarDemo {

    record Call(String target, String body, String identity) {}

    /** The application only knows how to make a plain call. */
    static String appLogic(Function<Call, String> outbound) {
        return outbound.apply(new Call("pricing", "price SKU MILK-1L", null));
    }

    /** The sidecar adds identity, retries and metrics around every outbound call. */
    static final class Sidecar implements Function<Call, String> {
        private final Function<Call, String> network; private final String serviceIdentity;
        final List<String> metrics = new ArrayList<>();
        Sidecar(Function<Call, String> network, String serviceIdentity) { this.network = network; this.serviceIdentity = serviceIdentity; }
        public String apply(Call c) {
            Call withIdentity = new Call(c.target(), c.body(), "spiffe://freshcart/" + serviceIdentity);
            for (int attempt = 1; ; attempt++) {
                try {
                    String r = network.apply(withIdentity);
                    metrics.add(c.target() + " ok attempt=" + attempt);
                    return r;
                } catch (RuntimeException e) {
                    metrics.add(c.target() + " error attempt=" + attempt);
                    if (attempt == 3) throw e;
                }
            }
        }
    }

    public static void main(String[] args) {
        int[] calls = {0};
        Function<Call, String> flakyNetwork = c -> {
            if (++calls[0] == 1) throw new IllegalStateException("connection reset");
            if (c.identity() == null) return "403 no client identity";
            return "200 Rs 68 (caller " + c.identity() + ")";
        };
        System.out.println("without sidecar: " + safely(() -> appLogic(flakyNetwork)));
        calls[0] = 0;
        Sidecar sidecar = new Sidecar(flakyNetwork, "checkout");
        System.out.println("with sidecar:    " + appLogic(sidecar));
        System.out.println("sidecar metrics: " + sidecar.metrics);
    }

    static String safely(java.util.function.Supplier<String> s) { try { return s.get(); } catch (RuntimeException e) { return "failed: " + e.getMessage(); } }
}
```

1. The application makes a plain call and knows nothing about identity or retries.
2. The sidecar wraps every outbound call.
3. It attaches a workload identity (like a SPIFFE ID in mTLS), retries and records metrics.

**Run it:** `java SidecarDemo.java`

```text
without sidecar: failed: connection reset
with sidecar:    200 Rs 68 (caller spiffe://freshcart/checkout)
sidecar metrics: [pricing error attempt=1, pricing ok attempt=2]
```

| Concern | Library in app | Sidecar / mesh |
|---------|----------------|----------------|
| Retries, timeouts | Resilience4j | Envoy retry policy |
| mTLS identity | Manual keystores | Automatic certificate rotation |
| Metrics, tracing | Micrometer, OpenTelemetry agent | Proxy-level metrics |
| Polyglot consistency | One library per language | Same for all |
| Debuggability | In process | Extra hop, more moving parts |

**Use** a mesh when you have many services, several languages or strict mTLS needs. **Avoid** stacking app-level and mesh retries without coordination.
**Real-world Java:** Istio/Envoy, Linkerd, Dapr sidecars; Kubernetes native sidecar containers (1.29+).
**Best practice:** decide per concern whether the app or the mesh owns it. **Gotcha:** retries in both the app and the sidecar multiply (3 x 3 = 9 attempts) during an outage.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's Ordering service needs prices from Pricing. Pricing's internal rules change weekly; Ordering must not care.

**OrderPricingContractDemo.java**

```java
import java.util.Map;

/** Minimal: Ordering calls Pricing through a small, versioned contract, never through Pricing's internal model. */
public class OrderPricingContractDemo {

    // ----- the contract both teams agree on (published, versioned) -----
    record PriceQuoteV1(String sku, long unitPricePaise, String currency) {}
    interface PricingApi { PriceQuoteV1 quote(String sku); }

    // ----- Pricing's internal model can change freely -----
    record PriceRule(String sku, long basePaise, int promoPercent, String region) {}
    static final class PricingService implements PricingApi {
        private final Map<String, PriceRule> rules = Map.of(
                "MILK-1L", new PriceRule("MILK-1L", 6800, 0, "BLR"),
                "CURD-500G", new PriceRule("CURD-500G", 4500, 10, "BLR"));
        public PriceQuoteV1 quote(String sku) {
            PriceRule r = rules.get(sku);
            return new PriceQuoteV1(sku, r.basePaise() * (100 - r.promoPercent()) / 100, "INR");
        }
    }

    // ----- Ordering only depends on the contract -----
    static long orderTotalPaise(PricingApi pricing, Map<String, Integer> lines) {
        return lines.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .mapToLong(e -> pricing.quote(e.getKey()).unitPricePaise() * e.getValue()).sum();
    }

    public static void main(String[] args) {
        PricingApi pricing = new PricingService();
        long total = orderTotalPaise(pricing, Map.of("MILK-1L", 2, "CURD-500G", 1));
        System.out.println("CURD-500G quote: " + pricing.quote("CURD-500G"));
        System.out.printf("order total: Rs %d.%02d%n", total / 100, total % 100);
        PricingApi stub = sku -> new PriceQuoteV1(sku, 1000, "INR");
        System.out.println("with a stub (Ordering's own tests): Rs " + orderTotalPaise(stub, Map.of("MILK-1L", 3)) / 100);
    }
}
```

1. The contract is a small versioned DTO, not Pricing's internal rule model.
2. Ordering depends only on the contract, so it can be tested with a stub.

**Run it:** `java OrderPricingContractDemo.java`

**Output:**

```text
CURD-500G quote: PriceQuoteV1[sku=CURD-500G, unitPricePaise=4050, currency=INR]
order total: Rs 176.50
with a stub (Ordering's own tests): Rs 30
```

**What to notice:**

- Pricing applied a 10% promo internally; Ordering only saw the final unit price.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart is extracting delivery-slot calculation from its monolith. Before switching traffic, the new service runs in shadow mode against real requests.

**SlotsExtractionDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Realistic: extracting delivery-slot calculation from the FreshCart monolith. The facade shadows traffic to the
 * new service, compares answers, keeps serving the monolith's answer, and only cuts over when mismatches stay low.
 */
public class SlotsExtractionDemo {

    static final Function<String, String> MONOLITH = pin -> pin.startsWith("560") ? "07:00-09:00" : "18:00-20:00";

    /** The new service has one bug: it doesn't know 5601xx PIN codes are served in the morning too. */
    static final Function<String, String> SLOTS_SERVICE_V1 = pin -> pin.startsWith("5600") ? "07:00-09:00" : "18:00-20:00";
    static final Function<String, String> SLOTS_SERVICE_V2 = pin -> pin.startsWith("560") ? "07:00-09:00" : "18:00-20:00";

    static final class ShadowingFacade {
        private Function<String, String> candidate; private boolean cutOver;
        int compared, mismatched; final List<String> mismatches = new ArrayList<>();
        ShadowingFacade(Function<String, String> candidate) { this.candidate = candidate; }
        void deploy(Function<String, String> next) { candidate = next; compared = 0; mismatched = 0; mismatches.clear(); }

        String slotFor(String pin) {
            if (cutOver) return candidate.apply(pin);
            String served = MONOLITH.apply(pin);
            String shadow = candidate.apply(pin);
            compared++;
            if (!served.equals(shadow)) { mismatched++; mismatches.add(pin + " monolith=" + served + " new=" + shadow); }
            return served;
        }

        String tryCutOver(double maxMismatchRate, int minSamples) {
            double rate = compared == 0 ? 1 : (double) mismatched / compared;
            if (compared >= minSamples && rate <= maxMismatchRate) { cutOver = true; return "cut over (" + mismatched + "/" + compared + " mismatches)"; }
            return "stay on monolith (" + mismatched + "/" + compared + " mismatches)";
        }
    }

    public static void main(String[] args) {
        List<String> pins = List.of("560001", "560102", "411001", "560034", "560110", "110001");
        ShadowingFacade facade = new ShadowingFacade(SLOTS_SERVICE_V1);
        pins.forEach(facade::slotFor);
        System.out.println("v1: " + facade.tryCutOver(0.01, 5));
        facade.mismatches.forEach(m -> System.out.println("  " + m));
        facade.deploy(SLOTS_SERVICE_V2);
        pins.forEach(facade::slotFor);
        System.out.println("v2: " + facade.tryCutOver(0.01, 5));
        System.out.println("560102 now served by the new service: " + facade.slotFor("560102"));
    }
}
```

1. Every request also goes to the new service, but its answer is only compared.
2. Customers still get the monolith's answer while the new service is unproven.
3. Cut-over needs enough samples and a mismatch rate under the threshold.

**Run it:** `java SlotsExtractionDemo.java`

**Output:**

```text
v1: stay on monolith (2/6 mismatches)
  560102 monolith=07:00-09:00 new=18:00-20:00
  560110 monolith=07:00-09:00 new=18:00-20:00
v2: cut over (0/6 mismatches)
560102 now served by the new service: 07:00-09:00
```

**What to notice:**

- v1 had a PIN-code bug that customers never saw; the mismatch list pointed straight at it.
- In production, shadow calls run asynchronously and must never have side effects (no writes, no notifications).

### Example 3: Production-grade

**Scenario:** the gateway's home-screen endpoint composes Catalog, Cart and Offers found through discovery. It has a 150 ms budget. Catalog and Cart are required; Offers is nice to have.

**HomeScreenAggregatorDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Production-grade: the gateway's home-screen endpoint composes three services found through discovery. Calls run in
 * parallel under one deadline; required parts fail the request, optional parts degrade; every call carries the
 * request ID; per-dependency outcomes are recorded. Latency is simulated with a virtual clock so output is stable.
 */
public class HomeScreenAggregatorDemo {

    record Reply(long latencyMs, String body) {}
    record Downstream(String service, boolean required) {}
    interface Transport { Reply call(String address, String path, Map<String, String> headers); }

    static final class Registry {
        private final Map<String, List<String>> live = new TreeMap<>();
        void register(String service, String address) { live.computeIfAbsent(service, k -> new ArrayList<>()).add(address); }
        void deregister(String service, String address) { live.getOrDefault(service, new ArrayList<>()).remove(address); }
        List<String> lookup(String service) { return live.getOrDefault(service, List.of()); }
    }

    record HomeScreen(int status, Map<String, String> sections, List<String> degraded, long latencyMs) {}

    static final class HomeScreenAggregator {
        static final List<Downstream> PARTS = List.of(
                new Downstream("catalog", true), new Downstream("cart", true), new Downstream("offers", false));
        private final Registry registry; private final Transport transport; private final long deadlineMs;
        private final Map<String, Integer> rr = new TreeMap<>();
        final List<String> outcomes = new ArrayList<>();
        HomeScreenAggregator(Registry registry, Transport transport, long deadlineMs) {
            this.registry = registry; this.transport = transport; this.deadlineMs = deadlineMs;
        }

        HomeScreen render(String requestId, String customerId) {
            Map<String, String> headers = Map.of("X-Request-Id", requestId, "X-Customer", customerId);
            Map<String, String> sections = new LinkedHashMap<>();
            List<String> degraded = new ArrayList<>();
            long slowest = 0; boolean failed = false;
            for (Downstream d : PARTS) {                                    // conceptually parallel: total = slowest call
                List<String> instances = registry.lookup(d.service());
                String outcome; Reply reply = null;
                if (instances.isEmpty()) outcome = "no-instance";
                else {
                    int i = rr.merge(d.service(), 1, Integer::sum) - 1;
                    reply = transport.call(instances.get(i % instances.size()), "/" + d.service() + "/home", headers);
                    slowest = Math.max(slowest, Math.min(reply.latencyMs(), deadlineMs));
                    outcome = reply.latencyMs() > deadlineMs ? "timeout" : reply.body().startsWith("5") ? "error" : "ok";
                }
                outcomes.add(requestId + " " + d.service() + "=" + outcome);
                if (outcome.equals("ok")) sections.put(d.service(), reply.body());
                else if (d.required()) failed = true;
                else degraded.add(d.service());
            }
            if (failed) return new HomeScreen(503, Map.of(), List.of(), slowest);
            return new HomeScreen(200, sections, degraded, slowest);
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();
        registry.register("catalog", "10.0.1.1"); registry.register("catalog", "10.0.1.2");
        registry.register("cart", "10.0.2.1"); registry.register("offers", "10.0.3.1");
        Map<String, Long> latency = new TreeMap<>(Map.of("10.0.1.1", 40L, "10.0.1.2", 45L, "10.0.2.1", 30L, "10.0.3.1", 60L));
        Transport transport = (address, path, h) -> new Reply(latency.get(address), path + " for " + h.get("X-Customer") + " via " + address);
        HomeScreenAggregator gw = new HomeScreenAggregator(registry, transport, 150);

        print(gw.render("req-1", "C-1"));
        latency.put("10.0.3.1", 400L);                  // offers is struggling
        print(gw.render("req-2", "C-1"));
        registry.deregister("cart", "10.0.2.1");        // the only cart instance went away
        print(gw.render("req-3", "C-1"));
        gw.outcomes.forEach(o -> System.out.println("  outcome " + o));
    }

    static void print(HomeScreen s) {
        System.out.println(s.status() + " in " + s.latencyMs() + " ms, degraded=" + s.degraded());
        s.sections().forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }
}
```

1. Each dependency is marked required or optional up front.
2. Every downstream call carries the request ID and customer for tracing.
3. Discovery returning no instances is an outcome, not an exception.
4. Each call is classified: ok, timeout (past the deadline) or error.
5. Optional failures degrade the response; required failures return 503.

**Run it:** `java HomeScreenAggregatorDemo.java`

**Output:**

```text
200 in 60 ms, degraded=[]
  catalog: /catalog/home for C-1 via 10.0.1.1
  cart: /cart/home for C-1 via 10.0.2.1
  offers: /offers/home for C-1 via 10.0.3.1
200 in 150 ms, degraded=[offers]
  catalog: /catalog/home for C-1 via 10.0.1.2
  cart: /cart/home for C-1 via 10.0.2.1
503 in 150 ms, degraded=[]
  outcome req-1 catalog=ok
  outcome req-1 cart=ok
  outcome req-1 offers=ok
  outcome req-2 catalog=ok
  outcome req-2 cart=ok
  outcome req-2 offers=timeout
  outcome req-3 catalog=ok
  outcome req-3 cart=no-instance
  outcome req-3 offers=timeout
```

**What to notice:**

- When Offers slowed to 400 ms, the response still took only 150 ms and the home screen rendered without offers.
- Catalog alternated between instances (round-robin).
- The outcome log per request and dependency is exactly what a dashboard needs.
- In real code the calls run concurrently (`CompletableFuture.allOf` with `orTimeout`, or structured concurrency); the virtual clock keeps this demo deterministic.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Capability decomposition | Local changes, team autonomy | Needs domain understanding up front |
| Database per service | Independent schemas, isolated load | No cross-service transactions or joins |
| API gateway | One client entry point, central policies | Extra hop, potential bottleneck |
| Service discovery | Dynamic, self-healing routing | Another critical component; caching issues |
| Strangler fig | Incremental, reversible migration | Two systems run in parallel for a long time |
| Sidecar / mesh | Uniform cross-cutting concerns | Resource overhead, operational complexity |

## 8. When to Use / When NOT to Use

**Use microservices when:**

- several teams need to deploy independently;
- parts of the system have very different scaling or availability needs;
- you're replacing a legacy system incrementally.

**Don't when:**

- one or two teams own the whole product (a modular monolith is simpler);
- the domain isn't understood yet and boundaries would be guesses;
- you lack automated deployment, monitoring and on-call maturity.

## 9. Best Practices

1. **Decompose by business capability aligned with bounded contexts.** *(Consensus.)*
2. **Each service owns its data; enforce with permissions.** *(Consensus.)*
3. **Start with a modular monolith and extract when there's a reason.** *(Consensus, Fowler's "MonolithFirst".)*
4. **Keep the gateway free of business logic.** *(Consensus.)*
5. **Prefer asynchronous events between services; keep synchronous call chains short.** *(Consensus.)*
6. **Propagate request IDs and trace context everywhere.** *(Consensus.)*
7. **Shadow and compare before cutting over during migrations.** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Cascading failure through a synchronous chain (production failure mode)**  
> **Symptom:** during a sale, an Offers slowdown made the whole home screen time out; app ratings dropped.
> **Root cause:** the gateway waited for every dependency with no deadline and treated Offers as required; threads piled up.
> **Fix:** one deadline per request, required vs optional dependencies, degraded responses (Example 3), plus timeouts and circuit breakers (next page).

> 🚨 **Distributed monolith**  
> **Symptom:** five services must be released together; one schema change needs four teams.
> **Root cause:** entity decomposition and a shared database.
> **Fix:** redraw boundaries around capabilities; split schemas; communicate via contracts and events.

> 🚨 **Shared database "just for reads"**  
> **Symptom:** a reporting service broke after Customer renamed a column.
> **Root cause:** direct reads of another service's tables.
> **Fix:** events into a reporting store, or a published read API.

> 🚨 **Business logic in the gateway**  
> **Symptom:** discount rules in gateway filters; every pricing change needs the platform team.
> **Root cause:** convenience; the gateway already "sees" every request.
> **Fix:** move rules to the owning service; keep the gateway to cross-cutting concerns.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `spring.cloud.gateway.httpclient.response-timeout` | none | Gateway upstream timeout | Always set | Too low drops slow but valid calls |
| `networkaddress.cache.ttl` | 30 s (no security manager) | JVM DNS cache | Dynamic DNS discovery | Lower = more DNS load |
| `eureka.instance.lease-expiration-duration-in-seconds` | 90 | Registry TTL | Faster removal of dead instances | False evictions on GC pauses |
| Kubernetes `readinessProbe` | none | Traffic only when ready | Every service | Wrong probe blocks traffic |
| Istio `VirtualService` retries | 2 attempts | Mesh retries | Coordinate with app retries | Retry storms |

## 12. Security Considerations

- Validate tokens at the gateway, **and** authorise in each service (zero trust); never trust a header just because it came from inside.
- Strip client-supplied internal headers (`X-Client`, `X-User-Id`) at the edge so clients can't spoof them.
- Use mTLS between services; a mesh rotates certificates automatically.
- Separate database credentials per service with least privilege.
- Rate limit per client and per endpoint at the edge.

## 13. Performance & Scalability

Each network hop adds latency (often 1–5 ms in-cluster, more with TLS and serialisation) and a failure point. Aggregate calls in parallel and set a deadline; the request is as slow as its slowest required dependency. Scale services independently based on their own metrics. Sidecars add a little latency and memory per pod (Envoy typically tens of MB). Avoid chatty APIs: prefer coarse-grained calls and local replicas of hot reference data.

## 14. Testing It

Test the aggregator's degradation rules and propagation with a scripted transport:

**HomeScreenAggregatorTest.java**

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HomeScreenAggregatorTest {

    HomeScreenAggregatorDemo.Registry registry;
    Map<String, HomeScreenAggregatorDemo.Reply> replies;
    List<Map<String, String>> seenHeaders;
    HomeScreenAggregatorDemo.HomeScreenAggregator gw;

    @BeforeEach
    void setUp() {
        registry = new HomeScreenAggregatorDemo.Registry();
        registry.register("catalog", "c1");
        registry.register("cart", "k1");
        registry.register("offers", "o1");
        replies = new HashMap<>(Map.of(
                "c1", new HomeScreenAggregatorDemo.Reply(20, "catalog"),
                "k1", new HomeScreenAggregatorDemo.Reply(30, "cart"),
                "o1", new HomeScreenAggregatorDemo.Reply(50, "offers")));
        seenHeaders = new ArrayList<>();
        gw = new HomeScreenAggregatorDemo.HomeScreenAggregator(registry, (address, path, headers) -> {
            seenHeaders.add(headers);
            return replies.get(address);
        }, 100);
    }

    @Test
    void allHealthyReturnsEverySectionAndSlowestLatency() {
        var screen = gw.render("r1", "C-9");
        assertEquals(200, screen.status());
        assertEquals(List.of("catalog", "cart", "offers"), List.copyOf(screen.sections().keySet()));
        assertEquals(50, screen.latencyMs());
    }

    @Test
    void everyDownstreamCallCarriesTheRequestId() {
        gw.render("r-42", "C-9");
        assertEquals(3, seenHeaders.size());
        seenHeaders.forEach(h -> assertEquals("r-42", h.get("X-Request-Id")));
    }

    @Test
    void slowOptionalDependencyDegradesAndIsCappedByDeadline() {
        replies.put("o1", new HomeScreenAggregatorDemo.Reply(900, "offers"));
        var screen = gw.render("r2", "C-9");
        assertEquals(200, screen.status());
        assertEquals(List.of("offers"), screen.degraded());
        assertEquals(100, screen.latencyMs());
    }

    @Test
    void erroringRequiredDependencyFailsTheRequest() {
        replies.put("k1", new HomeScreenAggregatorDemo.Reply(10, "503 overloaded"));
        assertEquals(503, gw.render("r3", "C-9").status());
        assertTrue(gw.outcomes.contains("r3 cart=error"));
    }

    @Test
    void missingRequiredInstanceFailsWithoutCallingIt() {
        registry.deregister("catalog", "c1");
        assertEquals(503, gw.render("r4", "C-9").status());
        assertTrue(gw.outcomes.contains("r4 catalog=no-instance"));
        assertEquals(2, seenHeaders.size());
    }

    @Test
    void roundRobinsAcrossInstances() {
        registry.register("catalog", "c2");
        replies.put("c2", new HomeScreenAggregatorDemo.Reply(20, "catalog-2"));
        assertEquals("catalog", gw.render("a", "C").sections().get("catalog"));
        assertEquals("catalog-2", gw.render("b", "C").sections().get("catalog"));
    }
}
```

Across services, add consumer-driven contract tests (Pact, Spring Cloud Contract) and a small number of end-to-end smoke tests.

## 15. Observability & Operations

Distributed tracing (OpenTelemetry) is mandatory: one trace per request across gateway and services. Track RED metrics (rate, errors, duration) per service and per dependency, plus degraded-response counts. Keep a service catalog (Backstage) with owners, dependencies and runbooks. Correlate logs by request ID.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2011–2014 | "Microservices" term coined; Lewis and Fowler's article (2014) |
| 2012–2015 | Netflix OSS: Eureka, Zuul, Hystrix, Ribbon |
| 2004 / 2019 | Fowler's Strangler Fig Application (2004), renamed 2019 |
| 2017 | Istio and Linkerd 2 popularise service meshes |
| 2018–2020 | Spring Cloud Gateway and LoadBalancer replace Zuul/Ribbon |
| 2023 | Spring Modulith GA; Kubernetes native sidecars (1.28 alpha, 1.29 beta) |

## 17. Expert Deep Dive

- **Conway's Law in reverse.** Design team structure to get the architecture you want; boundaries that cross teams decay.
- **Synchronous coupling budget.** Availability multiplies: five required services at 99.9% each give about 99.5%. Fewer synchronous dependencies per request matter more than faster ones.
- **Ambient mesh.** Istio ambient mode replaces per-pod sidecars with node-level proxies to cut overhead.
- **Cell-based architecture.** Whole stacks deployed per cell (region, customer shard) limit blast radius.
- **Modular monolith as the default.** Many teams have moved back from microservices; extract only where independent deployment or scaling pays.

> 📖 **War story: the home screen that waited for offers**  
> During a FreshCart festival sale, the Offers service slowed to two seconds under load. The gateway's home endpoint called Catalog, Cart and Offers sequentially without deadlines; gateway threads were exhausted in minutes and the app showed a blank screen to everyone, including customers who never looked at offers. **Fixes:** parallel calls with a 150 ms deadline, Offers marked optional with an empty-section fallback, per-dependency outcome metrics, and a bulkhead per downstream. During the next sale Offers degraded three times and nobody outside the team noticed. **Lesson:** decide up front which dependencies a response can live without.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's a microservice?** An **independently deployable service** owning one capability and its data.
2. **What does an API gateway do?** **Single entry point**: routing, auth, rate limits.
3. **Why database per service?** So services **change and scale independently**.
4. **What's service discovery?** Finding **live instances** of a service dynamically.
5. **What's the strangler fig?** **Incrementally replacing** a legacy system behind a facade.

**▸ 🟡 L3**

1. **Entity vs capability decomposition?** Entities cause **chatty call chains**; capabilities keep use cases local.
2. **How does a service get another service's data?** **API call, or a local copy via events**.
3. **Client-side vs server-side discovery?** **Caller load-balances** vs **platform/LB does**.
4. **What's a sidecar?** A **helper container** alongside the app for cross-cutting concerns.
5. **What's a BFF?** A **gateway per client type** shaped for that client.

**▸ 🔴 L4–L5**

1. **When would you not use microservices?** **Small team, unclear domain, immature ops**; use a modular monolith.
2. **How do you prevent a distributed monolith?** **Capability boundaries, own data, async integration, contracts.**
3. **How do you migrate a monolith safely?** **Strangler fig, shadow traffic, incremental cut-over, delete legacy.**
4. **How do you design a composed endpoint for resilience?** **Parallel calls, one deadline, required vs optional, degraded responses.**
5. **App retries vs mesh retries?** **One owner per concern**; avoid multiplying retries.

**▸ 💻 Hands-on**

**Task:** Change `SlotsExtractionDemo` to cut over gradually: route a percentage of PIN codes to the new service based on a stable hash.

> ✅ **Solution**  
> ```java
> int rolloutPercent = 10;
> String slotFor(String pin) {
>     boolean inRollout = Math.floorMod(pin.hashCode(), 100) < rolloutPercent;
>     return inRollout ? candidate.apply(pin) : MONOLITH.apply(pin);
> }
> // a stable hash keeps each customer on one side, so they don't see answers flip between requests
> ```

## 19. Practice

- [ ] List FreshCart's capabilities and draw candidate service boundaries.
    > 💡 **Hint**  
    > Start from the bounded contexts on the DDD Strategic page.

- [ ] Put Spring Cloud Gateway in front of two Spring Boot apps with a path route and a rate limiter.
    > 💡 **Hint**  
    > `RequestRateLimiter` filter with Redis.

- [ ] Add a TTL-based eviction test to `ServiceDiscoveryDemo`.
    > 💡 **Hint**  
    > Heartbeat at t=0, look up at t=ttl and t=ttl+1.

- [ ] Add an `error` case for Offers in `HomeScreenAggregatorDemo` and check it degrades.
    > 💡 **Hint**  
    > Return a body starting with `5`.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Cut by capability/subdomain, not by entity.
> - Each service owns its data; others use APIs or event-fed copies.
> - Gateway: routing, auth, limits, request IDs; no business logic.
> - Discovery: heartbeats/health checks, TTL, load balancing.
> - Strangler fig: facade, shadow, cut over, delete legacy.
> - Sidecar/mesh: mTLS, retries, metrics; one owner per concern.
>
> ```java
> if (outcome.equals("ok")) sections.put(d.service(), reply.body());
> else if (d.required()) failed = true;
> else degraded.add(d.service());
> ```

## 21. Further Reading

- Sam Newman, *Building Microservices* (2nd ed., 2021) and *Monolith to Microservices* (2019).
- Chris Richardson, *Microservices Patterns* (2018) and [microservices.io](https://microservices.io/).
- Martin Fowler, [StranglerFigApplication](https://martinfowler.com/bliki/StranglerFigApplication.html) and [MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html).
- [Spring Cloud Gateway reference](https://docs.spring.io/spring-cloud-gateway/reference/).

---

[← Previous: Domain-Driven Design: Strategic Patterns](ddd-strategic.md) · [Next: Resilience Patterns →](resilience-patterns.md)
