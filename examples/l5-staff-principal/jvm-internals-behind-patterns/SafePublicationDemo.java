import java.util.List;
import java.util.Map;

/**
 * Java Memory Model and safe publication: without a happens-before edge, another thread may see a reference to an
 * object before seeing its fields' values. Final fields, volatile, locks, static initialisers and concurrent
 * collections create those edges. Double-checked locking is only correct with volatile.
 */
public class SafePublicationDemo {

    /** Immutable: final fields are guaranteed visible once the constructor finishes (JLS 17.5). */
    record PriceList(Map<String, Long> prices) {                                                          // (1)!
        PriceList { prices = Map.copyOf(prices); }
    }

    static final class PriceListHolder {
        private volatile PriceList current;                                                                // (2)!
        private final Object lock = new Object();
        PriceList get() {
            PriceList p = current;                       // one volatile read on the fast path
            if (p == null) {
                synchronized (lock) {
                    p = current;
                    if (p == null) current = p = new PriceList(Map.of("MILK-1L", 6800L));                  // (3)!
                }
            }
            return p;
        }
        void replace(PriceList next) { current = next; }  // readers see either the old or the new list, never a half-built one
    }

    public static void main(String[] args) throws InterruptedException {
        PriceListHolder holder = new PriceListHolder();
        long[] seen = new long[4];
        List<Thread> readers = List.of(0, 1, 2, 3).stream().map(i -> new Thread(() -> seen[i] = holder.get().prices().get("MILK-1L"))).toList();
        readers.forEach(Thread::start);
        for (Thread t : readers) t.join();
        System.out.println("4 threads read: " + java.util.Arrays.toString(seen) + ", same instance: " + (holder.get() == holder.get()));
        holder.replace(new PriceList(Map.of("MILK-1L", 7000L)));
        System.out.println("after replace: " + holder.get().prices().get("MILK-1L"));
        System.out.println("rules: final fields, volatile, synchronized, static init, java.util.concurrent all publish safely");
    }
}
