import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

/** Minimal: QuickDrop counts orders per second from many request threads. A plain long loses updates; LongAdder doesn't. */
public class OrderCounterDemo {

    static long plain;                                                                                   // (1)!
    static final LongAdder adder = new LongAdder();                                                    // (2)!

    public static void main(String[] args) {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int t = 0; t < 8; t++) pool.submit(() -> {
                for (int i = 0; i < 100_000; i++) { plain++; adder.increment(); }
            });
        }
        System.out.println("expected 800000, LongAdder counted " + adder.sum());
        System.out.println("plain long: never more than 800000 (" + (plain <= 800_000) + "), usually less, and no guarantee either way");
    }
}
