/**
 * Monomorphic, bimorphic and megamorphic call sites. HotSpot profiles each call site; if it has seen one or two
 * receiver types it inlines them behind a cheap type check, with more it falls back to virtual dispatch and can't
 * inline. Results go to stdout; timings (machine-dependent) go to stderr.
 */
public class CallSiteShapesDemo {

    interface FeeRule { long fee(long paise); }                                       // (1)!
    static final class Flat implements FeeRule { public long fee(long p) { return 2_500; } }
    static final class Percent implements FeeRule { public long fee(long p) { return p / 50; } }
    static final class Capped implements FeeRule { public long fee(long p) { return Math.min(p / 40, 4_000); } }
    static final class Free implements FeeRule { public long fee(long p) { return 0; } }

    // Three identical loops so that each has its own call site and its own type profile.   // (2)!
    static long mono(FeeRule[] r, int n) { long s = 0; for (int i = 0; i < n; i++) s += r[i & (r.length - 1)].fee(i); return s; }
    static long bi(FeeRule[] r, int n) { long s = 0; for (int i = 0; i < n; i++) s += r[i & (r.length - 1)].fee(i); return s; }
    static long mega(FeeRule[] r, int n) { long s = 0; for (int i = 0; i < n; i++) s += r[i & (r.length - 1)].fee(i); return s; }

    interface Loop { long run(FeeRule[] r, int n); }

    static long measure(String label, Loop loop, FeeRule[] rules) {
        int n = 20_000_000;
        for (int warm = 0; warm < 5; warm++) loop.run(rules, n);                          // (3)!
        long start = System.nanoTime();
        long result = loop.run(rules, n);
        System.err.printf("%-12s %6.2f ns/call%n", label, (System.nanoTime() - start) / (double) n);
        return result;
    }

    public static void main(String[] args) {
        long m = measure("monomorphic", CallSiteShapesDemo::mono, new FeeRule[] {new Percent()});
        long b = measure("bimorphic", CallSiteShapesDemo::bi, new FeeRule[] {new Percent(), new Flat()});
        long g = measure("megamorphic", CallSiteShapesDemo::mega, new FeeRule[] {new Percent(), new Flat(), new Capped(), new Free()});
        System.out.println("checksums: " + m + ", " + b + ", " + g);
        System.out.println("timings per call are printed to stderr; they vary by machine and JVM");
    }
}
