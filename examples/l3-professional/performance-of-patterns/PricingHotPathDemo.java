import java.util.List;

/**
 * FreshCart prices 2 million cart lines during a flash-sale repricing job. Is the Decorator chain of pricing rules
 * too slow? Measure before flattening: usually the chain is inlined and costs little, unless call sites go
 * megamorphic. Results to stdout, timings to stderr.
 */
public class PricingHotPathDemo {

    interface Price { long of(long paise); }
    record Base() implements Price { public long of(long p) { return p; } }
    record Gst(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v + v * 5 / 100; } }
    record Member(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v - v / 20; } }
    record FlashSale(Price inner, int percent) implements Price { public long of(long p) { long v = inner.of(p); return v - v * percent / 100; } }

    static long flat(long p) {                                                       // (1)!
        long v = p + p * 5 / 100;
        v = v - v / 20;
        return v - v * 10 / 100;
    }

    static long runChain(Price price, long[] lines) { long s = 0; for (long l : lines) s += price.of(l); return s; }
    static long runFlat(long[] lines) { long s = 0; for (long l : lines) s += flat(l); return s; }

    static long time(String label, java.util.function.LongSupplier work) {
        for (int i = 0; i < 10; i++) work.getAsLong();                               // (2)!
        long best = Long.MAX_VALUE, result = 0;
        for (int i = 0; i < 5; i++) {
            long t = System.nanoTime();
            result = work.getAsLong();
            best = Math.min(best, System.nanoTime() - t);
        }
        System.err.printf("%-22s best of 5: %6.2f ms%n", label, best / 1e6);
        return result;
    }

    public static void main(String[] args) {
        long[] lines = new long[2_000_000];
        for (int i = 0; i < lines.length; i++) lines[i] = 2_000 + (i * 37L) % 98_000;
        Price chain = new FlashSale(new Member(new Gst(new Base())), 10);

        long a = time("flat method", () -> runFlat(lines));
        long b = time("decorator chain", () -> runChain(chain, lines));
        List<Price> variants = List.of(chain, new Gst(new Base()), new Member(new Base()), new FlashSale(new Base(), 20));
        long c = time("4 chains, same site", () -> { long s = 0; for (Price p : variants) s += runChain(p, lines); return s; });   // (3)!

        System.out.println("flat and chain agree: " + (a == b) + ", total " + a);
        System.out.println("mixed run total: " + c);
    }
}
