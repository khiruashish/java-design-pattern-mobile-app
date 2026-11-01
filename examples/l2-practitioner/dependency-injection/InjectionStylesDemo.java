import java.lang.reflect.Field;

/** Constructor, setter and field injection side by side, and what each allows to go wrong. */
public class InjectionStylesDemo {

    interface Payments { String charge(long cents); }
    record FakePayments() implements Payments { public String charge(long c) { return "charged " + c; } }

    // ===================== Constructor injection =====================
    static final class CheckoutA {
        private final Payments payments;                                                   // (1)!
        CheckoutA(Payments payments) { this.payments = java.util.Objects.requireNonNull(payments, "payments"); }
        String pay(long c) { return payments.charge(c); }
    }

    // ===================== Setter injection =====================
    static final class CheckoutB {
        private Payments payments;
        void setPayments(Payments p) { this.payments = p; }                               // (2)!
        String pay(long c) { return payments.charge(c); }
    }

    // ===================== Field injection (what @Autowired on a field does, via reflection) =====================
    static final class CheckoutC {
        @SuppressWarnings("unused") private Payments payments;                            // (3)!
        String pay(long c) { return payments.charge(c); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("constructor: " + new CheckoutA(new FakePayments()).pay(100));
        try { new CheckoutA(null); } catch (NullPointerException e) { System.out.println("constructor rejects missing dependency: " + e.getMessage()); }

        CheckoutB b = new CheckoutB();
        try { b.pay(100); } catch (NullPointerException e) { System.out.println("setter: object usable before it's ready -> NullPointerException"); }
        b.setPayments(new FakePayments());
        System.out.println("setter after wiring: " + b.pay(100));

        CheckoutC c = new CheckoutC();
        Field f = CheckoutC.class.getDeclaredField("payments");
        f.setAccessible(true);
        f.set(c, new FakePayments());                                                     // (4)!
        System.out.println("field (needs reflection, even in tests): " + c.pay(100));
    }
}
