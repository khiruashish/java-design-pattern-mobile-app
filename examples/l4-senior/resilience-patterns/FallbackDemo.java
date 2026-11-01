import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fallback: when a dependency fails, answer with something useful: a cached value (marked stale), a safe default,
 * or a degraded feature. Never fall back on business errors, and never on writes that must not be faked.
 */
public class FallbackDemo {

    record Eta(int minutes, String source) {}
    static final class EtaNotAvailable extends RuntimeException { EtaNotAvailable(String m) { super(m); } }
    static final class UnknownOrder extends RuntimeException { UnknownOrder(String m) { super(m); } }

    static final Map<String, Eta> lastKnown = new HashMap<>();

    static Eta etaFor(String orderId, Supplier<Integer> routingService) {
        try {
            Eta live = new Eta(routingService.get(), "live");
            lastKnown.put(orderId, live);
            return live;
        } catch (UnknownOrder e) {
            throw e;                                                                              // (1)!
        } catch (RuntimeException e) {
            Eta cached = lastKnown.get(orderId);
            if (cached != null) return new Eta(cached.minutes(), "cached, may be stale");          // (2)!
            return new Eta(45, "default slot estimate");                                         // (3)!
        }
    }

    public static void main(String[] args) {
        System.out.println("FC-1 " + etaFor("FC-1", () -> 18));
        System.out.println("FC-1 " + etaFor("FC-1", () -> { throw new EtaNotAvailable("routing 503"); }));
        System.out.println("FC-2 " + etaFor("FC-2", () -> { throw new EtaNotAvailable("routing 503"); }));
        try { etaFor("FC-9", () -> { throw new UnknownOrder("no order FC-9"); }); }
        catch (UnknownOrder e) { System.out.println("FC-9 -> 404: " + e.getMessage()); }
    }
}
