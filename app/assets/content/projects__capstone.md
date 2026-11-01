# Capstone: FreshCart

⏱ 6 to 20 hours to build · Prerequisites: L1 and L2 for milestones 1 to 2, L3 and L4 for milestones 3 to 6

> 📌 **TL;DR**  
> Build FreshCart, a grocery delivery backend and its mobile client contract, using most patterns in this playbook. You work through six milestones: domain model, pricing, delivery partners, checkout with saga, outbox and idempotency, the mobile contract, and production hardening. Each milestone lists requirements, the patterns to use and acceptance tests. A runnable one-file reference solution and its JUnit tests are included so you can compare.

## The product

FreshCart delivers groceries in Bengaluru and Pune within two hours. Customers order from a mobile app; products come from suppliers such as **DairyDay** and **GreenValley**; deliveries go through partners **QuickDrop** (Bengaluru) and **ParcelGo** (Pune); payments go through **PayLink**; SMS goes through **TextBlast**.

> 📊 *Diagram: open this page on the website to see it drawn.*

## Milestones

### M1: Domain model (L1 to L2)

**Build:** `Money` and `Product` value objects, a `Line` with a quantity invariant (1 to 20), and an `Order` aggregate whose lifecycle is Placed → Paid → Dispatched, or Cancelled from Placed or Paid.

**Patterns:** value objects with records ([DDD Tactical](../l4-senior/ddd-tactical.md)), [State](../l2-practitioner/state.md) with a sealed interface, [Builder](../l1-foundations/builder.md) optional for orders.

**Done when:**

- [ ] Negative money and invalid quantities can't be constructed.
- [ ] Illegal transitions (dispatching a cancelled order) throw with a clear message.
- [ ] The order records domain events (`OrderPlaced`, `OrderPaid`, …) instead of calling other services.

### M2: Pricing (L1 to L2)

**Build:** a delivery fee rule (free from Rs 500, otherwise Rs 29), and coupon `GREEN10`: 10% off organic items when the basket is at least Rs 300 and contains organic items. Cache catalogue lookups.

**Patterns:** [Strategy](../l1-foundations/strategy.md) for fees, [Specification](../l2-practitioner/specification.md) for eligibility, [Decorator](../l1-foundations/decorator.md) for the caching catalogue.

**Done when:**

- [ ] A new fee rule (members pay nothing) needs no change to checkout code.
- [ ] Coupon conditions combine with `and` / `or` and are unit-tested separately.
- [ ] Repeated catalogue lookups don't hit the database.

### M3: Delivery partners (L2 to L4)

**Build:** adapters for the QuickDrop and ParcelGo SDKs behind one `DeliveryPartner` interface; route by city; when ParcelGo fails, fall back to QuickDrop; after two consecutive ParcelGo failures, stop calling it (circuit open).

**Patterns:** [Adapter](../l1-foundations/adapter.md), [Factory Method](../l1-foundations/factory-method.md) for routing, circuit breaker and fallback from [Resilience Patterns](../l4-senior/resilience-patterns.md).

**Done when:**

- [ ] Partner SDK types never leak outside the adapters.
- [ ] With ParcelGo down, Pune orders still ship and ParcelGo stops receiving calls.

### M4: Checkout (L3 to L4)

**Build:** one `checkout(idempotencyKey, order, coupon)` entry point that reserves stock, charges PayLink, books delivery, and on any failure compensates in reverse (refund, release stock, cancel). The same key must never charge twice. Events are written to an outbox together with the state change and relayed to subscribers.

**Patterns:** [Facade](../l1-foundations/facade.md), saga and transactional outbox ([Distributed Data Patterns](../l4-senior/distributed-data-patterns.md)), idempotency key ([API Design Patterns](../l4-senior/api-design-patterns.md)), [Observer](../l1-foundations/observer.md) for subscribers.

**Done when:**

- [ ] A declined payment leaves stock unchanged and the order cancelled.
- [ ] A failed delivery booking refunds the payment.
- [ ] Retrying with the same key returns the same receipt without a second charge.
- [ ] No event is published unless the state change is stored.

### M5: Mobile client contract (L3)

**Build:** one home-screen endpoint that aggregates what the app needs in a single call.

**Patterns:** aggregator / backend for frontend ([UI and Mobile Patterns](../l3-professional/ui-and-mobile-patterns.md), [Microservice Patterns](../l4-senior/microservice-patterns.md)).

| Endpoint | Method | Purpose | Notes |
|----------|--------|---------|-------|
| `/v1/home` | GET | Loyalty points, recent orders, offers | One call per screen |
| `/v1/checkout` | POST | Place an order | Requires `Idempotency-Key` header |
| `/v1/orders/{id}` | GET | Order status | Cacheable for 10 s |
| `/v1/orders?cursor=` | GET | Order history | Cursor pagination |

Money is sent as integer `totalPaise` so the app never rounds floating-point values. Errors use Problem Details (`application/problem+json`).

**Done when:**

- [ ] The app renders the home screen from one response.
- [ ] Adding a field doesn't break older app versions (tolerant reader).

### M6: Production hardening (L4 to L5)

**Build:** replace the in-memory pieces with real infrastructure and add the checks from the [Production Checklist](../reference/production-checklist.md).

- [ ] Resilience4j instead of the hand-written breaker ([Maven example](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l4-senior/resilience-patterns/resilience4j)).
- [ ] Caffeine for the catalogue cache ([Maven example](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l4-senior/caching-patterns/caffeine)).
- [ ] ArchUnit rules for module boundaries ([Maven example](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l5-staff-principal/evolutionary-architecture/archunit)).
- [ ] A real outbox table and relay (or CDC), and idempotent consumers.
- [ ] Metrics, traces and alerts for checkout success rate and partner health.

## Reference solution

Try each milestone yourself first. The reference solution is a single runnable file, organised by milestone, so the whole flow fits on one screen of scrolling.

**FreshCartCapstoneDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * FreshCart capstone reference solution in one file. It wires together the patterns from the playbook: value
 * objects and an aggregate with State (M1), Strategy, Specification and a caching Decorator for pricing (M2),
 * Adapters, a Factory and a circuit breaker for delivery partners (M3), a checkout Facade running a saga with an
 * idempotency key and a transactional outbox (M4), Observer subscribers fed by the outbox relay, and an aggregated
 * home-screen contract for the mobile app (M5).
 */
public class FreshCartCapstoneDemo {

    // ===== M1: domain model =====
    record Money(long paise) implements Comparable<Money> {
        Money { if (paise < 0) throw new IllegalArgumentException("negative money"); }
        static Money rupees(long r) { return new Money(r * 100); }
        Money plus(Money o) { return new Money(paise + o.paise); }
        Money minus(Money o) { return new Money(Math.max(0, paise - o.paise)); }
        Money times(int n) { return new Money(paise * n); }
        Money percent(int p) { return new Money(paise * p / 100); }
        public int compareTo(Money o) { return Long.compare(paise, o.paise); }
        @Override public String toString() { return String.format("Rs %d.%02d", paise / 100, paise % 100); }
    }

    record Product(String sku, String name, Money price, Set<String> tags) {
        Product { tags = Set.copyOf(tags); }
    }

    record Line(Product product, int qty) {
        Line { if (qty < 1 || qty > 20) throw new IllegalArgumentException("quantity must be 1..20 for " + product.sku()); }
        Money total() { return product.price().times(qty); }
    }

    sealed interface OrderState permits Placed, Paid, Dispatched, Cancelled {
        default OrderState pay() { throw illegal("pay"); }
        default OrderState dispatch() { throw illegal("dispatch"); }
        default OrderState cancel() { throw illegal("cancel"); }
        private IllegalStateException illegal(String action) {
            return new IllegalStateException("cannot " + action + " an order that is " + getClass().getSimpleName().toUpperCase());
        }
    }
    record Placed() implements OrderState {
        public OrderState pay() { return new Paid(); }
        public OrderState cancel() { return new Cancelled(); }
    }
    record Paid() implements OrderState {
        public OrderState dispatch() { return new Dispatched(); }
        public OrderState cancel() { return new Cancelled(); }
    }
    record Dispatched() implements OrderState {}
    record Cancelled() implements OrderState {}

    record DomainEvent(String type, String orderId, String customer, String detail) {}

    static final class Order {
        final String id, customer, city;
        final List<Line> lines;
        private OrderState state = new Placed();
        private final List<DomainEvent> events = new ArrayList<>();

        Order(String id, String customer, String city, List<Line> lines) {
            if (lines.isEmpty()) throw new IllegalArgumentException("empty order");
            this.id = id; this.customer = customer; this.city = city; this.lines = List.copyOf(lines);
            events.add(new DomainEvent("OrderPlaced", id, customer, ""));
        }
        Money subtotal() { return lines.stream().map(Line::total).reduce(new Money(0), Money::plus); }
        OrderState state() { return state; }
        void markPaid(Money amount) { state = state.pay(); events.add(new DomainEvent("OrderPaid", id, customer, amount.toString())); }
        void markDispatched(String partner) { state = state.dispatch(); events.add(new DomainEvent("OrderDispatched", id, customer, partner)); }
        void cancel(String reason) { state = state.cancel(); events.add(new DomainEvent("OrderCancelled", id, customer, reason)); }
        List<DomainEvent> drainEvents() { var copy = List.copyOf(events); events.clear(); return copy; }
    }

    // ===== M2: pricing =====
    interface Catalogue { Product find(String sku); }

    static final class InMemoryCatalogue implements Catalogue {
        final Map<String, Product> products = new TreeMap<>();
        int lookups;
        public Product find(String sku) { lookups++; return products.get(sku); }
    }

    record CachingCatalogue(Catalogue inner, Map<String, Product> cache) implements Catalogue {       // Decorator
        CachingCatalogue(Catalogue inner) { this(inner, new HashMap<>()); }
        public Product find(String sku) { return cache.computeIfAbsent(sku, inner::find); }
    }

    interface DeliveryFeePolicy { Money fee(Money basket); }                                          // Strategy
    static final DeliveryFeePolicy STANDARD_FEE = b -> b.compareTo(Money.rupees(500)) >= 0 ? new Money(0) : Money.rupees(29);

    record Coupon(String code, Predicate<Order> eligible, int percentOffOrganic) {}                     // Specification
    static final Predicate<Order> BASKET_OVER_300 = o -> o.subtotal().compareTo(Money.rupees(300)) >= 0;
    static final Predicate<Order> HAS_ORGANIC = o -> o.lines.stream().anyMatch(l -> l.product().tags().contains("organic"));
    static final Coupon GREEN10 = new Coupon("GREEN10", BASKET_OVER_300.and(HAS_ORGANIC), 10);

    record Quote(Money subtotal, Money discount, Money deliveryFee, Money total) {}

    static Quote price(Order o, Coupon coupon, DeliveryFeePolicy fees) {
        Money discount = new Money(0);
        if (coupon != null && coupon.eligible().test(o)) {
            Money organic = o.lines.stream().filter(l -> l.product().tags().contains("organic")).map(Line::total).reduce(new Money(0), Money::plus);
            discount = organic.percent(coupon.percentOffOrganic());
        }
        Money afterDiscount = o.subtotal().minus(discount);
        Money fee = fees.fee(afterDiscount);
        return new Quote(o.subtotal(), discount, fee, afterDiscount.plus(fee));
    }

    // ===== M3: delivery partners =====
    interface DeliveryPartner { String name(); String book(String orderId); }

    static final class QuickDropApi { String createJob(String ref) { return "QD-" + ref; } }               // partner SDKs
    static final class ParcelGoApi {
        boolean down = true; int calls;
        Map<String, String> schedule(String ref) {
            calls++;
            if (down) throw new IllegalStateException("ParcelGo 503");
            return Map.of("awb", "PG-" + ref);
        }
    }

    record QuickDropAdapter(QuickDropApi api) implements DeliveryPartner {                               // Adapter
        public String name() { return "QuickDrop"; }
        public String book(String orderId) { return api.createJob(orderId); }
    }
    record ParcelGoAdapter(ParcelGoApi api) implements DeliveryPartner {
        public String name() { return "ParcelGo"; }
        public String book(String orderId) { return api.schedule(orderId).get("awb"); }
    }

    static final class CircuitBreaker {
        private final int threshold; private int failures; boolean open;
        CircuitBreaker(int threshold) { this.threshold = threshold; }
        <T> T call(java.util.function.Supplier<T> action) {
            if (open) throw new IllegalStateException("circuit open");
            try { T r = action.get(); failures = 0; return r; }
            catch (RuntimeException e) { if (++failures >= threshold) open = true; throw e; }
        }
    }

    static final class PartnerRouter {                                                                  // Factory + fallback
        final DeliveryPartner quickDrop, parcelGo;
        final CircuitBreaker parcelGoBreaker = new CircuitBreaker(2);
        PartnerRouter(DeliveryPartner quickDrop, DeliveryPartner parcelGo) { this.quickDrop = quickDrop; this.parcelGo = parcelGo; }

        DeliveryPartner primaryFor(String city) { return city.equals("Pune") ? parcelGo : quickDrop; }

        String[] book(Order o) {
            DeliveryPartner primary = primaryFor(o.city);
            if (primary == quickDrop) return new String[] {quickDrop.name(), quickDrop.book(o.id)};
            try {
                return new String[] {parcelGo.name(), parcelGoBreaker.call(() -> parcelGo.book(o.id))};
            } catch (RuntimeException e) {
                return new String[] {quickDrop.name() + " (fallback: " + e.getMessage() + ")", quickDrop.book(o.id)};
            }
        }
    }

    // ===== M4: checkout facade, saga, idempotency, outbox =====
    static final class Stock {
        final Map<String, Integer> units = new TreeMap<>();
        void reserve(Order o) {
            for (Line l : o.lines) if (units.getOrDefault(l.product().sku(), 0) < l.qty()) throw new IllegalStateException("out of stock: " + l.product().sku());
            o.lines.forEach(l -> units.merge(l.product().sku(), -l.qty(), Integer::sum));
        }
        void release(Order o) { o.lines.forEach(l -> units.merge(l.product().sku(), l.qty(), Integer::sum)); }
    }

    static final class PayLink {
        final List<String> ledger = new ArrayList<>();
        final Money limit = Money.rupees(2000);
        String charge(String orderId, Money amount) {
            if (amount.compareTo(limit) > 0) throw new IllegalStateException("PayLink declined " + amount);
            ledger.add("charge " + orderId + " " + amount);
            return "PL-" + orderId;
        }
        void refund(String paymentId, Money amount) { ledger.add("refund " + paymentId + " " + amount); }
    }

    record Receipt(String orderId, String status, Quote quote, String partner, String tracking, String note) {}

    static final class CheckoutFacade {
        final Stock stock; final PayLink payLink; final PartnerRouter partners;
        final Map<String, Receipt> idempotency = new HashMap<>();
        final List<DomainEvent> outbox = new ArrayList<>();

        CheckoutFacade(Stock stock, PayLink payLink, PartnerRouter partners) { this.stock = stock; this.payLink = payLink; this.partners = partners; }

        Receipt checkout(String idempotencyKey, Order order, Coupon coupon) {
            Receipt previous = idempotency.get(idempotencyKey);
            if (previous != null) return previous;
            Quote quote = price(order, coupon, STANDARD_FEE);
            Receipt receipt;
            String paymentId = null;
            boolean reserved = false;
            try {                                                                                     // saga steps
                stock.reserve(order); reserved = true;
                paymentId = payLink.charge(order.id, quote.total());
                order.markPaid(quote.total());
                String[] booking = partners.book(order);
                order.markDispatched(booking[0]);
                receipt = new Receipt(order.id, "DISPATCHED", quote, booking[0], booking[1], "");
            } catch (RuntimeException e) {                                                            // compensations in reverse
                if (paymentId != null) payLink.refund(paymentId, quote.total());
                if (reserved) stock.release(order);
                order.cancel(e.getMessage());
                receipt = new Receipt(order.id, "CANCELLED", quote, "-", "-", e.getMessage());
            }
            outbox.addAll(order.drainEvents());                                                       // same "transaction" as the state change
            idempotency.put(idempotencyKey, receipt);
            return receipt;
        }
    }

    static final class EventBus {                                                                       // Observer
        private final Map<String, List<Consumer<DomainEvent>>> subscribers = new LinkedHashMap<>();
        void subscribe(String type, Consumer<DomainEvent> s) { subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(s); }
        void publish(DomainEvent e) { subscribers.getOrDefault(e.type(), List.of()).forEach(s -> s.accept(e)); }
    }

    static int relay(List<DomainEvent> outbox, EventBus bus) {
        int n = outbox.size();
        outbox.forEach(bus::publish);
        outbox.clear();
        return n;
    }

    // ===== M5: mobile home-screen contract =====
    static String homeScreenJson(String customer, int loyaltyPoints, List<Receipt> recent, List<Product> offers) {
        StringBuilder json = new StringBuilder("{\n  \"customer\": \"" + customer + "\",\n  \"loyaltyPoints\": " + loyaltyPoints + ",\n  \"recentOrders\": [");
        for (int i = 0; i < recent.size(); i++) {
            Receipt r = recent.get(i);
            json.append(i == 0 ? "\n" : ",\n").append("    {\"id\": \"").append(r.orderId()).append("\", \"status\": \"").append(r.status())
                .append("\", \"totalPaise\": ").append(r.quote().total().paise()).append("}");
        }
        json.append("\n  ],\n  \"offers\": [");
        for (int i = 0; i < offers.size(); i++) json.append(i == 0 ? "" : ", ").append('"').append(offers.get(i).name()).append('"');
        return json.append("]\n}").toString();
    }

    // ===== wiring and scenario =====
    public static void main(String[] args) {
        InMemoryCatalogue db = new InMemoryCatalogue();
        for (Product p : List.of(
                new Product("MILK-1L", "DairyDay Milk 1L", new Money(6800), Set.of("dairy")),
                new Product("CURD-500G", "DairyDay Curd 500g", new Money(4500), Set.of("dairy")),
                new Product("SPINACH-250G", "GreenValley Organic Spinach", new Money(6000), Set.of("organic", "vegetables")),
                new Product("RICE-5KG", "Basmati Rice 5kg", new Money(64000), Set.of("staples")))) db.products.put(p.sku(), p);
        Catalogue catalogue = new CachingCatalogue(db);
        Stock stock = new Stock();
        stock.units.putAll(Map.of("MILK-1L", 50, "CURD-500G", 30, "SPINACH-250G", 10, "RICE-5KG", 5));
        PayLink payLink = new PayLink();
        ParcelGoApi parcelGoApi = new ParcelGoApi();
        CheckoutFacade checkout = new CheckoutFacade(stock, payLink, new PartnerRouter(new QuickDropAdapter(new QuickDropApi()), new ParcelGoAdapter(parcelGoApi)));

        Map<String, Integer> points = new TreeMap<>();
        EventBus bus = new EventBus();
        bus.subscribe("OrderDispatched", e -> System.out.println("  TextBlast SMS to " + e.customer() + ": order " + e.orderId() + " is on its way with " + e.detail().split(" ")[0]));
        bus.subscribe("OrderPaid", e -> points.merge(e.customer(), 10, Integer::sum));
        bus.subscribe("OrderCancelled", e -> System.out.println("  TextBlast SMS to " + e.customer() + ": order " + e.orderId() + " cancelled, no charge"));

        System.out.println("M2 pricing and M4 checkout:");
        Order o1 = new Order("FC-1001", "asha", "Bengaluru", List.of(new Line(catalogue.find("MILK-1L"), 2),
                new Line(catalogue.find("SPINACH-250G"), 3), new Line(catalogue.find("CURD-500G"), 1)));
        Receipt r1 = checkout.checkout("key-asha-1", o1, GREEN10);
        System.out.println("  " + r1.orderId() + " " + r1.status() + " via " + r1.partner() + " " + r1.tracking() + ": subtotal " + r1.quote().subtotal()
                + ", GREEN10 -" + r1.quote().discount() + ", delivery " + r1.quote().deliveryFee() + ", total " + r1.quote().total());
        Receipt again = checkout.checkout("key-asha-1", o1, GREEN10);
        System.out.println("  retry with the same idempotency key returns the same receipt: " + (again == r1) + ", charges so far " + payLink.ledger.size());

        System.out.println("M3 partners in Pune (ParcelGo is down):");
        List<Receipt> pune = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Order o = new Order("FC-200" + i, "bilal", "Pune", List.of(new Line(catalogue.find("MILK-1L"), 1), new Line(catalogue.find("RICE-5KG"), 1)));
            Receipt r = checkout.checkout("key-bilal-" + i, o, null);
            pune.add(r);
            System.out.println("  " + r.orderId() + " " + r.status() + " via " + r.partner());
        }
        System.out.println("  ParcelGo was called " + parcelGoApi.calls + " times; breaker open = " + checkout.partners.parcelGoBreaker.open);

        System.out.println("M4 saga compensation:");
        Order big = new Order("FC-3001", "chen", "Bengaluru", List.of(new Line(catalogue.find("RICE-5KG"), 2), new Line(catalogue.find("MILK-1L"), 20)));
        Receipt rb = checkout.checkout("key-chen-1", big, null);
        System.out.println("  " + rb.orderId() + " " + rb.status() + ": " + rb.note() + "; rice stock back to " + stock.units.get("RICE-5KG") + ", milk " + stock.units.get("MILK-1L"));
        try {
            big.markDispatched("QuickDrop");
        } catch (IllegalStateException e) {
            System.out.println("  state guard: " + e.getMessage());
        }

        System.out.println("M4 outbox relay to subscribers:");
        int published = relay(checkout.outbox, bus);
        System.out.println("  published " + published + " events; loyalty points " + points);
        System.out.println("  PayLink ledger " + payLink.ledger);
        System.out.println("  catalogue lookups hitting the database: " + db.lookups + " (cache serves the rest)");

        System.out.println("M5 home screen contract for the mobile app:");
        List<Product> offers = db.products.values().stream().filter(p -> p.tags().contains("organic") || p.tags().contains("dairy")).toList();
        System.out.println(homeScreenJson("asha", points.getOrDefault("asha", 0), List.of(r1), offers));
    }
}
```

**Run it:** `java FreshCartCapstoneDemo.java`

```text
M2 pricing and M4 checkout:
  FC-1001 DISPATCHED via QuickDrop QD-FC-1001: subtotal Rs 361.00, GREEN10 -Rs 18.00, delivery Rs 29.00, total Rs 372.00
  retry with the same idempotency key returns the same receipt: true, charges so far 1
M3 partners in Pune (ParcelGo is down):
  FC-2001 DISPATCHED via QuickDrop (fallback: ParcelGo 503)
  FC-2002 DISPATCHED via QuickDrop (fallback: ParcelGo 503)
  FC-2003 DISPATCHED via QuickDrop (fallback: circuit open)
  ParcelGo was called 2 times; breaker open = true
M4 saga compensation:
  FC-3001 CANCELLED: PayLink declined Rs 2640.00; rice stock back to 2, milk 45
  state guard: cannot dispatch an order that is CANCELLED
M4 outbox relay to subscribers:
  TextBlast SMS to asha: order FC-1001 is on its way with QuickDrop
  TextBlast SMS to bilal: order FC-2001 is on its way with QuickDrop
  TextBlast SMS to bilal: order FC-2002 is on its way with QuickDrop
  TextBlast SMS to bilal: order FC-2003 is on its way with QuickDrop
  TextBlast SMS to chen: order FC-3001 cancelled, no charge
  published 14 events; loyalty points {asha=10, bilal=30}
  PayLink ledger [charge FC-1001 Rs 372.00, charge FC-2001 Rs 708.00, charge FC-2002 Rs 708.00, charge FC-2003 Rs 708.00]
  catalogue lookups hitting the database: 4 (cache serves the rest)
M5 home screen contract for the mobile app:
{
  "customer": "asha",
  "loyaltyPoints": 10,
  "recentOrders": [
    {"id": "FC-1001", "status": "DISPATCHED", "totalPaise": 37200}
  ],
  "offers": ["DairyDay Curd 500g", "DairyDay Milk 1L", "GreenValley Organic Spinach"]
}
```

**What to notice:**

- GREEN10 takes 10% of the organic spinach only (Rs 18.00 of Rs 180.00), and the delivery fee still applies because the basket is under Rs 500.
- The second checkout with `key-asha-1` returns the stored receipt; PayLink's ledger shows one charge.
- ParcelGo was called twice; the third Pune order went straight to the fallback because the circuit was open.
- The oversized order was declined by PayLink, and the saga put stock back exactly as it was.
- Events were collected in the outbox during checkout and only reached SMS and loyalty when the relay ran.

## Tests

**FreshCartCapstoneTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FreshCartCapstoneTest {

    static final FreshCartCapstoneDemo.Product MILK = new FreshCartCapstoneDemo.Product("MILK-1L", "Milk", new FreshCartCapstoneDemo.Money(6800), Set.of("dairy"));
    static final FreshCartCapstoneDemo.Product SPINACH = new FreshCartCapstoneDemo.Product("SPINACH-250G", "Spinach", new FreshCartCapstoneDemo.Money(6000), Set.of("organic"));

    FreshCartCapstoneDemo.Stock stock;
    FreshCartCapstoneDemo.PayLink payLink;

    @BeforeEach
    void setUp() {
        stock = new FreshCartCapstoneDemo.Stock();
        stock.units.putAll(Map.of("MILK-1L", 10, "SPINACH-250G", 10));
        payLink = new FreshCartCapstoneDemo.PayLink();
    }

    private FreshCartCapstoneDemo.CheckoutFacade facade(FreshCartCapstoneDemo.DeliveryPartner quickDrop) {
        return new FreshCartCapstoneDemo.CheckoutFacade(stock, payLink,
                new FreshCartCapstoneDemo.PartnerRouter(quickDrop, new FreshCartCapstoneDemo.ParcelGoAdapter(new FreshCartCapstoneDemo.ParcelGoApi())));
    }

    private static FreshCartCapstoneDemo.Order order(String id, int milk, int spinach) {
        return new FreshCartCapstoneDemo.Order(id, "asha", "Bengaluru",
                List.of(new FreshCartCapstoneDemo.Line(MILK, milk), new FreshCartCapstoneDemo.Line(SPINACH, spinach)));
    }

    @Test
    void lineQuantityInvariantIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new FreshCartCapstoneDemo.Line(MILK, 21));
    }

    @Test
    void couponDiscountsOnlyOrganicItemsWhenEligible() {
        var quote = FreshCartCapstoneDemo.price(order("A", 2, 3), FreshCartCapstoneDemo.GREEN10, FreshCartCapstoneDemo.STANDARD_FEE);
        assertEquals(1800, quote.discount().paise());                // 10% of 3 x Rs 60
        var small = FreshCartCapstoneDemo.price(order("B", 1, 1), FreshCartCapstoneDemo.GREEN10, FreshCartCapstoneDemo.STANDARD_FEE);
        assertEquals(0, small.discount().paise());                   // basket under Rs 300
    }

    @Test
    void sameIdempotencyKeyChargesOnce() {
        var checkout = facade(new FreshCartCapstoneDemo.QuickDropAdapter(new FreshCartCapstoneDemo.QuickDropApi()));
        var order = order("A", 1, 1);
        var first = checkout.checkout("k1", order, null);
        assertSame(first, checkout.checkout("k1", order, null));
        assertEquals(1, payLink.ledger.size());
    }

    @Test
    void failedDeliveryBookingRefundsAndReleasesStock() {
        var broken = new FreshCartCapstoneDemo.DeliveryPartner() {
            public String name() { return "QuickDrop"; }
            public String book(String orderId) { throw new IllegalStateException("QuickDrop unavailable"); }
        };
        var receipt = facade(broken).checkout("k2", order("A", 2, 1), null);
        assertEquals("CANCELLED", receipt.status());
        assertEquals(List.of("charge A Rs 225.00", "refund PL-A Rs 225.00"), payLink.ledger);
        assertEquals(10, stock.units.get("MILK-1L"));
    }

    @Test
    void cancelledOrderCannotBeDispatched() {
        var order = order("A", 1, 1);
        order.cancel("customer request");
        assertInstanceOf(FreshCartCapstoneDemo.Cancelled.class, order.state());
        assertThrows(IllegalStateException.class, () -> order.markDispatched("QuickDrop"));
    }
}
```

## Assessing your solution

| Level | What a good solution shows |
|-------|----------------------------|
| L1–L2 | Correct value objects and state transitions, patterns used where they remove real duplication, unit tests per rule |
| L3 | Clear package boundaries, idempotent checkout, outbox, consistent error handling, an API contract the app team can use |
| L4 | Resilience tuned per partner, saga failure cases tested, observability, an explanation of consistency trade-offs |
| L5 | Architecture decision records, fitness functions, a plan to split into services only where boundaries are proven |

## Stretch goals

- Split checkout, pricing and delivery into separate services with Kafka between them ([Messaging Patterns](../l4-senior/messaging-patterns.md)).
- Model the order with event sourcing and build an order-history projection ([Distributed Data Patterns](../l4-senior/distributed-data-patterns.md)).
- Add a supplier feed pipeline for DairyDay price updates with a content-based router and dead letter channel.
- Migrate the legacy loyalty system with a strangler fig and parallel run ([Legacy Modernisation](../l5-staff-principal/legacy-modernisation.md)).
