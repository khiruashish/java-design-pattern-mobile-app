import java.util.Arrays;
import java.util.List;

/** Sealed interfaces: a closed set of variants the compiler knows, so switches can be checked for completeness. */
public class SealedHierarchyDemo {

    // ---------- before: an open hierarchy; any class anywhere can extend it ----------
    abstract static class PaymentBefore { abstract long paise(); }
    static final class UpiBefore extends PaymentBefore { long paise() { return 100; } }
    static final class SneakyGiftCard extends PaymentBefore { long paise() { return 100; } }     // added later in another module

    static String feeBefore(PaymentBefore p) {
        if (p instanceof UpiBefore) return "no fee";
        return "card fee 2%";                                                                     // (1)!
    }

    // ---------- after: sealed; every variant is listed ----------
    sealed interface Payment permits Upi, Card, CashOnDelivery { long paise(); }                   // (2)!
    record Upi(String vpa, long paise) implements Payment { }
    record Card(String last4, String network, long paise) implements Payment { }
    record CashOnDelivery(long paise) implements Payment { }

    static long feePaise(Payment p) {
        return switch (p) {                                                                       // (3)!
            case Upi u -> 0;
            case Card c -> c.paise() * 2 / 100;
            case CashOnDelivery c -> 2_000;
        };
    }

    public static void main(String[] args) {
        System.out.println("before, gift card: " + feeBefore(new SneakyGiftCard()) + "   <- silently treated as a card");
        for (Payment p : List.of(new Upi("asha@okbank", 64_900), new Card("4242", "VISA", 64_900), new CashOnDelivery(64_900)))
            System.out.println(p.getClass().getSimpleName() + " fee: " + feePaise(p));
        System.out.println("permitted: " + Arrays.stream(Payment.class.getPermittedSubclasses()).map(Class::getSimpleName).toList());
    }
}
