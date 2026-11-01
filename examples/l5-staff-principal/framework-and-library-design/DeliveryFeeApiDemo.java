/**
 * A small library API designed to evolve: callers see an interface and a static factory, never the implementation
 * class, so the implementation can change freely. New behaviour arrives as a default method, so existing
 * third-party implementations keep compiling and running.
 */
public class DeliveryFeeApiDemo {

    /** Public API (version 1.1). */
    public interface DeliveryFees {
        long feePaise(long basketPaise);

        static DeliveryFees standard() {                                                           // (1)!
            return new TieredFees(50000, 2900);
        }

        default long feePaise(long basketPaise, boolean member) {                                  // (2)!
            return member ? 0 : feePaise(basketPaise);
        }
    }

    /** Hidden implementation: package-private in a real library, so callers can't depend on it. */
    record TieredFees(long freeAbovePaise, long feePaise) implements DeliveryFees {
        public long feePaise(long basketPaise) { return basketPaise >= freeAbovePaise ? 0 : feePaise; }
    }

    /** A partner's implementation written against version 1.0, before the default method existed. */
    static final class QuickDropFlatFee implements DeliveryFees {
        public long feePaise(long basketPaise) { return 3500; }
    }

    public static void main(String[] args) {
        DeliveryFees fees = DeliveryFees.standard();
        System.out.println("standard fee on Rs 300: Rs " + fees.feePaise(30000) / 100.0);
        System.out.println("standard fee on Rs 600: Rs " + fees.feePaise(60000) / 100.0);
        System.out.println("member fee on Rs 300: Rs " + fees.feePaise(30000, true) / 100.0);
        DeliveryFees partner = new QuickDropFlatFee();
        System.out.println("QuickDrop 1.0 plugin, member call still works: Rs " + partner.feePaise(30000, true) / 100.0);
    }
}
