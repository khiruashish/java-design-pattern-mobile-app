import java.util.ArrayList;
import java.util.List;

/** "Don't call us, we'll call you": the framework owns the loop and calls your code at the right moments. */
public class HollywoodDemo {

    // ===================== A tiny batch framework =====================
    abstract static class BatchJob<T> {
        final String run(List<T> items) {                                                  // (1)!
            onStart();
            int ok = 0, failed = 0;
            for (T item : items) {
                try { process(item); ok++; }
                catch (RuntimeException e) { failed++; onItemFailed(item, e); }           // (2)!
            }
            onFinish(ok, failed);
            return getClass().getSimpleName() + ": " + ok + " ok, " + failed + " failed";
        }
        protected void onStart() { }
        protected abstract void process(T item);                                         // (3)!
        protected void onItemFailed(T item, RuntimeException e) { }
        protected void onFinish(int ok, int failed) { }
    }

    // ===================== Your code: only the parts that are yours =====================
    static final class ExpireOldCarts extends BatchJob<String> {
        final List<String> log = new ArrayList<>();
        protected void process(String cartId) {
            if (cartId.isBlank()) throw new IllegalArgumentException("blank cart id");
            log.add("expired " + cartId);
        }
        @Override protected void onItemFailed(String item, RuntimeException e) { log.add("skipped '" + item + "': " + e.getMessage()); }
    }

    public static void main(String[] args) {
        ExpireOldCarts job = new ExpireOldCarts();
        System.out.println(job.run(List.of("cart-1", " ", "cart-3")));                    // (4)!
        job.log.forEach(l -> System.out.println("  " + l));
    }
}
