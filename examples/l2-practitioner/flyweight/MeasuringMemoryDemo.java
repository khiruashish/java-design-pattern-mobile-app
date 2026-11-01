import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measure before you optimise. Estimate first (count objects x size), then measure the real heap.
 * (Exact per-object sizes need JOL - Java Object Layout; Runtime numbers are approximate, so we compare, not print raw bytes.)
 */
public class MeasuringMemoryDemo {

    record Style(String fontName, int size, String colour, byte[] glyphCache) { }
    record Label(String text, Style style) { }

    static long usedAfterGc() {
        for (int i = 0; i < 3; i++) System.gc();                                              // (1)!
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    static List<Label> build(int n, boolean share) {
        Map<String, Style> styles = new HashMap<>();
        List<Label> labels = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String key = (i % 4 == 0) ? "promo" : "normal";
            Style s = share ? styles.computeIfAbsent(key, k -> new Style("Inter", 12, k, new byte[4096]))
                            : new Style("Inter", 12, key, new byte[4096]);
            labels.add(new Label("SKU-" + i, s));
        }
        return labels;
    }

    public static void main(String[] args) {
        int n = 20_000;
        long estimateUnshared = (long) n * 4096, estimateShared = 2L * 4096;                  // (2)!
        System.out.println("estimate: unshared ~" + estimateUnshared / 1_000_000 + " MB of glyph caches, shared ~" + estimateShared / 1024 + " KB");

        long base = usedAfterGc();
        List<Label> unshared = build(n, false);
        long unsharedBytes = usedAfterGc() - base;
        unshared = null;

        base = usedAfterGc();
        List<Label> shared = build(n, true);
        long sharedBytes = usedAfterGc() - base;

        System.out.println("measured: shared version uses less than a quarter of the memory: " + (sharedBytes * 4 < unsharedBytes));   // (3)!
        System.out.println("labels still usable: " + shared.get(4).text() + " -> " + shared.get(4).style().colour());
    }
}
