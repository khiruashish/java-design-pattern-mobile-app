import java.util.Map;
import java.util.function.Supplier;

/** Simple factory (one class decides) vs Factory Method (subclasses decide). */
public class SimpleFactoryVsFactoryMethodDemo {

    interface Courier { String book(String orderId); }
    record InHouseCourier() implements Courier { public String book(String id) { return "in-house rider for " + id; } }
    record PartnerCourier(String partner) implements Courier { public String book(String id) { return partner + " courier for " + id; } }

    // ===================== Simple factory: a plain class with a creation method =====================
    static final class CourierFactory {                                                  // (1)!
        private final Map<String, Supplier<Courier>> byCity = Map.of(
                "pune", InHouseCourier::new,
                "mumbai", () -> new PartnerCourier("QuickDrop"),
                "delhi", () -> new PartnerCourier("ParcelGo"));

        Courier forCity(String city) {
            Supplier<Courier> s = byCity.get(city);
            if (s == null) throw new IllegalArgumentException("no courier configured for " + city);
            return s.get();
        }
    }

    // ===================== Factory Method: the workflow class leaves creation to subclasses =====================
    abstract static class Dispatcher {                                                   // (2)!
        abstract Courier courier();
        final String dispatch(String orderId) { return "[" + getClass().getSimpleName() + "] " + courier().book(orderId); }
    }
    static final class DarkStoreDispatcher extends Dispatcher { Courier courier() { return new InHouseCourier(); } }
    static final class MarketplaceDispatcher extends Dispatcher { Courier courier() { return new PartnerCourier("ParcelGo"); } }

    public static void main(String[] args) {
        CourierFactory factory = new CourierFactory();
        System.out.println("simple factory: " + factory.forCity("mumbai").book("FC-1"));
        try { factory.forCity("goa"); } catch (IllegalArgumentException e) { System.out.println("simple factory: rejected: " + e.getMessage()); }
        System.out.println("factory method: " + new DarkStoreDispatcher().dispatch("FC-2"));
        System.out.println("factory method: " + new MarketplaceDispatcher().dispatch("FC-3"));
    }
}
