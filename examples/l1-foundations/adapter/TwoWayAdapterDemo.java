import java.util.ArrayList;
import java.util.List;

/** Two-way adapter: one object usable through the OLD and the NEW interface, so a migration can be gradual. */
public class TwoWayAdapterDemo {

    // Old interface, used by 40 legacy call sites: amounts as rupee strings, boolean results
    interface LegacyPayments { boolean pay(String customer, String amountRupees); }

    // New interface, used by new code: cents and a result record
    record Charge(String id, long cents, boolean ok) { }
    interface Payments { Charge charge(String customerId, long cents); }

    /** Implements BOTH. Every call ends up in the new implementation, so behaviour is identical either way. */
    static final class PaymentsBridge implements Payments, LegacyPayments {             // (1)!
        final List<String> ledger = new ArrayList<>();
        private int seq;

        public Charge charge(String customerId, long cents) {
            if (cents <= 0) return new Charge("rejected", cents, false);
            String id = "ch_" + (++seq);
            ledger.add(id + " " + customerId + " " + cents);
            return new Charge(id, cents, true);
        }

        public boolean pay(String customer, String amountRupees) {                       // (2)!
            long cents = new java.math.BigDecimal(amountRupees).movePointRight(2).longValueExact();
            return charge(customer, cents).ok();
        }
    }

    public static void main(String[] args) {
        PaymentsBridge bridge = new PaymentsBridge();
        LegacyPayments oldCheckout = bridge;                                              // (3)!
        Payments newCheckout = bridge;

        System.out.println("legacy pay 499.50 -> " + oldCheckout.pay("cust-1", "499.50"));
        System.out.println("new charge 12000 -> " + newCheckout.charge("cust-2", 12_000));
        System.out.println("legacy pay 0 -> " + oldCheckout.pay("cust-3", "0"));
        System.out.println("one ledger for both: " + bridge.ledger);
    }
}
