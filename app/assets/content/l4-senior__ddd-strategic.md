# Domain-Driven Design: Strategic Patterns

L4 🟠 Senior / Lead · ⏱ 40 min read · Prerequisites: [Domain-Driven Design: Tactical Patterns](ddd-tactical.md), [Architectural Patterns](architectural-patterns.md)

> 📌 **TL;DR**  
> Strategic DDD is about **where one model ends and another begins**. A **ubiquitous language** makes code read like the business. A **bounded context** is the boundary inside which a model and its language are consistent; "product" can mean different things in Catalogue, Warehouse and Pricing, and that's fine. A **context map** documents how contexts relate (customer/supplier, conformist, partnership, shared kernel, open host service). An **anti-corruption layer** translates a foreign model so it can't distort yours. A **shared kernel** is a small model two contexts deliberately share; a **published language** is a documented, versioned format for integration. These decisions shape team boundaries, service boundaries and integration contracts, and they matter more than any tactical pattern.

## 1. What It Is

**Plain English.** In a grocery company, the warehouse, the app team and finance all talk about "products" and "orders", but they mean different things and care about different details. Strategic DDD accepts that: it gives each area its own model and language, draws clear boundaries between them, and designs how they exchange information.

**Definition.**

| Pattern | Purpose |
|---------|---------|
| Ubiquitous language | One shared vocabulary per context, used in conversation and code |
| Bounded context | Explicit boundary within which a model is consistent |
| Context map | Diagram and description of relationships between contexts |
| Anti-corruption layer (ACL) | Translation layer protecting a model from a foreign one |
| Shared kernel | Small, jointly owned model shared by two contexts |
| Published language | Well-documented exchange format for integration |
| Open host service | A protocol/API one context offers to many consumers |

## 2. Why It Exists

Large systems fail when one model tries to serve everyone: a 60-field `Product` class that every team edits, where each change breaks someone else, and nobody can agree what "available" means. Splitting by technical layers doesn't help. Evans' insight was that **models are only valid within a context**, and that the boundaries between contexts are a design decision with organisational consequences (Conway's Law). Getting them right enables independent teams and services; getting them wrong creates distributed monoliths.

## 3. Mental Model / Analogy

**Analogy.** Countries. Each has its own language and laws (bounded context, ubiquitous language). A map shows borders and treaties (context map). Interpreters translate at borders so foreign laws don't apply at home (anti-corruption layer). Some neighbours share a currency they must agree on before changing (shared kernel). International shipping uses standard documents everyone can read (published language).

**Technical model.** A bounded context is usually owned by one team, has its own code base or module and its own data, and integrates with others only through explicit contracts.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Ubiquitous language

**Description.** Within a bounded context, developers and domain experts use **one vocabulary**, and the code uses the same words: classes, methods and events named after business concepts (`Subscription.canSkip`, `SKIP_CUTOFF`), not technical stand-ins (`DataManager.process`, `flag2`). The language is refined in conversations and kept in a glossary.

**Advantages / features.** Experts can review rules by reading code; fewer translation errors; new developers learn the business from the code.

**Before (technical names) and after (domain language):**

**UbiquitousLanguageDemo.java**

```java
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/** Ubiquitous language: code uses exactly the words domain experts use, so rules can be read back to them. */
public class UbiquitousLanguageDemo {

    // ---- Before: technical names. What is flag2? What does process() do? ----
    static final class DataManager {
        boolean process(Map<String, Object> rec, LocalDate d, LocalTime t) {
            boolean flag2 = (Boolean) rec.get("f2");
            if (flag2) return false;
            return d.isAfter(LocalDate.of(2026, 11, 1)) || t.isBefore(LocalTime.of(22, 0));
        }
    }

    // ---- After: the same rule in DairyDay's language. ----
    static final class Subscription {
        private final boolean paused;
        Subscription(boolean paused) { this.paused = paused; }
        static final LocalTime SKIP_CUTOFF = LocalTime.of(22, 0);
        boolean canSkip(LocalDate deliveryDay, LocalDate today, LocalTime now) {
            if (paused) return false;
            boolean deliveryIsTomorrow = deliveryDay.equals(today.plusDays(1));
            return !deliveryIsTomorrow || now.isBefore(SKIP_CUTOFF);
        }
    }

    static final Map<String, String> GLOSSARY = Map.of(
            "subscription", "a standing order for daily milk deliveries",
            "skip", "cancel one day's delivery; allowed until the skip cutoff",
            "skip cutoff", "22:00 on the day before delivery",
            "pause", "stop deliveries for a date range, at most 30 days");

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 1);
        boolean before = new DataManager().process(Map.of("f2", false), today.plusDays(1), LocalTime.of(21, 30));
        boolean after = new Subscription(false).canSkip(today.plusDays(1), today, LocalTime.of(21, 30));
        System.out.println("same answer at 21:30: " + (before == after) + " (" + after + ")");
        System.out.println("at 22:15: " + new Subscription(false).canSkip(today.plusDays(1), today, LocalTime.of(22, 15)));
        Set<String> used = Set.of("subscription", "skip", "skip cutoff");
        System.out.println("terms used in code that are in the glossary: " + GLOSSARY.keySet().containsAll(used));
        System.out.println("skip cutoff means: " + GLOSSARY.get("skip cutoff"));
    }
}
```

1. Before: the rule is correct but unreadable to anyone outside the team.
2. After: the same rule in the words a DairyDay operations manager uses.
3. A glossary keeps terms precise; code and conversations both use it.

**Run it:** `java UbiquitousLanguageDemo.java`

```text
same answer at 21:30: true (true)
at 22:15: false
terms used in code that are in the glossary: true
skip cutoff means: 22:00 on the day before delivery
```

| Smell | Language fix |
|-------|--------------|
| `Manager`, `Processor`, `Data`, `Info` names | Name the business concept or action |
| Boolean flags (`f2`, `isSpecial`) | Named states (`Paused`, `VipCustomer`) |
| Developers translating in meetings | Adopt the experts' words in code |
| Same word, different meanings | Split into contexts or qualify the term |

**Use** the experts' terms even when they seem odd ("dahi" vs "curd" if that's what the business says). **Avoid** inventing technical synonyms; every synonym is a translation bug waiting to happen.
**Real-world Java:** event names (`OrderPlaced`), method names (`order.place()`), package names by subdomain.
**Best practice:** keep a short glossary in the repo, and update code when the language changes. **Gotcha:** a language is only "ubiquitous" inside its context; forcing one glossary across the whole company recreates the God model.

### Bounded contexts

**Description.** A bounded context is the explicit boundary within which a particular model applies. Inside it, every term has one meaning. The same real-world thing (a product) can be modelled differently in different contexts, linked by a shared identifier. Contexts typically align with subdomains and teams, and often (not always) with deployable services.

**Advantages / features.** Smaller, focused models; teams change their model without coordinating with everyone; clear ownership.

**BoundedContextsDemo.java**

```java
import java.util.Map;

/**
 * Bounded contexts: one word, several models. "Product" means different things to the catalogue, the warehouse and
 * pricing; each context keeps its own model and they share only the identifier.
 */
public class BoundedContextsDemo {

    record Sku(String code) {}

    // Catalogue context: what customers see.
    record CatalogProduct(Sku sku, String title, String description, String imageUrl) {}
    // Warehouse context: where it is and how to handle it.
    record StockItem(Sku sku, String binLocation, int grams, boolean chilled, int onHand) {}
    // Pricing context: what it costs right now.
    record PricedItem(Sku sku, long mrpPaise, long salePaise, int gstPercent) {}

    /** Before: one "Product" model trying to serve everyone. */
    record GodProduct(String sku, String title, String description, String imageUrl, String bin, int grams,
                      boolean chilled, int onHand, long mrp, long sale, int gst, String supplierCode, String hsn) {}

    public static void main(String[] args) {
        Sku paneer = new Sku("PANEER-200G");
        var catalog = Map.of(paneer, new CatalogProduct(paneer, "Fresh Paneer 200g", "Soft malai paneer", "/img/paneer.jpg"));
        var warehouse = Map.of(paneer, new StockItem(paneer, "C-04-2", 200, true, 37));
        var pricing = Map.of(paneer, new PricedItem(paneer, 9_500, 8_900, 5));

        System.out.println("app shows:    " + catalog.get(paneer).title() + " at Rs " + pricing.get(paneer).salePaise() / 100);
        System.out.println("picker sees:  bin " + warehouse.get(paneer).binLocation() + ", chilled=" + warehouse.get(paneer).chilled());
        System.out.println("fields per model: catalogue " + CatalogProduct.class.getRecordComponents().length
                + ", warehouse " + StockItem.class.getRecordComponents().length + ", pricing " + PricedItem.class.getRecordComponents().length
                + " vs one shared model " + GodProduct.class.getRecordComponents().length);
    }
}
```

1. The shared identifier is the only thing all contexts agree on.
2. Each context models only what it needs: the warehouse cares about bins and chilling, not descriptions.
3. Before: one model with every field, which every team must coordinate on.

**Run it:** `java BoundedContextsDemo.java`

```text
app shows:    Fresh Paneer 200g at Rs 89
picker sees:  bin C-04-2, chilled=true
fields per model: catalogue 4, warehouse 5, pricing 4 vs one shared model 13
```

| Finding boundaries | Signal |
|--------------------|--------|
| Language changes meaning | "Product", "order", "delivery" mean different things |
| Different experts | Warehouse staff vs marketing vs finance |
| Different change rates | Prices change hourly, descriptions monthly |
| Different consistency needs | Stock must be exact; search can lag |
| Event storming | Clusters of events and commands around pivotal events |

**Use** bounded contexts to split large domains and organisations. **Avoid** one context per entity (too fine) or one for the whole company (too coarse).
**Real-world Java:** modules in a modular monolith, separate Spring Boot services, separate schemas.
**Best practice:** one team per context; a context can have several services, but a service shouldn't span contexts. **Gotcha:** sharing database tables across contexts silently merges their models again.

### Context mapping

**Description.** A context map documents how bounded contexts relate, technically and organisationally. Common relationships: **Partnership** (teams succeed or fail together), **Shared Kernel**, **Customer/Supplier** (upstream plans for downstream needs), **Conformist** (downstream adopts upstream's model as is), **Anti-corruption layer**, **Open Host Service** with a **Published Language**, and **Separate Ways**.

**Advantages / features.** Makes dependencies and power dynamics visible; shows where change ripples; guides integration design.

**ContextMappingDemo.java**

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A context map as data: who depends on whom, and how. It answers "who is affected if this context changes?" */
public class ContextMappingDemo {

    enum Relationship {
        PARTNERSHIP, SHARED_KERNEL, CUSTOMER_SUPPLIER, CONFORMIST, ANTICORRUPTION_LAYER, OPEN_HOST_SERVICE
    }

    record Link(String upstream, String downstream, Relationship how) {}

    static final List<Link> FRESHCART = List.of(
            new Link("Catalogue", "Search", Relationship.OPEN_HOST_SERVICE),
            new Link("Catalogue", "Pricing", Relationship.CUSTOMER_SUPPLIER),
            new Link("Pricing", "Checkout", Relationship.CUSTOMER_SUPPLIER),
            new Link("Checkout", "Delivery", Relationship.CUSTOMER_SUPPLIER),
            new Link("ParcelGo API", "Delivery", Relationship.ANTICORRUPTION_LAYER),
            new Link("PayLink API", "Checkout", Relationship.CONFORMIST),
            new Link("Shared Money/Sku", "Checkout", Relationship.SHARED_KERNEL),
            new Link("Shared Money/Sku", "Pricing", Relationship.SHARED_KERNEL));

    static Set<String> impactOf(String changed) {
        Set<String> affected = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>(List.of(changed));
        while (!todo.isEmpty()) {
            String up = todo.pop();
            for (Link l : FRESHCART) {
                if (!l.upstream().equals(up) || !affected.add(l.downstream())) continue;
                if (l.how() != Relationship.ANTICORRUPTION_LAYER) todo.push(l.downstream());   // an ACL absorbs change
            }
        }
        return affected;
    }

    public static void main(String[] args) {
        FRESHCART.forEach(l -> System.out.printf("%-17s -> %-9s %s%n", l.upstream(), l.downstream(), l.how()));
        System.out.println("change in Catalogue affects: " + impactOf("Catalogue"));
        System.out.println("change in ParcelGo API affects: " + impactOf("ParcelGo API") + " (stops at the ACL)");
        System.out.println("change in PayLink API affects: " + impactOf("PayLink API") + " (conformist: change flows through)");
        List<String> conformists = new ArrayList<>();
        FRESHCART.stream().filter(l -> l.how() == Relationship.CONFORMIST).forEach(l -> conformists.add(l.downstream() + " conforms to " + l.upstream()));
        System.out.println("risks to review: " + conformists);
    }
}
```

1. The standard relationship types from the DDD community.
2. Each link names upstream, downstream and how they integrate.
3. Impact analysis: changes flow downstream, except where an anti-corruption layer absorbs them.

**Run it:** `java ContextMappingDemo.java`

```text
Catalogue         -> Search    OPEN_HOST_SERVICE
Catalogue         -> Pricing   CUSTOMER_SUPPLIER
Pricing           -> Checkout  CUSTOMER_SUPPLIER
Checkout          -> Delivery  CUSTOMER_SUPPLIER
ParcelGo API      -> Delivery  ANTICORRUPTION_LAYER
PayLink API       -> Checkout  CONFORMIST
Shared Money/Sku  -> Checkout  SHARED_KERNEL
Shared Money/Sku  -> Pricing   SHARED_KERNEL
change in Catalogue affects: [Search, Pricing, Checkout, Delivery]
change in ParcelGo API affects: [Delivery] (stops at the ACL)
change in PayLink API affects: [Checkout, Delivery] (conformist: change flows through)
risks to review: [Checkout conforms to PayLink API]
```

| Relationship | Downstream influence on upstream | Coupling |
|--------------|----------------------------------|----------|
| Partnership | High (joint planning) | High |
| Shared kernel | Shared ownership | High, small surface |
| Customer/Supplier | Negotiated | Medium |
| Conformist | None | High (adopts model) |
| Anti-corruption layer | None | Low (translated) |
| Open host + published language | Via versioned protocol | Low |
| Separate ways | n/a | None |

**Use** a context map early, and keep it current; it's the best overview of a large system. **Avoid** conformist relationships with volatile upstreams; put an ACL in between.
**Real-world Java:** Context Mapper DSL (CML) tooling, C4 container diagrams annotated with relationships.
**Best practice:** record the organisational reality (who can influence whom), not the ideal. **Gotcha:** "Customer/Supplier" fails if the supplier team has no incentive to serve the customer; escalate or switch to an ACL.

### Anti-corruption layer

**Description.** When integrating with a legacy system or third-party API whose model differs from yours, an anti-corruption layer **translates** between them: foreign codes, units, formats and concepts are mapped into your domain model at the boundary. Inside your context, nothing knows the foreign model exists.

**Advantages / features.** Your model stays clean; changes to the foreign system are contained in one place; unknown inputs can be quarantined.

**AntiCorruptionLayerDemo.java**

```java
import java.util.List;
import java.util.Map;

/**
 * An Anti-Corruption Layer translates a foreign model (here a legacy ERP's supplier feed) into our own, so the
 * foreign concepts and codes never leak into the domain.
 */
public class AntiCorruptionLayerDemo {

    // ---------- The legacy ERP's model: cryptic codes, weights in kg as strings, prices with tax mixed in ----------
    record ErpItemRow(String MATNR, String MAKTX, String MEINS, String BRGEW, String VERPR, String MWSKZ, String LVORM) {}

    // ---------- Our domain model ----------
    record Sku(String code) {}
    record Product(Sku sku, String name, int grams, long pricePaiseExTax, int gstPercent) {}

    // ---------- The ACL ----------
    static final class ErpProductTranslator {
        private static final Map<String, Integer> TAX_CODES = Map.of("V0", 0, "V1", 5, "V2", 12, "V3", 18);
        private static final Map<String, String> SKU_BY_MATERIAL = Map.of("000000000010045", "PANEER-200G", "000000000010046", "CURD-400G");

        List<Product> translate(List<ErpItemRow> rows) {
            return rows.stream()
                    .filter(r -> !"X".equals(r.LVORM()))                                                 // deletion flag
                    .filter(r -> SKU_BY_MATERIAL.containsKey(r.MATNR()))
                    .map(this::toProduct)
                    .toList();
        }
        private Product toProduct(ErpItemRow r) {
            int gst = TAX_CODES.getOrDefault(r.MWSKZ(), 18);
            long grossPaise = Math.round(Double.parseDouble(r.VERPR().replace(',', '.')) * 100);
            long exTax = grossPaise * 100 / (100 + gst);
            int grams = (int) Math.round(Double.parseDouble(r.BRGEW().replace(',', '.')) * ("KG".equals(r.MEINS()) ? 1000 : 1));
            return new Product(new Sku(SKU_BY_MATERIAL.get(r.MATNR())), r.MAKTX().strip(), grams, exTax, gst);
        }
    }

    public static void main(String[] args) {
        var feed = List.of(
                new ErpItemRow("000000000010045", "PANEER MALAI 200G   ", "KG", "0,200", "93,45", "V1", ""),
                new ErpItemRow("000000000010046", "DAHI 400G", "G", "400", "47,25", "V1", ""),
                new ErpItemRow("000000000010047", "OLD SKU", "KG", "1,0", "10,00", "V3", "X"));
        new ErpProductTranslator().translate(feed).forEach(System.out::println);
    }
}
```

1. The legacy ERP's model: German-abbreviated field names, decimal commas, tax-inclusive prices, deletion flags.
2. The translator is the only code that knows those details.
3. Business meaning is translated, not just field names: prices become tax-exclusive and weights become grams.

**Run it:** `java AntiCorruptionLayerDemo.java`

```text
Product[sku=Sku[code=PANEER-200G], name=PANEER MALAI 200G, grams=200, pricePaiseExTax=8900, gstPercent=5]
Product[sku=Sku[code=CURD-400G], name=DAHI 400G, grams=400, pricePaiseExTax=4500, gstPercent=5]
```

| ACL component | Role |
|---------------|------|
| Facade | Simplifies the foreign API's calls |
| Adapter | Implements our port using the foreign client |
| Translator | Converts foreign data to our model and back |
| Quarantine / dead letter | Holds inputs that can't be translated |

**Use** an ACL for legacy systems, third-party APIs and any upstream you can't influence. **Avoid** one for stable, well-designed upstream models you can conform to cheaply.
**Real-world Java:** adapters around SAP/ERP connectors, payment gateway SDK wrappers, Spring Integration transformers.
**Best practice:** make the translator a pure function with thorough tests from real payloads. **Gotcha:** ACLs that grow business logic become a hidden context; keep them about translation.

### Shared kernel and published language

**Description.** A **shared kernel** is a small subset of the model (e.g., `Money`, `Sku`) that two contexts share as code and change only by agreement. A **published language** is a documented exchange format (event schemas, API specs) that a context offers to many consumers, versioned and evolved compatibly. Consumers should be **tolerant readers**: read only what they need, ignore unknown fields, default optional ones.

**Advantages / features.** Shared kernel avoids duplicate value types between close partners; published language lets many consumers integrate without coupling to internals.

**SharedKernelPublishedLanguageDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared kernel: a small model two contexts agree to share and change together. Published language: a documented,
 * versioned exchange format that many consumers can read, with tolerant readers so producers can evolve.
 */
public class SharedKernelPublishedLanguageDemo {

    // ---------- Shared kernel: tiny, stable, jointly owned by Checkout and Pricing ----------
    record Money(long paise) {
        Money { if (paise < 0) throw new IllegalArgumentException("negative"); }
        Money plus(Money o) { return new Money(paise + o.paise); }
    }

    // ---------- Published language: OrderPlaced as a documented map-based message ----------
    static Map<String, Object> orderPlacedV2(String orderId, Money total, String pin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "OrderPlaced");
        m.put("version", 2);
        m.put("orderId", orderId);
        m.put("totalPaise", total.paise());
        m.put("deliveryPin", pin);
        m.put("channel", "app");                                 // added in v2
        return m;
    }

    /** A tolerant reader: takes only the fields it needs, ignores unknown ones, defaults optional ones. */
    record DeliveryView(String orderId, String pin, String channel) {
        static DeliveryView read(Map<String, Object> msg) {
            if (!"OrderPlaced".equals(msg.get("type"))) throw new IllegalArgumentException("not an OrderPlaced");
            return new DeliveryView((String) msg.get("orderId"), (String) msg.get("deliveryPin"),
                    (String) msg.getOrDefault("channel", "unknown"));
        }
    }

    public static void main(String[] args) {
        Money total = new Money(45_000).plus(new Money(6_800));
        Map<String, Object> v2 = orderPlacedV2("FC-88", total, "560034");
        Map<String, Object> v1 = new LinkedHashMap<>(v2);
        v1.put("version", 1);
        v1.remove("channel");
        Map<String, Object> v3 = new LinkedHashMap<>(v2);
        v3.put("version", 3);
        v3.put("giftWrap", true);

        System.out.println("published: " + v2);
        System.out.println("reads v1: " + DeliveryView.read(v1));
        System.out.println("reads v2: " + DeliveryView.read(v2));
        System.out.println("reads v3 (unknown field ignored): " + DeliveryView.read(v3));
    }
}
```

1. A shared kernel value object, jointly owned and deliberately tiny.
2. The published `OrderPlaced` message: type, version and documented fields.
3. A tolerant reader handles v1 (missing field), v2, and v3 (extra field) without changes.

**Run it:** `java SharedKernelPublishedLanguageDemo.java`

```text
published: {type=OrderPlaced, version=2, orderId=FC-88, totalPaise=51800, deliveryPin=560034, channel=app}
reads v1: DeliveryView[orderId=FC-88, pin=560034, channel=unknown]
reads v2: DeliveryView[orderId=FC-88, pin=560034, channel=app]
reads v3 (unknown field ignored): DeliveryView[orderId=FC-88, pin=560034, channel=app]
```

| Format | Typical use |
|--------|-------------|
| JSON Schema / OpenAPI | REST APIs and JSON events |
| Avro / Protobuf + schema registry | Kafka events with compatibility checks |
| AsyncAPI | Documenting event-driven interfaces |
| CloudEvents | Common event envelope (type, source, id, time) |

**Use** a shared kernel only between closely cooperating teams, for small, stable concepts. **Avoid** "shared libraries" that grow into a shared domain model; that couples every team.
**Real-world Java:** shared `money` modules, Confluent Schema Registry with Avro/Protobuf, Spring Cloud Contract.
**Best practice:** version every published message and run compatibility checks in CI. **Gotcha:** Jackson fails on unknown properties by default (`FAIL_ON_UNKNOWN_PROPERTIES`); configure tolerant reading for consumers.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** at QuickDrop, customers and riders both talk about "deliveries", and every argument about the delivery model is really about two different things.

**DeliveryTermsDemo.java**

```java
import java.util.List;

/** QuickDrop: "delivery" means one thing to customers and another to riders, so each context gets its own model. */
public class DeliveryTermsDemo {

    // Customer context: a delivery is "when my order arrives".
    record CustomerDelivery(String orderId, String window, String status) {}

    // Rider context: a delivery is "one stop on my trip", and a trip has many.
    record Stop(String orderId, String address, int sequence) {}
    record Trip(String riderId, List<Stop> stops) {
        int remaining(int completed) { return stops.size() - completed; }
    }

    public static void main(String[] args) {
        var trip = new Trip("R7", List.of(new Stop("QD-1", "12 MG Road", 1), new Stop("QD-2", "4 Brigade Rd", 2), new Stop("QD-3", "9 Church St", 3)));
        System.out.println("rider sees: " + trip.stops().size() + " stops, " + trip.remaining(1) + " remaining");
        System.out.println("customer sees: " + new CustomerDelivery("QD-2", "18:00-20:00", "next stop"));
    }
}
```

1. To a customer, a delivery is when their order arrives.
2. To a rider, a delivery is one stop on a trip; the trip is the unit of work.

**Run it:** `java DeliveryTermsDemo.java`

**Output:**

```text
rider sees: 3 stops, 2 remaining
customer sees: CustomerDelivery[orderId=QD-2, window=18:00-20:00, status=next stop]
```

**What to notice:**

- Two small models are easier to get right than one model that must satisfy both groups.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart's Delivery context receives ParcelGo webhooks with ParcelGo's status codes (`MAN`, `OFD`, `UDL`, `RTO`) and reason codes. Translate them into FreshCart's delivery lifecycle and customer messages, and quarantine anything unexpected.

**PartnerShipmentAclDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FreshCart's Delivery context integrates ParcelGo through an anti-corruption layer: ParcelGo's status codes,
 * timestamps and failure reasons are translated into FreshCart's own delivery lifecycle, and anything unexpected
 * is quarantined instead of leaking in.
 */
public class PartnerShipmentAclDemo {

    // ParcelGo's webhook payload (their language)
    record ParcelGoWebhook(String awb, String clientRef, String statusCode, long epochSeconds, String reasonCode) {}

    // FreshCart's Delivery context (our language)
    enum DeliveryStatus { ASSIGNED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED_ATTEMPT }
    record DeliveryUpdate(String orderId, DeliveryStatus status, String customerMessage) {}

    static final class ParcelGoAcl {
        private static final Map<String, DeliveryStatus> STATUS = Map.of(
                "MAN", DeliveryStatus.ASSIGNED, "PKD", DeliveryStatus.PICKED_UP, "OFD", DeliveryStatus.OUT_FOR_DELIVERY,
                "DLV", DeliveryStatus.DELIVERED, "UDL", DeliveryStatus.FAILED_ATTEMPT);
        private static final Map<String, String> REASONS = Map.of(
                "CNA", "We couldn't reach you; we'll try again tomorrow",
                "ADI", "The address seemed incomplete; please check it in the app");
        final List<ParcelGoWebhook> quarantine = new ArrayList<>();

        java.util.Optional<DeliveryUpdate> translate(ParcelGoWebhook w) {
            DeliveryStatus status = STATUS.get(w.statusCode());
            if (status == null || w.clientRef() == null || !w.clientRef().startsWith("FC-")) {
                quarantine.add(w);
                return java.util.Optional.empty();
            }
            String message = switch (status) {
                case FAILED_ATTEMPT -> REASONS.getOrDefault(w.reasonCode(), "Delivery attempt failed; we'll contact you");
                case DELIVERED -> "Delivered. Enjoy!";
                case OUT_FOR_DELIVERY -> "On the way";
                default -> "Being prepared";
            };
            return java.util.Optional.of(new DeliveryUpdate(w.clientRef(), status, message));
        }
    }

    public static void main(String[] args) {
        ParcelGoAcl acl = new ParcelGoAcl();
        List<ParcelGoWebhook> webhooks = List.of(
                new ParcelGoWebhook("PG123", "FC-71", "OFD", 1_793_800_000L, null),
                new ParcelGoWebhook("PG123", "FC-71", "UDL", 1_793_801_800L, "CNA"),
                new ParcelGoWebhook("PG124", "FC-72", "RTO", 1_793_802_000L, null),
                new ParcelGoWebhook("PG125", null, "DLV", 1_793_803_000L, null),
                new ParcelGoWebhook("PG123", "FC-71", "DLV", 1_793_890_000L, null));
        for (ParcelGoWebhook w : webhooks) acl.translate(w).ifPresent(u -> System.out.println(u));
        System.out.println("quarantined for review: " + acl.quarantine.stream().map(w -> w.awb() + "/" + w.statusCode()).toList());
    }
}
```

1. ParcelGo's codes map to FreshCart's statuses in one table.
2. Unknown statuses and payloads without a FreshCart order reference are quarantined, not guessed.
3. Customer messages are FreshCart's language, chosen per status and reason.

**Run it:** `java PartnerShipmentAclDemo.java`

**Output:**

```text
DeliveryUpdate[orderId=FC-71, status=OUT_FOR_DELIVERY, customerMessage=On the way]
DeliveryUpdate[orderId=FC-71, status=FAILED_ATTEMPT, customerMessage=We couldn't reach you; we'll try again tomorrow]
DeliveryUpdate[orderId=FC-71, status=DELIVERED, customerMessage=Delivered. Enjoy!]
quarantined for review: [PG124/RTO, PG125/DLV]
```

**What to notice:**

- `RTO` (return to origin) isn't modelled yet; quarantining it creates a review task instead of a wrong status.
- If FreshCart switches to another partner, only a new ACL is needed; the Delivery model doesn't change.

### Example 3: Production-grade

**Scenario:** DairyDay's Subscriptions context publishes `SubscriptionStarted` to Billing and Delivery, which are owned by different teams. Old v1 messages are still in the broker. The Subscriptions team wants to rename a field and must not break anyone. Billing also integrates PayLink's mandate API, whose states change without notice.

**ContextIntegrationDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DairyDay's contexts (Subscriptions, Billing, Delivery) integrate through a published language: versioned event
 * messages with documented fields. Old messages are upcast to the current version; each consumer declares the
 * fields it relies on, and the producer's CI checks every schema change against those consumer contracts.
 */
public class ContextIntegrationDemo {

    // ---------- Published language: schema per version ----------
    record Schema(String type, int version, Set<String> required, Set<String> optional) {}

    static final Map<Integer, Schema> SUBSCRIPTION_STARTED = Map.of(
            1, new Schema("SubscriptionStarted", 1, Set.of("subscriptionId", "customerId", "litres"), Set.of()),
            2, new Schema("SubscriptionStarted", 2, Set.of("subscriptionId", "customerId", "litresPerDay", "pricePerLitrePaise"), Set.of("deliverySlot")));

    /** Upcaster: old messages are converted to the current version before any consumer sees them. */
    static Map<String, Object> upcast(Map<String, Object> msg) {
        Map<String, Object> m = new LinkedHashMap<>(msg);
        if ((int) m.get("version") == 1) {
            m.put("litresPerDay", m.remove("litres"));
            m.put("pricePerLitrePaise", 6_800L);           // v1 was only sold at the launch price
            m.put("version", 2);
        }
        return m;
    }

    // ---------- Consumer-driven contracts ----------
    record ConsumerContract(String consumer, Set<String> fieldsUsed) {}

    static List<String> checkCompatibility(Schema proposed, List<ConsumerContract> contracts) {
        List<String> broken = new ArrayList<>();
        for (ConsumerContract c : contracts)
            for (String f : new TreeSet<>(c.fieldsUsed()))
                if (!proposed.required().contains(f) && !proposed.optional().contains(f))
                    broken.add(c.consumer() + " needs '" + f + "'");
        return broken;
    }

    // ---------- Consumers, each with its own model ----------
    record BillingAccount(String customerId, long monthlyPaise) {}
    record RouteStop(String subscriptionId, String slot) {}

    static BillingAccount billing(Map<String, Object> m) {
        return new BillingAccount((String) m.get("customerId"), ((Number) m.get("litresPerDay")).longValue() * ((Number) m.get("pricePerLitrePaise")).longValue() * 30);
    }
    static RouteStop delivery(Map<String, Object> m) {
        return new RouteStop((String) m.get("subscriptionId"), (String) m.getOrDefault("deliverySlot", "06:00-07:00"));
    }

    // ---------- Anti-corruption layer for the PayLink mandate API (Billing's upstream) ----------
    record PayLinkMandateResponse(String mandate_ref, String state, String umrn) {}
    enum MandateStatus { ACTIVE, PENDING, REJECTED }
    static MandateStatus toMandateStatus(PayLinkMandateResponse r) {
        return switch (r.state()) {
            case "ACTIVE", "active" -> MandateStatus.ACTIVE;
            case "INITIATED", "PENDING_BANK_APPROVAL" -> MandateStatus.PENDING;
            default -> MandateStatus.REJECTED;
        };
    }

    public static void main(String[] args) {
        List<ConsumerContract> contracts = List.of(
                new ConsumerContract("billing", Set.of("customerId", "litresPerDay", "pricePerLitrePaise")),
                new ConsumerContract("delivery", Set.of("subscriptionId", "deliverySlot")));

        Map<String, Object> oldMsg = new LinkedHashMap<>(Map.of("type", "SubscriptionStarted", "version", 1, "subscriptionId", "SUB-1", "customerId", "C-9", "litres", 1));
        Map<String, Object> newMsg = new LinkedHashMap<>(Map.of("type", "SubscriptionStarted", "version", 2, "subscriptionId", "SUB-2", "customerId", "C-17",
                "litresPerDay", 2, "pricePerLitrePaise", 6_500L, "deliverySlot", "07:00-08:00"));
        for (Map<String, Object> m : List.of(oldMsg, newMsg)) {
            Map<String, Object> current = upcast(m);
            System.out.println("v" + m.get("version") + " -> billing " + billing(current) + ", delivery " + delivery(current));
        }

        System.out.println("v2 schema vs contracts: " + checkCompatibility(SUBSCRIPTION_STARTED.get(2), contracts));
        Schema proposedV3 = new Schema("SubscriptionStarted", 3, Set.of("subscriptionId", "customerId", "litresPerDay", "unitPrice"), Set.of("deliverySlot"));
        System.out.println("proposed v3 (renames price) vs contracts: " + checkCompatibility(proposedV3, contracts));

        for (var r : List.of(new PayLinkMandateResponse("m1", "active", "U1"), new PayLinkMandateResponse("m2", "PENDING_BANK_APPROVAL", null),
                new PayLinkMandateResponse("m3", "EXPIRED_BY_NPCI", null)))
            System.out.println("PayLink " + r.state() + " -> " + toMandateStatus(r));
    }
}
```

1. The published language: a schema per version with required and optional fields.
2. Upcasting converts old messages to the current version in one place, so consumers handle only one shape.
3. Each consumer declares the fields it uses (a consumer-driven contract).
4. Consumers build their own models from the message: billing accounts, route stops.
5. An anti-corruption layer maps PayLink's states, treating unknown ones as rejected.

**Run it:** `java ContextIntegrationDemo.java`

**Output:**

```text
v1 -> billing BillingAccount[customerId=C-9, monthlyPaise=204000], delivery RouteStop[subscriptionId=SUB-1, slot=06:00-07:00]
v2 -> billing BillingAccount[customerId=C-17, monthlyPaise=390000], delivery RouteStop[subscriptionId=SUB-2, slot=07:00-08:00]
v2 schema vs contracts: []
proposed v3 (renames price) vs contracts: [billing needs 'pricePerLitrePaise']
PayLink active -> ACTIVE
PayLink PENDING_BANK_APPROVAL -> PENDING
PayLink EXPIRED_BY_NPCI -> REJECTED
```

**What to notice:**

- The v1 message was billed correctly after upcasting, and delivery defaulted the missing slot.
- The proposed v3 renamed `pricePerLitrePaise`; the contract check names exactly which consumer would break, before anything is deployed.
- In production, the same checks run with a schema registry (Avro/Protobuf compatibility rules) or Spring Cloud Contract / Pact.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Ubiquitous language | Shared understanding, readable code | Needs ongoing collaboration with experts |
| Bounded contexts | Focused models, team autonomy | Duplication of similar concepts; integration work |
| Context map | Visibility of dependencies and power | Must be kept current |
| Anti-corruption layer | Protected model, contained change | Extra translation code to maintain |
| Shared kernel | No duplication between partners | Coupled release coordination |
| Published language | Many consumers, safe evolution | Versioning discipline required |

## 8. When to Use / When NOT to Use

**Use them when:**

- several teams work on one domain or system;
- the business uses the same words with different meanings;
- integrating with legacy or third-party systems.

**Don't overdo them when:**

- one small team owns a simple domain; one context is fine;
- contexts would be split purely along technical lines;
- integration formats would be versioned for consumers that don't exist.

## 9. Best Practices

1. **Discover contexts with domain experts (event storming), not by drawing boxes alone.** *(Consensus.)*
2. **One team per bounded context.** *(Consensus.)*
3. **No shared database tables across contexts.** *(Consensus.)*
4. **ACLs for legacy and third-party upstreams.** *(Consensus.)*
5. **Version published languages; consumers read tolerantly.** *(Consensus.)*
6. **Keep shared kernels tiny and stable.** *(Consensus.)*
7. **Draw the context map as it is, then as you want it.** *(Opinion, widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Breaking a published message (production failure mode)**  
> **Symptom:** billing stopped creating invoices for new subscriptions; nobody noticed for four days.
> **Root cause:** the Subscriptions team renamed `pricePerLitrePaise` in their event; billing read `null` and skipped the message as invalid.
> **Fix:** consumer-driven contract checks in the producer's CI; additive changes only (add the new field, deprecate the old, remove after consumers migrate); alerts on skipped messages.

> 🚨 **One model to rule them all**  
> **Symptom:** a 60-field shared `Product` library; every release needs sign-off from six teams.
> **Root cause:** no bounded contexts; "reuse" of one model everywhere.
> **Fix:** split into context-specific models linked by SKU; keep only `Sku` and `Money` shared.

> 🚨 **Conformist to a volatile partner**  
> **Symptom:** every ParcelGo API change broke FreshCart's delivery screens.
> **Root cause:** ParcelGo's statuses were used directly in the domain and UI.
> **Fix:** an anti-corruption layer translating to FreshCart's own statuses.

> 🚨 **Contexts drawn around technology**  
> **Symptom:** "frontend context", "database context", "notification context"; every feature touches all of them.
> **Root cause:** boundaries by technical layer rather than business capability.
> **Fix:** redraw around business capabilities and language.

## 11. Configuration & Tuning

| Setting / tool | Default | What it does | When to change it | Risk of changing |
|----------------|---------|--------------|-------------------|------------------|
| Jackson `FAIL_ON_UNKNOWN_PROPERTIES` | true | Fails on unknown JSON fields | false for tolerant consumers | Typos go unnoticed |
| Schema registry compatibility | BACKWARD | Allowed schema changes | FULL for shared topics | Slower evolution |
| Spring Cloud Contract / Pact | — | Consumer-driven contracts | Cross-team APIs/events | Contract maintenance |
| Context Mapper (CML) | — | Context maps as code | Large landscapes | Tooling learning curve |
| CloudEvents envelope | — | Standard event metadata | Multi-team event platforms | Migration of existing events |

## 12. Security Considerations

- Context boundaries are natural trust boundaries: validate everything crossing them, especially from external upstreams.
- ACLs should sanitise and validate foreign data, not only translate it.
- Published languages should exclude personal data consumers don't need; classify fields.
- Shared kernels must not include security-sensitive logic one team could change for both.

## 13. Performance & Scalability

Bounded contexts allow different scaling strategies per context (search scaled for reads, checkout for writes). Translation layers cost little CPU; the main costs are extra network hops and data duplication. Event-carried data reduces cross-context calls at the price of storage and eventual consistency. Keep published messages compact and avoid chatty request/response integration across contexts.

## 14. Testing It

Test translations with real payloads, schemas against consumer contracts, and upcasters with old messages:

**ContextIntegrationTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContextIntegrationTest {

    static final List<ContextIntegrationDemo.ConsumerContract> CONTRACTS = List.of(
            new ContextIntegrationDemo.ConsumerContract("billing", Set.of("customerId", "litresPerDay", "pricePerLitrePaise")),
            new ContextIntegrationDemo.ConsumerContract("delivery", Set.of("subscriptionId", "deliverySlot")));

    @Test
    void currentSchemaSatisfiesEveryConsumer() {
        assertEquals(List.of(), ContextIntegrationDemo.checkCompatibility(ContextIntegrationDemo.SUBSCRIPTION_STARTED.get(2), CONTRACTS));
    }

    @Test
    void removingAFieldAConsumerUsesIsCaught() {
        var withoutSlot = new ContextIntegrationDemo.Schema("SubscriptionStarted", 3,
                Set.of("subscriptionId", "customerId", "litresPerDay", "pricePerLitrePaise"), Set.of());
        assertEquals(List.of("delivery needs 'deliverySlot'"), ContextIntegrationDemo.checkCompatibility(withoutSlot, CONTRACTS));
    }

    @Test
    void v1MessagesAreUpcastBeforeConsumersSeeThem() {
        Map<String, Object> v1 = new LinkedHashMap<>(Map.of("type", "SubscriptionStarted", "version", 1, "subscriptionId", "S", "customerId", "C", "litres", 3));
        var current = ContextIntegrationDemo.upcast(v1);
        assertEquals(2, current.get("version"));
        assertEquals(3, current.get("litresPerDay"));
        assertEquals(3 * 6_800L * 30, ContextIntegrationDemo.billing(current).monthlyPaise());
        assertEquals(1, v1.get("version"), "the original message is not modified");
    }

    @Test
    void unknownPayLinkStatesAreTreatedAsRejected() {
        assertEquals(ContextIntegrationDemo.MandateStatus.REJECTED,
                ContextIntegrationDemo.toMandateStatus(new ContextIntegrationDemo.PayLinkMandateResponse("m", "SOMETHING_NEW", null)));
    }

    @Test
    void aclQuarantinesWhatItCannotTranslate() {
        var acl = new PartnerShipmentAclDemo.ParcelGoAcl();
        assertTrue(acl.translate(new PartnerShipmentAclDemo.ParcelGoWebhook("A", "FC-1", "XYZ", 0, null)).isEmpty());
        assertTrue(acl.translate(new PartnerShipmentAclDemo.ParcelGoWebhook("B", "OTHER-1", "DLV", 0, null)).isEmpty());
        assertEquals(2, acl.quarantine.size());
        assertEquals(PartnerShipmentAclDemo.DeliveryStatus.DELIVERED,
                acl.translate(new PartnerShipmentAclDemo.ParcelGoWebhook("C", "FC-2", "DLV", 0, null)).orElseThrow().status());
    }

    @Test
    void contextMapImpactStopsAtAntiCorruptionLayers() {
        assertEquals(Set.of("Delivery"), ContextMappingDemo.impactOf("ParcelGo API"));
        assertTrue(ContextMappingDemo.impactOf("PayLink API").contains("Delivery"));
    }

    @Test
    void erpTranslationRemovesTaxAndSkipsDeletedItems() {
        var products = new AntiCorruptionLayerDemo.ErpProductTranslator().translate(List.of(
                new AntiCorruptionLayerDemo.ErpItemRow("000000000010045", "P", "KG", "0,2", "105,00", "V1", ""),
                new AntiCorruptionLayerDemo.ErpItemRow("000000000010046", "C", "G", "400", "10,00", "V1", "X")));
        assertEquals(1, products.size());
        assertEquals(10_000, products.get(0).pricePaiseExTax());
        assertEquals(200, products.get(0).grams());
    }
}
```

## 15. Observability & Operations

Monitor each integration point: messages consumed, skipped, quarantined and upcast per version; ACL translation failures by reason. Alert on quarantine growth. Track schema versions in use per consumer so old versions can be retired. A context map in the docs, linked to dashboards per context, helps on-call engineers understand blast radius.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2003 | Evans' *Domain-Driven Design*: bounded context, context map, ACL |
| 2013 | Vernon's *Implementing DDD*: context mapping in practice |
| 2013 | Brandolini introduces Event Storming |
| 2014+ | Microservices popularise bounded contexts as service boundaries |
| 2019 | Team Topologies links contexts to team design |
| 2020s | DDD Crew context map canvas, Context Mapper DSL |

## 17. Expert Deep Dive

- **Subdomain types.** Core (competitive advantage: invest), supporting (necessary, custom), generic (buy or use off-the-shelf). Spend DDD effort on core subdomains.
- **Team Topologies.** Stream-aligned teams own contexts; platform teams offer open host services; enabling teams help adopt patterns.
- **Context boundaries vs service boundaries.** A context may be one service, a module in a monolith, or several services; boundaries are about models and language, deployment is a separate choice.
- **Evolving boundaries.** Contexts split as understanding grows; plan for it with published languages and ACLs that make re-partitioning cheaper.
- **Data mesh parallels.** Domain-owned data products are bounded contexts for analytics, with published languages for data.

> 📖 **War story: four days without invoices**  
> DairyDay's Subscriptions team renamed `pricePerLitrePaise` to `unitPrice` in `SubscriptionStarted`, a "cleanup" approved in their own review. Billing's consumer treated messages without a price as malformed and skipped them, logging at DEBUG. For four days, 1,900 new subscribers received milk with no invoices. **Fixes:** a consumer-driven contract check in the producer's pipeline (exactly the check in Example 3); a rule that published fields are only added or deprecated, never renamed in place; quarantine with an alert for skipped messages; and a context map review in each team's planning. Recovered revenue required a manual backfill from the event log. **Lesson:** a published language is a promise to other teams; the producer's CI must check it.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's a ubiquitous language?** A **shared vocabulary** used by experts and in code.
2. **What's a bounded context?** The **boundary within which a model is consistent**.
3. **Can "product" mean different things?** **Yes**, in different contexts.
4. **What does an ACL do?** **Translates a foreign model** into ours.
5. **What's a published language?** A **documented exchange format**.

**▸ 🟡 L3**

1. **Shared kernel vs shared library?** Shared kernel is **small, deliberately co-owned**; libraries often grow into shared models.
2. **Conformist vs ACL?** **Adopt upstream's model** vs **translate it**.
3. **How do you find context boundaries?** **Language shifts, experts, change rates, event storming.**
4. **What's a tolerant reader?** A consumer that **ignores unknown fields** and **defaults optional ones**.
5. **Should contexts share database tables?** **No**.

**▸ 🔴 L4–L5**

1. **How do contexts relate to teams and services?** **One team per context**; context may be one or more services or a module.
2. **How do you evolve an event schema safely?** **Additive changes, versions, upcasting, consumer-driven contracts, registry compatibility.**
3. **Customer/Supplier isn't working; what now?** **Escalate priorities**, or switch to **ACL / separate ways**.
4. **Where do you invest DDD effort?** **Core subdomains**; buy generic ones.
5. **How do you migrate from a shared God model?** **Identify contexts, introduce context-specific models with ACLs, strangle the shared model.**

**▸ 💻 Hands-on**

**Task:** Add a schema compatibility rule to `ContextIntegrationDemo`: a new version may not make a previously optional field required.

> ✅ **Solution**  
> ```java
> static List<String> optionalBecameRequired(Schema previous, Schema proposed) {
>     return previous.optional().stream()
>             .filter(proposed.required()::contains)
>             .sorted()
>             .map(f -> "'" + f + "' changed from optional to required")
>             .toList();
> }
> // old producers (and old messages in the broker) may not include it; upcasters would need a default
> ```

## 19. Practice

- [ ] Run a one-hour event storming session on FreshCart's checkout and mark candidate bounded contexts.
    > 💡 **Hint**  
    > Look for pivotal events like `OrderPlaced` where language changes.

- [ ] Draw FreshCart's context map with the relationship types from this page.
    > 💡 **Hint**  
    > Start from `ContextMappingDemo.FRESHCART`.

- [ ] Write an ACL for a public API you use (weather, maps) into your own model.
    > 💡 **Hint**  
    > Translate units and codes; quarantine unknowns.

- [ ] Configure a Jackson `ObjectMapper` as a tolerant reader and test it with an extra field.
    > 💡 **Hint**  
    > `disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)`.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Language and model are valid only inside a bounded context.
> - Same word, different meanings → different contexts, linked by IDs.
> - Context map: partnership, shared kernel, customer/supplier, conformist, ACL, open host/published language.
> - ACL for legacy and volatile upstreams; quarantine what can't be translated.
> - Published language: versioned, additive changes, tolerant readers, consumer contracts.
> - One team per context; no shared tables.
>
> ```java
> for (ConsumerContract c : contracts)
>     for (String f : c.fieldsUsed())
>         if (!proposed.required().contains(f) && !proposed.optional().contains(f))
>             broken.add(c.consumer() + " needs '" + f + "'");
> ```

## 21. Further Reading

- Eric Evans, *Domain-Driven Design* (2003), Part IV "Strategic Design".
- Vlad Khononov, *Learning Domain-Driven Design* (2021).
- Alberto Brandolini, *Introducing EventStorming* (Leanpub).
- Matthew Skelton and Manuel Pais, *Team Topologies* (2nd ed., 2025).
- [DDD Crew: context mapping](https://github.com/ddd-crew/context-mapping) and [Context Mapper](https://contextmapper.org/).

---

[← Previous: Domain-Driven Design: Tactical Patterns](ddd-tactical.md) · [Next: Microservice Patterns →](microservice-patterns.md)
