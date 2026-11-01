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
