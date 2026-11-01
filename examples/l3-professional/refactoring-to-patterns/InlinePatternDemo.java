import java.util.ArrayList;
import java.util.List;

/** Refactoring away from a pattern: an Observer with exactly one listener, forever, is just an indirect method call. */
public class InlinePatternDemo {

    static final List<String> audit = new ArrayList<>();

    // ---- Before: event bus, listener interface, registration, for one fixed listener. ----
    interface PriceChangedListener { void onPriceChanged(String sku, long oldP, long newP); }     // (1)!
    static final class PriceEvents {
        private final List<PriceChangedListener> listeners = new ArrayList<>();
        void register(PriceChangedListener l) { listeners.add(l); }
        void publish(String sku, long o, long n) { listeners.forEach(l -> l.onPriceChanged(sku, o, n)); }
    }
    static final class AuditListener implements PriceChangedListener {
        public void onPriceChanged(String sku, long o, long n) { audit.add(sku + " " + o + "->" + n); }
    }
    static final class CatalogBefore {
        private final PriceEvents events;
        CatalogBefore(PriceEvents events) { this.events = events; }
        void changePrice(String sku, long o, long n) { events.publish(sku, o, n); }   // where does this go? search the code base...
    }

    // ---- After: the one collaborator is called directly; the dependency is visible. ----
    record PriceAudit(List<String> log) { void record(String sku, long o, long n) { log.add(sku + " " + o + "->" + n); } }   // (2)!
    record Catalog(PriceAudit audit) {
        void changePrice(String sku, long o, long n) { audit.record(sku, o, n); }                                          // (3)!
    }

    public static void main(String[] args) {
        PriceEvents events = new PriceEvents();
        events.register(new AuditListener());
        new CatalogBefore(events).changePrice("GHEE-1L", 65_000, 62_000);
        System.out.println("before: " + audit);

        List<String> log = new ArrayList<>();
        new Catalog(new PriceAudit(log)).changePrice("GHEE-1L", 65_000, 62_000);
        System.out.println("after:  " + log + ", same=" + log.equals(audit));
    }
}
