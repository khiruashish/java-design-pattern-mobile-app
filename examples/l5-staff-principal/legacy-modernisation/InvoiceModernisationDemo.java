import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Production-grade: FreshCart moves invoice generation off the legacy billing system one customer segment at a
 * time. Each segment is LEGACY, SHADOW (parallel run, legacy served) or NEW. Legacy output passes through an
 * anti-corruption layer; promotion needs enough clean comparisons; in NEW mode, failures fall back to legacy and
 * too many failures roll the segment back to SHADOW automatically.
 */
public class InvoiceModernisationDemo {

    record Order(String id, String segment, long netPaise) {}
    record Invoice(String orderId, long netPaise, long gstPaise) {}
    enum Mode { LEGACY, SHADOW, NEW }

    /** Legacy output: fixed-width "INV" + order id (8) + net (9, paise) + gst (7, paise). */
    static Invoice fromLegacy(String record) {                                                             // (1)!
        return new Invoice(record.substring(3, 11).trim(), Long.parseLong(record.substring(11, 20)), Long.parseLong(record.substring(20, 27)));
    }

    static final class Migrator {
        final Map<String, Mode> modes = new TreeMap<>();
        final Map<String, int[]> shadowStats = new TreeMap<>();     // [compared, unexplained]
        final Map<String, int[]> newStats = new TreeMap<>();        // [served, failures]
        final List<String> log = new ArrayList<>();
        private final Function<Order, String> legacy; private final Function<Order, Invoice> modern;
        Migrator(Function<Order, String> legacy, Function<Order, Invoice> modern) { this.legacy = legacy; this.modern = modern; }

        Invoice invoice(Order o) {
            Mode mode = modes.getOrDefault(o.segment(), Mode.LEGACY);                                     // (2)!
            if (mode == Mode.LEGACY) return fromLegacy(legacy.apply(o));
            if (mode == Mode.SHADOW) {
                Invoice served = fromLegacy(legacy.apply(o));
                int[] s = shadowStats.computeIfAbsent(o.segment(), k -> new int[2]);
                s[0]++;
                try {
                    Invoice candidate = modern.apply(o);
                    if (!candidate.equals(served) && Math.abs(candidate.gstPaise() - served.gstPaise()) > 1) {   // (3)!
                        s[1]++; log.add("diff " + o.id() + ": legacy " + served + " new " + candidate);
                    }
                } catch (RuntimeException e) { s[1]++; log.add("new failed in shadow for " + o.id() + ": " + e.getMessage()); }
                return served;
            }
            int[] n = newStats.computeIfAbsent(o.segment(), k -> new int[2]);
            n[0]++;
            try { return modern.apply(o); }
            catch (RuntimeException e) {
                n[1]++;
                log.add("new failed for " + o.id() + ", served legacy: " + e.getMessage());
                if (n[0] >= 4 && n[1] * 10 > n[0]) { modes.put(o.segment(), Mode.SHADOW); log.add("auto-rollback " + o.segment() + " to SHADOW"); }   // (5)!
                return fromLegacy(legacy.apply(o));
            }
        }

        String promote(String segment, int minSamples) {                                                // (4)!
            int[] s = shadowStats.getOrDefault(segment, new int[2]);
            if (s[0] < minSamples) return segment + " stays SHADOW: only " + s[0] + " comparisons";
            if (s[1] > 0) return segment + " stays SHADOW: " + s[1] + " unexplained difference(s)";
            modes.put(segment, Mode.NEW);
            newStats.remove(segment);
            return segment + " promoted to NEW after " + s[0] + " clean comparisons";
        }
    }

    public static void main(String[] args) {
        Function<Order, String> legacy = o -> String.format("INV%-8s%09d%07d", o.id(), o.netPaise(), o.netPaise() * 5 / 100);
        boolean[] b2bBug = {true};
        boolean[] newOutage = {false};
        Function<Order, Invoice> modern = o -> {
            if (newOutage[0]) throw new IllegalStateException("invoice-service 503");
            long gst = Math.round(o.netPaise() * 0.05);
            if (o.segment().equals("B2B") && b2bBug[0]) gst = o.netPaise() * 18 / 100;      // wrong slab for business customers
            return new Invoice(o.id(), o.netPaise(), gst);
        };
        Migrator m = new Migrator(legacy, modern);
        m.modes.put("B2C", Mode.SHADOW);
        m.modes.put("B2B", Mode.SHADOW);
        for (int i = 1; i <= 6; i++) { m.invoice(new Order("C" + i, "B2C", 10_000 + i * 1_001)); m.invoice(new Order("B" + i, "B2B", 50_000)); }
        System.out.println(m.promote("B2C", 5));
        System.out.println(m.promote("B2B", 5));
        newOutage[0] = true;
        for (int i = 7; i <= 10; i++) m.invoice(new Order("C" + i, "B2C", 12_000));
        System.out.println("modes: " + m.modes);
        m.log.stream().limit(2).forEach(l -> System.out.println("  " + l));
        System.out.println("  ... " + (m.log.size() - 2) + " more log lines; last: " + m.log.get(m.log.size() - 1));
    }
}
