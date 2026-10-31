import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Static factory methods (Effective Java, Item 1): names, caching, validation, subtype freedom. */
public class StaticFactoryDemo {

    static final class Money {
        private static final Map<Long, Money> SMALL = new ConcurrentHashMap<>();
        private final long cents;

        private Money(long cents) { this.cents = cents; }                                // (1)!

        static Money ofCents(long cents) {                                               // (2)!
            if (cents < 0) throw new IllegalArgumentException("money cannot be negative: " + cents);
            return cents <= 1_000 ? SMALL.computeIfAbsent(cents, Money::new) : new Money(cents);   // (3)!
        }

        static Money parse(String text) {                                               // (4)!
            BigDecimal amount = new BigDecimal(text.replace("$", "").trim());
            if (amount.scale() > 2) throw new IllegalArgumentException("too many decimals: " + text);
            return ofCents(amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact());
        }

        static Money zero() { return ofCents(0); }

        @Override public String toString() { return "$%d.%02d".formatted(cents / 100, cents % 100); }
    }

    public static void main(String[] args) {
        System.out.println(Money.parse("$12.99") + " " + Money.ofCents(250) + " " + Money.zero());
        System.out.println("small amounts cached: " + (Money.ofCents(99) == Money.ofCents(99)));
        System.out.println("large amounts not cached: " + (Money.ofCents(50_000) == Money.ofCents(50_000)));
        for (String bad : new String[] {"$1.999", "-5"}) {
            try { Money.parse(bad); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
        System.out.println("JDK examples: " + Integer.valueOf(127).equals(Integer.valueOf(127)) + ", " + java.util.List.of(1, 2) + ", " + java.time.LocalDate.of(2026, 11, 2));
    }
}
