import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BasketDemo {

    // ---------- Value object: immutable, validated on creation ----------
    record Money(long cents) implements Comparable<Money> {
        static final Money ZERO = new Money(0);

        Money {
            if (cents < 0) throw new IllegalArgumentException("Money cannot be negative: " + cents);
        }
        Money plus(Money other) { return new Money(Math.addExact(cents, other.cents)); }
        Money times(int quantity) { return new Money(Math.multiplyExact(cents, quantity)); }
        public int compareTo(Money other) { return Long.compare(cents, other.cents); }
        @Override public String toString() { return "$%d.%02d".formatted(cents / 100, cents % 100); }
    }

    record Line(String sku, int quantity, Money unitPrice) {
        Line {
            Objects.requireNonNull(sku, "sku");
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        Money subtotal() { return unitPrice.times(quantity); }
    }

    // ---------- Abstractions the basket depends on (DIP) ----------
    interface PriceCatalog {                                   // (1)!
        Optional<Money> priceOf(String sku);
    }

    interface BasketEvents {                                   // (2)!
        void itemAdded(String basketId, Line line);
        void itemRejected(String basketId, String sku, String reason);
    }

    static final class UnknownProductException extends RuntimeException {
        UnknownProductException(String sku) { super("Unknown product: " + sku); }
    }

    static final class BasketLimitException extends RuntimeException {
        BasketLimitException(int limit) { super("Basket limit of " + limit + " items reached"); }
    }

    // ---------- Entity with encapsulated state and invariants ----------
    static final class Basket {
        private final String id;
        private final int maxItems;
        private final PriceCatalog catalog;
        private final BasketEvents events;
        private final List<Line> lines = new ArrayList<>();    // (3)!

        Basket(String id, int maxItems, PriceCatalog catalog, BasketEvents events) {
            this.id = Objects.requireNonNull(id, "id");
            if (maxItems <= 0) throw new IllegalArgumentException("maxItems must be positive");
            this.maxItems = maxItems;
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            this.events = Objects.requireNonNull(events, "events");
        }

        void add(String sku, int quantity) {
            try {
                Money price = catalog.priceOf(sku).orElseThrow(() -> new UnknownProductException(sku));
                if (itemCount() + quantity > maxItems) throw new BasketLimitException(maxItems);
                Line line = new Line(sku, quantity, price);
                lines.add(line);
                events.itemAdded(id, line);
            } catch (UnknownProductException | BasketLimitException | IllegalArgumentException e) {
                events.itemRejected(id, sku, e.getMessage());
                throw e;
            }
        }

        int itemCount() { return lines.stream().mapToInt(Line::quantity).sum(); }

        Money total() { return lines.stream().map(Line::subtotal).reduce(Money.ZERO, Money::plus); }

        List<Line> lines() { return Collections.unmodifiableList(lines); }  // (4)!
    }

    // ---------- Adapters used at the edge of the program ----------
    record InMemoryCatalog(Map<String, Money> prices) implements PriceCatalog {
        public Optional<Money> priceOf(String sku) { return Optional.ofNullable(prices.get(sku)); }
    }

    static final class ConsoleEvents implements BasketEvents {
        public void itemAdded(String basketId, Line line) {
            System.out.printf("event=item_added basket=%s sku=%s qty=%d subtotal=%s%n",
                    basketId, line.sku(), line.quantity(), line.subtotal());
        }
        public void itemRejected(String basketId, String sku, String reason) {
            System.out.printf("event=item_rejected basket=%s sku=%s reason=\"%s\"%n", basketId, sku, reason);
        }
    }

    public static void main(String[] args) {
        int maxItems = Integer.getInteger("freshcart.basket.maxItems", 10);   // (5)!
        PriceCatalog catalog = new InMemoryCatalog(Map.of(
                "MILK-1L", new Money(129),
                "BREAD-WHT", new Money(249),
                "APPLE-GALA", new Money(65)));

        Basket basket = new Basket("B-42", maxItems, catalog, new ConsoleEvents());
        basket.add("MILK-1L", 2);
        basket.add("BREAD-WHT", 1);
        basket.add("APPLE-GALA", 6);

        for (String[] attempt : new String[][] {{"CAVIAR-50G", "1"}, {"APPLE-GALA", "3"}, {"MILK-1L", "0"}}) {
            try {
                basket.add(attempt[0], Integer.parseInt(attempt[1]));
            } catch (RuntimeException e) {
                System.out.println("rejected: " + e.getMessage());
            }
        }

        try {
            basket.lines().clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("rejected: callers cannot modify the basket's lines directly");
        }

        System.out.printf("basket=%s items=%d total=%s%n", "B-42", basket.itemCount(), basket.total());
    }
}
