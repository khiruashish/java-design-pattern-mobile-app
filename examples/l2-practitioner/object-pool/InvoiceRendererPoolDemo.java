import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Realistic Object Pool: invoice PDF renderers are expensive to start (fonts, templates); pool a few, share them. */
public class InvoiceRendererPoolDemo {

    static final AtomicInteger STARTED = new AtomicInteger();

    static final class PdfRenderer {
        PdfRenderer() { STARTED.incrementAndGet(); busyWork(); }                           // (1)!
        String render(String orderId) { return "%PDF invoice " + orderId; }
        static void busyWork() { long x = 0; for (int i = 0; i < 2_000_000; i++) x += i; if (x == 42) System.out.print(""); }
    }

    static final class RendererPool {
        private final BlockingQueue<PdfRenderer> idle;
        RendererPool(int size) {
            idle = new ArrayBlockingQueue<>(size);
            for (int i = 0; i < size; i++) idle.add(new PdfRenderer());                  // (2)!
        }
        String render(String orderId) throws InterruptedException {
            PdfRenderer r = idle.take();
            try { return r.render(orderId); } finally { idle.add(r); }                    // (3)!
        }
    }

    public static void main(String[] args) throws Exception {
        RendererPool pool = new RendererPool(3);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<String>> invoices = new java.util.ArrayList<>();
        for (int i = 1; i <= 200; i++) { String id = "FC-" + (2700 + i); invoices.add(workers.submit(() -> pool.render(id))); }
        int done = 0; for (Future<String> f : invoices) if (f.get().startsWith("%PDF")) done++;
        workers.shutdown();
        System.out.println("invoices rendered: " + done + " by 8 worker threads");
        System.out.println("renderers started: " + STARTED.get() + " (without a pool it would be 200)");
    }
}
