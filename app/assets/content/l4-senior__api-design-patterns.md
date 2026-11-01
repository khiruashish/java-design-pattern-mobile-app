# API Design Patterns

L4 🟠 Senior / Lead · ⏱ 30 min read · Prerequisites: [Builder](../l1-foundations/builder.md), [Microservice Patterns](microservice-patterns.md)

> 📌 **TL;DR**  
> An API is a promise to code you don't control, so design it to evolve without breaking callers. **Fluent interfaces** (and staged builders) make Java APIs read well and make misuse fail to compile. **Versioning** lets incompatible changes coexist; deprecate with dates and measure usage before retiring. **Cursor (keyset) pagination** stays correct and fast as data changes; offset pagination doesn't. **Idempotency keys** make retried POSTs safe: store the result per key and replay it. **Backward compatibility rules**: add optional fields freely; never remove, rename, retype or newly require fields; treat new enum values as potentially breaking; clients read tolerantly. Consistent errors (Problem Details) round it off.

## 1. What It Is

**Plain English.** FreshCart's API is used by its own apps (some installed years ago and never updated), partner systems and internal services. These patterns make the API pleasant to call, safe to retry, efficient to page through, and possible to change without breaking any of those callers.

**Definition.**

| Pattern | Problem it solves |
|---------|-------------------|
| Fluent interface | Readable call chains; invalid combinations fail to compile |
| Versioning | Incompatible changes without breaking existing clients |
| Pagination | Large result sets in stable, efficient chunks |
| Idempotency keys | Safe retries for non-idempotent operations |
| Backward compatibility rules | Evolving contracts without new versions |

## 2. Why It Exists

Clients upgrade slowly: mobile apps stay installed for years, partners deploy quarterly, internal teams have their own priorities. Every breaking change forces all of them to change at once or break. Networks fail mid-request, so clients retry, and without idempotency a retried payment is a double charge. Lists grow from tens to millions of rows, and page 5,000 of an offset query scans everything before it. These patterns are the accumulated practice of public API providers (Stripe, Google, GitHub) and of Java library design (Bloch's *Effective Java*).

## 3. Mental Model / Analogy

**Analogy.** A restaurant menu. Fluent: you order in a natural sequence (starter, main, dessert) and the waiter won't take dessert before a main. Versioning: a new menu is printed while regulars can still order from the old one until a stated date. Pagination: "show me the next ten dishes after the one I'm looking at", not "skip the first 40 dishes" while the kitchen adds specials. Idempotency key: your order number; if you ask twice, you get the same order, not two meals. Compatibility: you can add dishes to the menu, but you can't quietly change what "Thali" means.

**Technical model.** A contract (types, fields, status codes, semantics) with rules for how it may change, plus protocol-level mechanisms (headers, cursors, keys) that make interactions safe under failures and growth.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Fluent interfaces

**Description.** Methods return an object (often `this`) so calls chain into readable sentences: `slots().in("BLR").on(date).withAtLeast(5)`. A **staged (step) builder** returns a different interface at each step, so required steps can't be skipped or reordered and the compiler enforces the API's grammar.

**Advantages / features.** Readable, discoverable through IDE completion; required parameters enforced at compile time; optional parameters without telescoping constructors.

**FluentInterfaceDemo.java**

```java
/**
 * Fluent interface: methods return an object to continue the chain, so code reads like a sentence. A staged
 * (step) builder makes the compiler enforce the required order: you can't build a query without a city.
 */
public class FluentInterfaceDemo {

    record SlotQuery(String city, String date, int minFreeSlots, boolean expressOnly) {}

    interface NeedsCity { NeedsDate in(String city); }
    interface NeedsDate { Options on(String date); }
    interface Options { Options withAtLeast(int freeSlots); Options expressOnly(); SlotQuery build(); }

    static NeedsCity slots() {
        return city -> date -> new Options() {
            int min = 1; boolean express;
            public Options withAtLeast(int n) { min = n; return this; }
            public Options expressOnly() { express = true; return this; }
            public SlotQuery build() { return new SlotQuery(city, date, min, express); }
        };
    }

    public static void main(String[] args) {
        SlotQuery q = slots().in("BLR").on("2026-11-02").withAtLeast(5).expressOnly().build();
        System.out.println(q);
        System.out.println(slots().in("PUN").on("2026-11-03").build());
        // slots().on("2026-11-02").build();   does not compile: NeedsCity has no on(), so the city can't be forgotten
    }
}
```

1. Each step is its own interface exposing only the next valid methods.
2. Lambdas implement the required steps; optional settings return the same object.
3. The call reads like a sentence and can only be written in a valid order.

**Run it:** `java FluentInterfaceDemo.java`

```text
SlotQuery[city=BLR, date=2026-11-02, minFreeSlots=5, expressOnly=true]
SlotQuery[city=PUN, date=2026-11-03, minFreeSlots=1, expressOnly=false]
```

| Style | Example | Notes |
|-------|---------|-------|
| Mutable builder returning `this` | `StringBuilder`, Lombok `@Builder` | Simple; required fields checked at runtime |
| Staged builder | jOOQ DSL, this demo | Compile-time order; more interfaces |
| Immutable "wither" chain | `java.time` (`withDayOfMonth`) | Each call returns a new value |
| Query DSL | Criteria API, AssertJ | Domain-specific vocabulary |

**Use** for builders, query DSLs and configuration. **Avoid** fluent setters on entities (`order.setX().setY()` breaks JavaBeans tools and hides invariants).
**Real-world Java:** `HttpClient.newBuilder()`, `Stream`, AssertJ `assertThat(...).isEqualTo(...)`, jOOQ, Spring Security `HttpSecurity` lambdas.
**Best practice:** end chains with a terminal method (`build()`, `execute()`) and validate there. **Gotcha:** long chains make stack traces and debugging harder; keep each step simple.

### Versioning

**Description.** When a change can't be made compatibly, publish a new version and serve both. Common schemes: URI (`/v2/orders`), header (`Accept: application/vnd.freshcart.v2+json`, `API-Version: 2026-11-01`) or date-based versions. Keep one domain model and render each version from it. Announce retirement with `Deprecation` and `Sunset` headers and track usage per version.

**Advantages / features.** Old clients keep working; new clients get the better contract; retirement becomes a measured, planned event.

**VersioningDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * API versioning: when a breaking change is unavoidable, serve the old and new contract side by side from one
 * domain model, route by version, tell old clients when the old version will be retired, and measure who still
 * uses it.
 */
public class VersioningDemo {

    record Product(String sku, String name, long pricePaise, String unit) {}                   // one domain model

    static final Map<String, Function<Product, String>> REPRESENTATIONS = new TreeMap<>();
    static {
        REPRESENTATIONS.put("v1", p -> "{\"sku\":\"" + p.sku() + "\",\"name\":\"" + p.name() + "\",\"price\":" + p.pricePaise() / 100.0 + "}");
        REPRESENTATIONS.put("v2", p -> "{\"sku\":\"" + p.sku() + "\",\"name\":\"" + p.name()
                + "\",\"price\":{\"amountPaise\":" + p.pricePaise() + ",\"currency\":\"INR\"},\"unit\":\"" + p.unit() + "\"}");
    }
    static final Map<String, Integer> usage = new TreeMap<>();

    static String get(String path, Product p) {
        String version = path.split("/")[1];
        usage.merge(version, 1, Integer::sum);
        String body = REPRESENTATIONS.get(version).apply(p);
        String deprecation = version.equals("v1") ? "  [Deprecation: true, Sunset: Sat, 01 May 2027 00:00:00 GMT]" : "";
        return version + " " + body + deprecation;
    }

    public static void main(String[] args) {
        Product milk = new Product("MILK-1L", "Toned Milk", 6850, "1 L");
        for (String path : List.of("/v1/products/MILK-1L", "/v2/products/MILK-1L", "/v1/products/MILK-1L"))
            System.out.println(get(path, milk));
        System.out.println("usage by version: " + usage);
    }
}
```

1. One representation function per version, all rendering the same domain model.
2. The version comes from the URI here; headers work the same way.
3. Old versions carry deprecation and sunset information, and usage is counted.

**Run it:** `java VersioningDemo.java`

```text
v1 {"sku":"MILK-1L","name":"Toned Milk","price":68.5}  [Deprecation: true, Sunset: Sat, 01 May 2027 00:00:00 GMT]
v2 {"sku":"MILK-1L","name":"Toned Milk","price":{"amountPaise":6850,"currency":"INR"},"unit":"1 L"}
v1 {"sku":"MILK-1L","name":"Toned Milk","price":68.5}  [Deprecation: true, Sunset: Sat, 01 May 2027 00:00:00 GMT]
usage by version: {v1=2, v2=1}
```

| Scheme | Pros | Cons |
|--------|------|------|
| URI `/v2/` | Visible, cacheable, easy routing | Versions whole API surface |
| Header / media type | Clean URIs, per-resource | Harder to test in a browser; caches need `Vary` |
| Date-based (Stripe-style) | Fine-grained, pinned per client | Needs version transformation layers |
| No versioning, only compatible changes | Simplest for clients | Requires strict discipline |

**Use** versions for genuinely breaking changes, and as rarely as possible. **Avoid** versioning every release, and avoid forking the domain model per version.
**Real-world Java:** Spring `@RequestMapping("/v2/...")`, Spring Framework 7 API versioning support (`ApiVersionConfigurer`), springdoc OpenAPI groups per version.
**Best practice:** support at least two versions in parallel and publish a deprecation policy. **Gotcha:** mobile apps in the field may call v1 for years; measure before you sunset.

### Pagination

**Description.** Return large collections in pages. **Offset pagination** (`?offset=40&limit=20`) is simple but slow for deep pages and inconsistent when rows are inserted or deleted between requests. **Cursor (keyset) pagination** returns an opaque cursor encoding the last item's sort key, and the next request asks for items after it: stable under inserts and fast with an index.

**Advantages / features.** Bounded response sizes; no duplicates or gaps while data changes; constant-time page fetches with a matching index.

**PaginationDemo.java**

```java
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;

/**
 * Pagination: offset/limit is simple but skips or repeats items when data changes between pages, and gets slower
 * for deep pages. Cursor (keyset) pagination continues "after the last item seen" and stays correct and fast.
 */
public class PaginationDemo {

    static final TreeMap<Integer, String> orders = new TreeMap<>();   // order number -> description, newest has highest number

    static List<String> offsetPage(int offset, int limit) {
        return orders.descendingMap().values().stream().skip(offset).limit(limit).toList();
    }

    record Page(List<String> items, String nextCursor) {}
    static Page cursorPage(String cursor, int limit) {
        int before = cursor == null ? Integer.MAX_VALUE
                : Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        List<String> items = new ArrayList<>();
        int last = -1;
        for (var e : orders.headMap(before, false).descendingMap().entrySet()) {
            if (items.size() == limit) break;
            items.add(e.getValue()); last = e.getKey();
        }
        String next = items.size() < limit ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(last).getBytes(StandardCharsets.UTF_8));
        return new Page(items, next);
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 7; i++) orders.put(i, "FC-" + i);
        List<String> p1 = offsetPage(0, 3);
        Page c1 = cursorPage(null, 3);
        orders.put(8, "FC-8");                       // a new order arrives while the customer is scrolling
        System.out.println("offset:  page1=" + p1 + " page2=" + offsetPage(3, 3) + "  (FC-5 shown twice)");
        System.out.println("cursor:  page1=" + c1.items() + " page2=" + cursorPage(c1.nextCursor(), 3).items() + "  next cursor=" + c1.nextCursor());
    }
}
```

1. Offset pagination counts from the start every time.
2. Cursor pagination continues after the last key seen.
3. The cursor is opaque to clients (encoded), so the server can change its contents later.

**Run it:** `java PaginationDemo.java`

```text
offset:  page1=[FC-7, FC-6, FC-5] page2=[FC-5, FC-4, FC-3]  (FC-5 shown twice)
cursor:  page1=[FC-7, FC-6, FC-5] page2=[FC-4, FC-3, FC-2]  next cursor=NQ
```

| | Offset | Cursor / keyset |
|-|--------|-----------------|
| Jump to page N | Yes | No (next/previous only) |
| Inserts during paging | Duplicates or gaps | Stable |
| Deep page cost | Scans skipped rows | Index seek |
| Total count | Easy (but expensive) | Usually omitted |
| SQL | `OFFSET ? LIMIT ?` | `WHERE (placed_at, id) < (?, ?) ORDER BY ... LIMIT ?` |

**Use** cursors for feeds, histories and large or changing lists; offsets for small, stable admin tables. **Avoid** exposing raw database IDs or SQL in cursors.
**Real-world Java:** Spring Data `Window`/`ScrollPosition` (keyset scrolling), jOOQ `seek()`, GraphQL Relay connections.
**Best practice:** cap `limit` server-side and make the sort order total (add a unique tie-breaker). **Gotcha:** sorting by a non-unique column alone (timestamp) skips or repeats rows that share a value.

### Idempotency keys

**Description.** For non-idempotent operations (create order, charge card), the client generates a unique key per logical operation and sends it in an `Idempotency-Key` header. The server stores the request fingerprint and response under that key; a retry with the same key returns the stored response instead of performing the operation again.

**Advantages / features.** Clients can retry safely after timeouts; no double orders or charges; simple client logic.

**IdempotencyKeyDemo.java**

```java
import java.util.HashMap;
import java.util.Map;

/**
 * Idempotency keys: the client sends a unique key with a non-idempotent request (POST). The server stores the
 * result under that key; a retry with the same key gets the stored result instead of repeating the action.
 */
public class IdempotencyKeyDemo {

    record StoredResponse(int requestHash, int status, String body) {}

    static final Map<String, StoredResponse> idempotencyStore = new HashMap<>();
    static int ordersCreated;

    static String postOrder(String idempotencyKey, String body) {
        StoredResponse previous = idempotencyStore.get(idempotencyKey);
        if (previous != null) {
            if (previous.requestHash() != body.hashCode())
                return "422 key reused with a different request";
            return previous.status() + " " + previous.body() + " (replayed)";
        }
        ordersCreated++;
        StoredResponse created = new StoredResponse(body.hashCode(), 201, "order FC-" + (1000 + ordersCreated));
        idempotencyStore.put(idempotencyKey, created);
        return created.status() + " " + created.body();
    }

    public static void main(String[] args) {
        String key = "7f3c9a2e-checkout-C1";
        System.out.println(postOrder(key, "{cart: C-1, total: 13600}"));
        System.out.println(postOrder(key, "{cart: C-1, total: 13600}"));      // app retried after a network timeout
        System.out.println(postOrder(key, "{cart: C-1, total: 99900}"));      // client bug: same key, different body
        System.out.println(postOrder("a91b-checkout-C1", "{cart: C-1, total: 4500}"));
        System.out.println("orders created: " + ordersCreated);
    }
}
```

1. A key reused with a different request is a client bug; reject it.
2. Same key, same request: replay the stored response.
3. The response is stored under the key after the first execution.

**Run it:** `java IdempotencyKeyDemo.java`

```text
201 order FC-1001
201 order FC-1001 (replayed)
422 key reused with a different request
201 order FC-1002
orders created: 2
```

| Detail | Recommendation |
|--------|----------------|
| Key format | Client-generated UUID per logical operation |
| Scope | Per client/account + key |
| Stored | Request fingerprint, status, response body, headers |
| Concurrent duplicate | 409 with `Retry-After` while in progress |
| Server errors | Release the key so the client can retry |
| Retention | Longer than any client retry window (often 24 h+) |

**Use** on every POST that creates or charges. **Avoid** generating the key on the server or per HTTP attempt (it must survive retries).
**Real-world Java:** a filter or interceptor with a database table keyed by `(client_id, idempotency_key)` and a unique constraint; Stripe's API popularised the header (IETF draft `Idempotency-Key`).
**Best practice:** store the key and the business effect in the same transaction. **Gotcha:** keys that expire before the client stops retrying (an app offline for two days) let duplicates through.

### Backward compatibility rules

**Description.** Rules that decide whether a contract change can ship without a new version. Safe: adding endpoints, optional request fields, response fields. Breaking: removing or renaming fields, changing types or meaning, making optional fields required, tightening validation, changing defaults. Potentially breaking: new enum values (strict clients fail), new error codes. Clients follow the **tolerant reader** rule: ignore unknown fields, handle unknown enum values.

**Advantages / features.** Most changes ship without versions; checks can be automated in CI against the published schema.

**BackwardCompatibilityDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Backward compatibility rules: compare an old and a proposed response schema and classify each change. Adding
 * optional fields is safe; removing, renaming, retyping or making fields required breaks existing clients.
 */
public class BackwardCompatibilityDemo {

    record Field(String type, boolean required) {}

    static List<String> breakingChanges(Map<String, Field> before, Map<String, Field> after, Map<String, List<String>> enumsBefore, Map<String, List<String>> enumsAfter) {
        List<String> problems = new ArrayList<>();
        before.forEach((name, f) -> {
            Field g = after.get(name);
            if (g == null) problems.add("removed '" + name + "'");
            else if (!g.type().equals(f.type())) problems.add("changed type of '" + name + "' " + f.type() + " -> " + g.type());
        });
        after.forEach((name, g) -> {
            if (!before.containsKey(name) && g.required()) problems.add("new required field '" + name + "' (old clients never send it)");
        });
        enumsBefore.forEach((name, values) -> {
            List<String> added = enumsAfter.getOrDefault(name, List.of()).stream().filter(v -> !values.contains(v)).toList();
            if (!added.isEmpty()) problems.add("new enum values " + added + " in '" + name + "' (breaks strict clients)");
        });
        return problems;
    }

    public static void main(String[] args) {
        Map<String, Field> v1 = new TreeMap<>(Map.of("orderId", new Field("string", true), "total", new Field("number", true),
                "status", new Field("string", true), "eta", new Field("string", false)));
        Map<String, Field> safe = new TreeMap<>(v1);
        safe.put("riderName", new Field("string", false));
        Map<String, Field> risky = new TreeMap<>(v1);
        risky.remove("eta");
        risky.put("total", new Field("object", true));
        risky.put("slotId", new Field("string", true));
        Map<String, List<String>> statuses = Map.of("status", List.of("PLACED", "PACKED", "DELIVERED"));
        Map<String, List<String>> statusesPlus = Map.of("status", List.of("PLACED", "PACKED", "OUT_FOR_DELIVERY", "DELIVERED"));

        System.out.println("add optional riderName: " + breakingChanges(v1, safe, statuses, statuses));
        System.out.println("proposed v1.1:");
        breakingChanges(v1, risky, statuses, statusesPlus).forEach(p -> System.out.println("  BREAKING " + p));
    }
}
```

1. Removing a field or changing its type breaks clients that read it.
2. A newly required field breaks clients that don't send it.
3. New enum values break clients that map statuses with a strict `switch` or enum.

**Run it:** `java BackwardCompatibilityDemo.java`

```text
add optional riderName: []
proposed v1.1:
  BREAKING removed 'eta'
  BREAKING changed type of 'total' number -> object
  BREAKING new required field 'slotId' (old clients never send it)
  BREAKING new enum values [OUT_FOR_DELIVERY] in 'status' (breaks strict clients)
```

| Change | Compatible? |
|--------|-------------|
| Add optional response field | Yes (tolerant readers) |
| Add optional request field with a default | Yes |
| Add enum value | Only if clients handle unknowns |
| Rename field | No: add new, deprecate old, remove later (expand and contract) |
| Change type (number to object) | No |
| Tighten validation | No |
| Change default behaviour | No, even if the schema is unchanged |

**Use** these rules as a CI gate on your OpenAPI or schema files. **Avoid** "it's only a small rename".
**Real-world Java:** openapi-diff or oasdiff in CI, Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` and `@JsonEnumDefaultValue` for tolerant reading, Protobuf field-number rules.
**Best practice:** expand and contract: add the new field, migrate clients, then remove the old one. **Gotcha:** Hyrum's Law: with enough users, every observable behaviour (field order, timing, error text) is depended on by somebody.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's API returned errors in four different shapes. Clients now get one standard shape.

**ProblemDetailsDemo.java**

```java
/** Minimal: every error from FreshCart's API has the same shape (RFC 9457 Problem Details), so clients handle all errors one way. */
public class ProblemDetailsDemo {

    record Problem(String type, String title, int status, String detail, String instance) {
        String toJson() {
            return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"status\":" + status
                    + ",\"detail\":\"" + detail + "\",\"instance\":\"" + instance + "\"}";
        }
    }

    static Problem slotFull(String slot, String path) {
        return new Problem("https://api.freshcart.example/problems/slot-full", "Delivery slot is full", 409,
                "Slot " + slot + " has no capacity left; choose another slot", path);
    }

    public static void main(String[] args) {
        System.out.println("409 Content-Type: application/problem+json");
        System.out.println(slotFull("07:00-09:00", "/v2/orders").toJson());
    }
}
```

1. RFC 9457 fields: a type URI, a short title, the status, a human-readable detail and the request path.
2. The type identifies the problem precisely; clients branch on it, not on the message text.

**Run it:** `java ProblemDetailsDemo.java`

**Output:**

```text
409 Content-Type: application/problem+json
{"type":"https://api.freshcart.example/problems/slot-full","title":"Delivery slot is full","status":409,"detail":"Slot 07:00-09:00 has no capacity left; choose another slot","instance":"/v2/orders"}
```

**What to notice:**

- Spring Boot 3+ can produce this shape for you (`spring.mvc.problemdetails.enabled=true`, `ProblemDetail`, `ErrorResponseException`).

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the "My orders" screen pages through a customer's orders, newest first, filtered by status. Several orders often share the same timestamp, and the app must not be able to forge cursors.

**OrderHistoryApiDemo.java**

```java
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Realistic: FreshCart's GET /v2/customers/{id}/orders. Orders sort by (placedAt desc, id desc) because many orders
 * share a timestamp; the cursor encodes the last (placedAt, id) and the filter, and is signed so clients can't
 * forge or edit it.
 */
public class OrderHistoryApiDemo {

    record Order(String id, long placedAt, String status) {}
    record Page(List<String> items, String nextCursor) {}

    static final byte[] KEY = "demo-only-secret-rotate-me".getBytes(StandardCharsets.UTF_8);
    static final Comparator<Order> NEWEST_FIRST = Comparator.comparingLong(Order::placedAt).reversed().thenComparing(Order::id, Comparator.reverseOrder());

    static Page list(List<Order> all, String statusFilter, String cursor, int limit) {
        long afterTime = Long.MAX_VALUE; String afterId = "~"; String filter = statusFilter;
        if (cursor != null) {
            String[] parts = verify(cursor).split("\\|");
            afterTime = Long.parseLong(parts[0]); afterId = parts[1]; filter = parts[2];
        }
        final long t = afterTime; final String id = afterId; final String f = filter;
        List<Order> page = all.stream()
                .filter(o -> f.equals("ALL") || o.status().equals(f))
                .filter(o -> o.placedAt() < t || (o.placedAt() == t && o.id().compareTo(id) < 0))
                .sorted(NEWEST_FIRST).limit(limit + 1L).toList();
        boolean more = page.size() > limit;
        List<Order> shown = more ? page.subList(0, limit) : page;
        Order last = shown.isEmpty() ? null : shown.get(shown.size() - 1);
        String next = more ? sign(last.placedAt() + "|" + last.id() + "|" + f) : null;
        List<String> items = new ArrayList<>();
        shown.forEach(o -> items.add(o.id()));
        return new Page(items, next);
    }

    static String sign(String payload) {
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + hmac(body);
    }
    static String verify(String cursor) {
        String[] parts = cursor.split("\\.");
        if (parts.length != 2 || !hmac(parts[0]).equals(parts[1])) throw new IllegalArgumentException("400 invalid cursor");
        return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    }
    static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static void main(String[] args) {
        List<Order> orders = List.of(new Order("FC-101", 1000, "DELIVERED"), new Order("FC-102", 2000, "DELIVERED"),
                new Order("FC-103", 2000, "CANCELLED"), new Order("FC-104", 2000, "DELIVERED"),
                new Order("FC-105", 3000, "DELIVERED"), new Order("FC-106", 4000, "PLACED"));
        Page p1 = list(orders, "DELIVERED", null, 2);
        Page p2 = list(orders, "ignored: the cursor carries the filter", p1.nextCursor(), 2);
        System.out.println("page 1: " + p1.items() + " next=" + p1.nextCursor());
        System.out.println("page 2: " + p2.items() + " next=" + (p2.nextCursor() != null));
        String tampered = p1.nextCursor().replace(p1.nextCursor().substring(0, 4), "MXxG");
        try { list(orders, "ALL", tampered, 2); } catch (IllegalArgumentException e) { System.out.println("tampered cursor: " + e.getMessage()); }
    }
}
```

1. The cursor carries the last position and the filter, so later pages can't drift to a different query.
2. Keyset condition on (placedAt, id): ties on time are broken by ID.
3. Cursors are signed with an HMAC; edited or forged cursors are rejected.

**Run it:** `java OrderHistoryApiDemo.java`

**Output:**

```text
page 1: [FC-105, FC-104] next=MjAwMHxGQy0xMDR8REVMSVZFUkVE.-ZFFUSzk0CH_wZ4I
page 2: [FC-102, FC-101] next=false
tampered cursor: 400 invalid cursor
```

**What to notice:**

- FC-104 and FC-102 share a timestamp, and paging split between them without skipping or repeating either.
- The second request's filter parameter was ignored; the cursor's filter won.
- The secret is hardcoded only for the demo; in production it comes from a secrets manager and is rotated.

### Example 3: Production-grade

**Scenario:** the checkout endpoint must be safe under retries from flaky mobile networks, concurrent double-taps, partial outages and two API versions.

**CheckoutApiDemo.java**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Production-grade: FreshCart's POST /{version}/orders. Requires an Idempotency-Key; replays stored responses;
 * rejects a reused key with a different body; returns 409 while the first request is still running; lets clients
 * retry after server errors; expires keys after 24 hours; renders v1 and v2 from one domain result; all errors are
 * Problem Details.
 */
public class CheckoutApiDemo {

    record Response(int status, Map<String, String> headers, String body) {
        @Override public String toString() { return status + " " + headers + " " + body; }
    }
    record Placed(String orderId, long totalPaise) {}
    record IdempotencyRecord(String fingerprint, boolean inProgress, Response response, long createdAt) {}

    static final class CheckoutApi {
        static final long KEY_TTL_MS = 24 * 60 * 60 * 1000L;
        final Map<String, IdempotencyRecord> keys = new HashMap<>();
        private final Function<String, Placed> placeOrder;
        long now;
        Runnable whileProcessing = () -> {};
        CheckoutApi(Function<String, Placed> placeOrder) { this.placeOrder = placeOrder; }

        Response post(String version, Map<String, String> headers, String body) {
            String key = headers.get("Idempotency-Key");
            if (key == null) return problem(400, "idempotency-key-missing", "Send an Idempotency-Key header with every POST /orders");
            String fingerprint = version + " POST /orders " + body;
            IdempotencyRecord existing = keys.get(key);
            if (existing != null && now - existing.createdAt() > KEY_TTL_MS) { keys.remove(key); existing = null; }
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) return problem(422, "idempotency-key-reused", "This key was used for a different request");
                if (existing.inProgress()) return withHeader(problem(409, "request-in-progress", "The original request is still being processed"), "Retry-After", "1");
                return withHeader(existing.response(), "Idempotent-Replayed", "true");
            }
            keys.put(key, new IdempotencyRecord(fingerprint, true, null, now));
            whileProcessing.run();
            Response response;
            try {
                response = render(version, placeOrder.apply(body));
            } catch (IllegalArgumentException e) {
                response = problem(422, "order-invalid", e.getMessage());
            } catch (RuntimeException e) {
                keys.remove(key);
                return problem(503, "temporarily-unavailable", "Please retry with the same Idempotency-Key");
            }
            keys.put(key, new IdempotencyRecord(fingerprint, false, response, now));
            return response;
        }

        static Response render(String version, Placed p) {
            String total = version.equals("v1") ? String.format("%d.%02d", p.totalPaise() / 100, p.totalPaise() % 100)
                    : "{\"amountPaise\":" + p.totalPaise() + ",\"currency\":\"INR\"}";
            return new Response(201, new TreeMap<>(Map.of("Location", "/" + version + "/orders/" + p.orderId())),
                    "{\"orderId\":\"" + p.orderId() + "\",\"total\":" + total + "}");
        }
        static Response problem(int status, String type, String detail) {
            return new Response(status, new TreeMap<>(Map.of("Content-Type", "application/problem+json")),
                    "{\"type\":\"/problems/" + type + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
        }
        static Response withHeader(Response r, String name, String value) {
            Map<String, String> h = new TreeMap<>(r.headers()); h.put(name, value);
            return new Response(r.status(), h, r.body());
        }
    }

    public static void main(String[] args) {
        int[] created = {0};
        boolean[] paymentsDown = {false};
        CheckoutApi api = new CheckoutApi(body -> {
            if (body.contains("qty=0")) throw new IllegalArgumentException("Quantity must be at least 1");
            if (paymentsDown[0]) throw new IllegalStateException("PayLink unavailable");
            created[0]++;
            return new Placed("FC-" + (2000 + created[0]), 13600);
        });
        Map<String, String> k1 = Map.of("Idempotency-Key", "k-1");
        api.whileProcessing = () -> System.out.println("  concurrent duplicate: " + api.post("v2", k1, "MILK-1L qty=2"));
        System.out.println("first:      " + api.post("v2", k1, "MILK-1L qty=2"));
        api.whileProcessing = () -> {};
        System.out.println("retry:      " + api.post("v2", k1, "MILK-1L qty=2"));
        System.out.println("other body: " + api.post("v2", k1, "MILK-1L qty=3"));
        System.out.println("no key:     " + api.post("v2", Map.of(), "MILK-1L qty=2"));
        System.out.println("invalid:    " + api.post("v2", Map.of("Idempotency-Key", "k-2"), "MILK-1L qty=0"));
        paymentsDown[0] = true;
        System.out.println("5xx:        " + api.post("v1", Map.of("Idempotency-Key", "k-3"), "CURD-500G qty=1"));
        paymentsDown[0] = false;
        System.out.println("retry 5xx:  " + api.post("v1", Map.of("Idempotency-Key", "k-3"), "CURD-500G qty=1"));
        api.now += CheckoutApi.KEY_TTL_MS + 1;
        System.out.println("after 24 h: " + api.post("v2", k1, "MILK-1L qty=2"));
        System.out.println("orders created: " + created[0]);
    }
}
```

1. Missing keys are rejected with a Problem Details error, so every client is forced to be retry-safe.
2. A duplicate that arrives while the first request is running gets 409 and `Retry-After`.
3. A completed key replays the stored response, marked with `Idempotent-Replayed`.
4. Server-side failures release the key so the client can retry with the same key.
5. Responses are rendered per version from one domain result.

**Run it:** `java CheckoutApiDemo.java`

**Output:**

```text
  concurrent duplicate: 409 {Content-Type=application/problem+json, Retry-After=1} {"type":"/problems/request-in-progress","status":409,"detail":"The original request is still being processed"}
first:      201 {Location=/v2/orders/FC-2001} {"orderId":"FC-2001","total":{"amountPaise":13600,"currency":"INR"}}
retry:      201 {Idempotent-Replayed=true, Location=/v2/orders/FC-2001} {"orderId":"FC-2001","total":{"amountPaise":13600,"currency":"INR"}}
other body: 422 {Content-Type=application/problem+json} {"type":"/problems/idempotency-key-reused","status":422,"detail":"This key was used for a different request"}
no key:     400 {Content-Type=application/problem+json} {"type":"/problems/idempotency-key-missing","status":400,"detail":"Send an Idempotency-Key header with every POST /orders"}
invalid:    422 {Content-Type=application/problem+json} {"type":"/problems/order-invalid","status":422,"detail":"Quantity must be at least 1"}
5xx:        503 {Content-Type=application/problem+json} {"type":"/problems/temporarily-unavailable","status":503,"detail":"Please retry with the same Idempotency-Key"}
retry 5xx:  201 {Location=/v1/orders/FC-2002} {"orderId":"FC-2002","total":136.00}
after 24 h: 201 {Location=/v2/orders/FC-2003} {"orderId":"FC-2003","total":{"amountPaise":13600,"currency":"INR"}}
orders created: 3
```

**What to notice:**

- Validation errors (422) are stored and replayed like successes; only server errors release the key.
- The v1 retry got rupees as a decimal and the v2 calls got a money object, from the same `Placed` result.
- After 24 hours the key had expired and the same request created a third order: retention must outlast every client's retry behaviour.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Fluent interface | Readable, compile-time safety (staged) | More types; harder debugging |
| Versioning | Breaking changes without breaking clients | Parallel maintenance; version sprawl |
| Cursor pagination | Stable, fast | No random page access; opaque cursors |
| Idempotency keys | Safe retries | Storage, retention, extra checks |
| Compatibility rules | Fewer versions | Discipline; tolerant clients needed |

## 8. When to Use / When NOT to Use

**Use them when:**

- clients are outside your release cycle (mobile apps, partners, other teams);
- operations have side effects and networks are unreliable;
- collections can grow without bound.

**Don't overdo them when:**

- the API is internal to one deployable and changes together with its callers;
- a list is small and bounded (a full response is fine);
- an operation is naturally idempotent (PUT of a full resource, DELETE).

## 9. Best Practices

1. **Design contract-first (OpenAPI) and check compatibility in CI.** *(Consensus.)*
2. **Add, don't change: expand and contract for renames.** *(Consensus.)*
3. **Clients read tolerantly: ignore unknown fields, handle unknown enum values.** *(Consensus.)*
4. **Idempotency keys on all creating POSTs.** *(Consensus.)*
5. **Cursor pagination with a total sort order and a server-side limit cap.** *(Consensus.)*
6. **One error format (Problem Details) across the API.** *(Consensus.)*
7. **Publish deprecation policy; send `Deprecation`/`Sunset` headers; measure usage.** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Double orders from app retries (production failure mode)**  
> **Symptom:** customers on slow networks received two identical deliveries and two charges.
> **Root cause:** the app retried `POST /orders` after a timeout; the first request had succeeded.
> **Fix:** mandatory `Idempotency-Key` generated once per checkout attempt and stored with the order (Example 3).

> 🚨 **Renamed field breaks old apps**  
> **Symptom:** app versions older than three months showed blank prices.
> **Root cause:** `price` renamed to `unitPrice` in a "harmless cleanup".
> **Fix:** expand and contract; compatibility checks in CI; keep old fields until usage reaches zero.

> 🚨 **New enum value crashes clients**  
> **Symptom:** Android app crashed on the order screen after `OUT_FOR_DELIVERY` was added.
> **Root cause:** the app mapped statuses to an enum with `valueOf`, failing on unknown values.
> **Fix:** clients map unknown values to a default (`UNKNOWN`); servers add new values behind client capability checks.

> 🚨 **Deep offset pagination**  
> **Symptom:** the admin order export slowed to minutes per page after a year.
> **Root cause:** `OFFSET 400000` scanned and discarded rows every request.
> **Fix:** keyset pagination on an indexed (created_at, id).

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Max page size | — | Caps `limit` | Always set (e.g. 100) | Too low: chatty clients |
| Idempotency key retention | — | How long keys are remembered | Above client retry window | Storage growth |
| Jackson `FAIL_ON_UNKNOWN_PROPERTIES` | true | Strict reading | false for clients | Typos go unnoticed |
| Jackson `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` | false | Unknown enum handling | true with `@JsonEnumDefaultValue` | Silent defaults |
| Spring `spring.mvc.problemdetails.enabled` | false | RFC 9457 errors | true | Error shape change for clients |
| Spring Data `Pageable` max size | 2000 | Caps `size` param | Lower it | Clients requesting more get capped |

## 12. Security Considerations

- Cursors must not leak internal data or allow access to other users' data; sign or encrypt them and re-check authorisation per page.
- Idempotency keys are scoped per client or user; otherwise one client could replay another's response.
- Cap page sizes and request bodies to prevent resource exhaustion.
- Error details must not reveal stack traces, SQL or internal hostnames.
- Old API versions need security fixes too, or must be retired.

## 13. Performance & Scalability

Keyset pagination is O(log n) per page with the right composite index; offset is O(offset). Idempotency lookups add one indexed read per request; keep the table partitioned or TTL-cleaned. Version transformation layers add little CPU but grow complexity; prefer rendering from one model. Fluent builders allocate small objects; irrelevant except in hot loops.

## 14. Testing It

Test the API's behaviour under retries, concurrency, failures and versions:

**CheckoutApiTest.java**

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutApiTest {

    int created;
    boolean failing;
    CheckoutApiDemo.CheckoutApi api;

    @BeforeEach
    void setUp() {
        created = 0; failing = false;
        api = new CheckoutApiDemo.CheckoutApi(body -> {
            if (failing) throw new IllegalStateException("down");
            if (body.contains("qty=0")) throw new IllegalArgumentException("bad qty");
            created++;
            return new CheckoutApiDemo.Placed("FC-" + created, 4500);
        });
    }

    static Map<String, String> key(String k) { return Map.of("Idempotency-Key", k); }

    @Test
    void retryWithSameKeyReplaysWithoutCreatingAgain() {
        var first = api.post("v2", key("a"), "x");
        var second = api.post("v2", key("a"), "x");
        assertEquals(first.body(), second.body());
        assertEquals("true", second.headers().get("Idempotent-Replayed"));
        assertEquals(1, created);
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        api.post("v2", key("a"), "x");
        assertEquals(422, api.post("v2", key("a"), "y").status());
    }

    @Test
    void sameKeyDifferentVersionIsADifferentRequest() {
        api.post("v2", key("a"), "x");
        assertEquals(422, api.post("v1", key("a"), "x").status());
    }

    @Test
    void concurrentDuplicateGets409() {
        int[] status = {0};
        api.whileProcessing = () -> status[0] = api.post("v2", key("a"), "x").status();
        assertEquals(201, api.post("v2", key("a"), "x").status());
        assertEquals(409, status[0]);
        assertEquals(1, created);
    }

    @Test
    void validationFailuresAreStoredAndReplayed() {
        assertEquals(422, api.post("v2", key("a"), "qty=0").status());
        assertEquals("true", api.post("v2", key("a"), "qty=0").headers().get("Idempotent-Replayed"));
    }

    @Test
    void serverErrorsReleaseTheKeySoTheClientCanRetry() {
        failing = true;
        assertEquals(503, api.post("v2", key("a"), "x").status());
        failing = false;
        assertEquals(201, api.post("v2", key("a"), "x").status());
    }

    @Test
    void missingKeyIsAProblemDetailsError() {
        var r = api.post("v2", Map.of(), "x");
        assertEquals(400, r.status());
        assertEquals("application/problem+json", r.headers().get("Content-Type"));
    }

    @Test
    void versionsRenderTheSameOrderDifferently() {
        assertTrue(api.post("v1", key("a"), "x").body().contains("\"total\":45.00"));
        assertTrue(api.post("v2", key("b"), "x").body().contains("\"amountPaise\":4500"));
    }
}
```

Add contract tests (Spring Cloud Contract, Pact) per consumer and an OpenAPI diff check in CI.

## 15. Observability & Operations

Track requests per API version and per client app version (to plan sunsets), idempotent replays and 409s (signals of client retry behaviour), 422s on reused keys (client bugs), page sizes and deep-pagination usage, and error types by Problem Details `type`. Publish a changelog and deprecation schedule.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2005 | Fowler names "Fluent Interface" |
| 2008 | *Effective Java* 2nd ed.: Builder for many parameters |
| 2010s | Stripe popularises Idempotency-Key and date-based API versions |
| 2016 | RFC 7807 Problem Details; OpenAPI 3 work begins (3.0 in 2017) |
| 2019 | RFC 8594 `Sunset` header |
| 2023 | RFC 9457 replaces 7807; Spring Data 3.1 adds keyset scrolling |
| 2025 | RFC 9745 `Deprecation` header; Spring Framework 7 adds first-class API versioning |

## 17. Expert Deep Dive

- **Hyrum's Law.** Observable behaviour becomes contract. Randomise what you don't promise (map ordering, timing) to prevent accidental dependencies.
- **Version transformation chains.** Stripe-style: the core speaks the latest version; per-version transformers convert requests and responses step by step back to each client's pinned version.
- **Postel's law, revisited.** Liberal reading in clients is good; liberal acceptance in servers can lock you into quirks forever. Validate strictly on input, read tolerantly on output.
- **Consumer-driven contracts.** Let consumers publish what they use, and break only what nobody uses.
- **Library APIs.** For Java libraries, binary compatibility (method signatures, default methods, sealed hierarchies) matters as much as source compatibility; tools like japicmp and Revapi check it.

> 📖 **War story: two deliveries, two charges**  
> FreshCart's checkout used a plain `POST /orders`. When a popular sale coincided with a mobile network slowdown in Pune, the app's HTTP client timed out after 10 seconds and retried automatically. The first request had usually succeeded. About 1,100 customers were charged twice and 600 received duplicate deliveries before the app was patched. **Fixes:** a mandatory `Idempotency-Key` created once per checkout attempt and persisted on the device, 409 for in-flight duplicates, replay of stored responses, key retention of 7 days because some app versions retried on next launch, and a dashboard of replays per app version. **Lesson:** every client retries eventually, so every creating endpoint needs an answer for "what if this arrives twice?"

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's a fluent interface?** **Chained calls** that read like a sentence.
2. **Why version an API?** To make **breaking changes without breaking existing clients**.
3. **Why paginate?** To return **large collections in bounded chunks**.
4. **What's idempotent?** **Same effect whether done once or many times.**
5. **Is adding a response field breaking?** **No**, for tolerant clients.

**▸ 🟡 L3**

1. **Offset vs cursor pagination?** **Skip N** vs **continue after last key**; cursor is stable and fast.
2. **Which HTTP methods are idempotent?** **GET, PUT, DELETE** (and HEAD, OPTIONS); **POST isn't**.
3. **How do idempotency keys work?** **Store response per key; replay on retry.**
4. **What's a staged builder?** A builder with **an interface per step** to enforce order.
5. **Is a new enum value breaking?** **Potentially**, for strict clients.

**▸ 🔴 L4–L5**

1. **How do you rename a field safely?** **Expand and contract.**
2. **URI vs header versioning?** **Visibility and routing** vs **clean URIs and per-resource**; both fine if consistent.
3. **Concurrent requests with the same key?** **First wins; others get 409 while in progress.**
4. **How do you decide when to sunset v1?** **Usage metrics per client version, published policy, Sunset headers.**
5. **What's Hyrum's Law?** **All observable behaviour gets depended on.**

**▸ 💻 Hands-on**

**Task:** Add a server-side cap to `OrderHistoryApiDemo.list`: clamp `limit` to 1..50 and default to 20 when it's 0 or missing.

> ✅ **Solution**  
> ```java
> static int effectiveLimit(Integer requested) {
>     if (requested == null || requested == 0) return 20;
>     return Math.max(1, Math.min(50, requested));   // never let a client ask for 100,000 rows
> }
> ```

## 19. Practice

- [ ] Write a staged builder for a FreshCart delivery booking (address, then slot, then optional notes).
    > 💡 **Hint**  
    > One interface per required step; `build()` only on the last.

- [ ] Add `oasdiff breaking` to a CI pipeline for an OpenAPI file.
    > 💡 **Hint**  
    > Compare against the spec from the main branch.

- [ ] Implement keyset pagination with Spring Data `Window<T>` and `ScrollPosition.keyset()`.
    > 💡 **Hint**  
    > Sort by a unique, indexed combination.

- [ ] Make an Android/Java client map unknown enum values to `UNKNOWN` with Jackson.
    > 💡 **Hint**  
    > `@JsonEnumDefaultValue` plus the matching deserialization feature.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Fluent + staged builders: readable and impossible to misuse.
> - Version only for breaking changes; one model, many renderings; Deprecation/Sunset; measure usage.
> - Cursor pagination with a total order; cap the limit; opaque, signed cursors.
> - Idempotency-Key on creating POSTs: fingerprint, 409 in progress, replay, release on 5xx, long retention.
> - Compatible: add optional. Breaking: remove, rename, retype, require. Enums: careful.
>
> ```java
> if (existing.inProgress()) return problem(409, "request-in-progress", "...");
> return withHeader(existing.response(), "Idempotent-Replayed", "true");
> ```

## 21. Further Reading

- Joshua Bloch, *Effective Java* (3rd ed., 2018), item 2 (builders) and chapter 8 (methods).
- JJ Geewax, *API Design Patterns* (Manning, 2021).
- [Google API Improvement Proposals](https://google.aip.dev/), especially AIP-158 (pagination) and AIP-180 (backwards compatibility).
- RFC 9457 (Problem Details), RFC 8594 (Sunset), RFC 9745 (Deprecation), IETF draft "The Idempotency-Key HTTP Header Field".

---

[← Previous: Caching Patterns](caching-patterns.md) · [Next: L5 Staff / Principal →](../l5-staff-principal/index.md)
