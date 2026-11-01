import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The object pool lifecycle: create lazily up to a max, borrow (validate), use, reset, return; wait or fail when exhausted. */
public class PoolLifecycleDemo {

    static final class Pool<T> {
        private final Deque<T> idle = new ArrayDeque<>();
        private final Semaphore permits;                                                   // (1)!
        private final Supplier<T> factory; private final Predicate<T> valid; private final Consumer<T> reset;
        int created;

        Pool(int max, Supplier<T> factory, Predicate<T> valid, Consumer<T> reset) {
            this.permits = new Semaphore(max, true); this.factory = factory; this.valid = valid; this.reset = reset;
        }

        T borrow(long timeoutMs) throws InterruptedException {
            if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS))                    // (2)!
                throw new IllegalStateException("pool exhausted after " + timeoutMs + "ms");
            synchronized (this) {
                while (!idle.isEmpty()) {
                    T t = idle.pop();
                    if (valid.test(t)) return t;                                          // (3)!
                }
                created++;
                return factory.get();
            }
        }

        void giveBack(T t) {
            reset.accept(t);                                                              // (4)!
            synchronized (this) { idle.push(t); }
            permits.release();
        }
    }

    static final class Scanner { boolean healthy = true; String lastSku; }

    public static void main(String[] args) throws InterruptedException {
        Pool<Scanner> scanners = new Pool<>(2, Scanner::new, s -> s.healthy, s -> s.lastSku = null);
        Scanner a = scanners.borrow(10); a.lastSku = "MILK";
        Scanner b = scanners.borrow(10);
        try { scanners.borrow(50); } catch (IllegalStateException e) { System.out.println("third borrow: " + e.getMessage()); }
        scanners.giveBack(a);
        Scanner again = scanners.borrow(10);
        System.out.println("reused the returned scanner: " + (again == a) + ", state reset: " + (again.lastSku == null));
        again.healthy = false;
        scanners.giveBack(b); scanners.giveBack(again);                                   // broken one is on top of the idle stack
        Scanner next = scanners.borrow(10);
        System.out.println("broken scanner skipped, got healthy one: " + next.healthy + ", total created: " + scanners.created);
    }
}
