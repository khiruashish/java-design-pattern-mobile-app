import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Records as Value Objects (and as Mementos): equality by value, validation in one place, immutable by default. */
public class RecordsValueObjectDemo {

    // ---------- before: a hand-written value class; someone forgot hashCode ----------
    static final class MoneyBefore {
        private final long paise; private final String currency;
        MoneyBefore(long paise, String currency) { this.paise = paise; this.currency = currency; }
        @Override public boolean equals(Object o) {
            return o instanceof MoneyBefore m && m.paise == paise && m.currency.equals(currency);
        }
        // hashCode missing: equal objects land in different HashSet buckets                       // (1)!
    }

    // ---------- after: a record ----------
    record Money(long paise, String currency) {
        Money {                                                                                   // (2)!
            Objects.requireNonNull(currency, "currency");
            if (paise < 0) throw new IllegalArgumentException("negative amount: " + paise);
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("bad currency: " + currency);
        }
        Money plus(Money other) {
            if (!currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
            return new Money(paise + other.paise, currency);
        }
        Money withPaise(long p) { return new Money(p, currency); }                                // (3)!
    }

    // Memento: a record snapshot of a cart's state; immutable, so it can't be changed after it's taken
    record CartSnapshot(List<String> skus, Money total) {
        CartSnapshot { skus = List.copyOf(skus); }                                                // (4)!
    }

    public static void main(String[] args) {
        Set<MoneyBefore> before = new HashSet<>(List.of(new MoneyBefore(6_400, "INR"), new MoneyBefore(6_400, "INR")));
        System.out.println("before: equal values in a set -> size " + before.size());

        Set<Money> after = new HashSet<>(List.of(new Money(6_400, "INR"), new Money(6_400, "INR")));
        System.out.println("record: equal values in a set -> size " + after.size() + ", printed as " + after.iterator().next());
        System.out.println("plus: " + new Money(6_400, "INR").plus(new Money(4_500, "INR")));
        try { new Money(-1, "INR"); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }

        List<String> live = new java.util.ArrayList<>(List.of("MILK"));
        CartSnapshot snap = new CartSnapshot(live, new Money(6_400, "INR"));
        live.add("BREAD");
        System.out.println("snapshot unaffected by later changes: " + snap.skus());
    }
}
