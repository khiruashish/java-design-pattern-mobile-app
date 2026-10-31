import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production-grade builder for the checkout's OrderRequest:
 * collects ALL validation errors at once, merges duplicate lines, enforces business limits,
 * produces a deeply immutable object, and supports toBuilder() for edits (e.g. "reorder").
 */
public class OrderRequestBuilderDemo {

    record Line(String sku, int quantity, long unitPriceCents) {
        long totalCents() { return quantity * unitPriceCents; }
    }

    static final class ValidationException extends RuntimeException {
        final List<String> errors;
        ValidationException(List<String> errors) { super(String.join("; ", errors)); this.errors = List.copyOf(errors); }
    }

    record OrderRequest(String customerId, String storeId, List<Line> lines, LocalTime slotStart, String couponCode, Instant createdAt) {
        long subtotalCents() { return lines.stream().mapToLong(Line::totalCents).sum(); }

        static Builder builder(Clock clock) { return new Builder(clock); }

        Builder toBuilder(Clock clock) {                                                   // (1)!
            Builder b = new Builder(clock).customer(customerId).store(storeId).slot(slotStart).coupon(couponCode);
            lines.forEach(l -> b.add(l.sku(), l.quantity(), l.unitPriceCents()));
            return b;
        }

        static final class Builder {
            static final int MAX_LINES = 100, MAX_QTY_PER_SKU = 20;
            static final long MIN_ORDER_CENTS = 9_900;

            private final Clock clock;
            private String customerId, storeId, couponCode; private LocalTime slotStart;
            private final Map<String, Line> lines = new LinkedHashMap<>();               // keeps the order customers added items

            private Builder(Clock clock) { this.clock = Objects.requireNonNull(clock); }
            Builder customer(String v) { customerId = v; return this; }
            Builder store(String v) { storeId = v; return this; }
            Builder slot(LocalTime v) { slotStart = v; return this; }
            Builder coupon(String v) { couponCode = v == null || v.isBlank() ? null : v.strip().toUpperCase(); return this; }

            Builder add(String sku, int qty, long unitPriceCents) {
                lines.merge(sku, new Line(sku, qty, unitPriceCents),                     // (2)!
                        (old, add) -> new Line(sku, old.quantity() + add.quantity(), add.unitPriceCents()));
                return this;
            }

            Builder remove(String sku) { lines.remove(sku); return this; }

            OrderRequest build() {
                List<String> errors = new ArrayList<>();                                  // (3)!
                if (customerId == null) errors.add("customer is required");
                if (storeId == null) errors.add("store is required");
                if (lines.isEmpty()) errors.add("basket is empty");
                if (lines.size() > MAX_LINES) errors.add("at most " + MAX_LINES + " different items");
                lines.values().forEach(l -> {
                    if (l.quantity() < 1) errors.add(l.sku() + ": quantity must be at least 1");
                    if (l.quantity() > MAX_QTY_PER_SKU) errors.add(l.sku() + ": at most " + MAX_QTY_PER_SKU + " per order");
                    if (l.unitPriceCents() <= 0) errors.add(l.sku() + ": price must be positive");
                });
                long subtotal = lines.values().stream().mapToLong(Line::totalCents).sum();
                if (!lines.isEmpty() && subtotal < MIN_ORDER_CENTS) errors.add("minimum order is " + MIN_ORDER_CENTS + " cents, basket is " + subtotal);
                Instant now = clock.instant();
                if (slotStart == null) errors.add("delivery slot is required");
                else if (!slotStart.isAfter(LocalTime.ofInstant(now, clock.getZone()).plusMinutes(30)))
                    errors.add("slot " + slotStart + " must start at least 30 minutes from now");   // (4)!
                if (!errors.isEmpty()) throw new ValidationException(errors);
                return new OrderRequest(customerId, storeId, List.copyOf(lines.values()), slotStart, couponCode, now);   // (5)!
            }
        }
    }

    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T06:30:00Z"), ZoneId.of("Asia/Kolkata"));   // 12:00 in Pune

        OrderRequest order = OrderRequest.builder(clock)
                .customer("cust-42").store("pune-01").slot(LocalTime.of(18, 0)).coupon(" welcome20 ")
                .add("MILK-1L", 2, 129).add("RICE-5KG", 1, 54_900).add("MILK-1L", 1, 129)
                .build();
        System.out.println("lines: " + order.lines());
        System.out.println("subtotal: " + order.subtotalCents() + ", coupon: " + order.couponCode() + ", at " + order.createdAt());

        OrderRequest reorder = order.toBuilder(clock).remove("RICE-5KG").add("ATTA-5KG", 1, 26_500).build();
        System.out.println("reorder: " + reorder.lines().stream().map(Line::sku).toList() + ", original unchanged: " + order.lines().size());

        try {
            OrderRequest.builder(clock).store("pune-01").slot(LocalTime.of(12, 15)).add("EGGS-12", 25, 349).add("FREEBIE", 1, 0).build();
        } catch (ValidationException e) {
            System.out.println(e.errors.size() + " errors:");
            e.errors.forEach(err -> System.out.println("  - " + err));
        }
    }
}
