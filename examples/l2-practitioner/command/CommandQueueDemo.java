import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Commands as data in a queue: the request is made now and executed later by a worker, possibly more than once. */
public class CommandQueueDemo {

    record SendInvoice(String idempotencyKey, String orderId, String email) { }               // (1)!

    static final Set<String> processed = new HashSet<>();
    static final List<String> sent = new ArrayList<>();

    static void handle(SendInvoice c) {
        if (!processed.add(c.idempotencyKey())) { System.out.println("  skip duplicate " + c.idempotencyKey()); return; }   // (2)!
        sent.add(c.orderId());
        System.out.println("  emailed invoice " + c.orderId() + " to " + c.email());
    }

    public static void main(String[] args) throws Exception {
        BlockingQueue<SendInvoice> queue = new LinkedBlockingQueue<>(100);                     // (3)!
        queue.put(new SendInvoice("inv-FC-7101", "FC-7101", "asha@mail"));
        queue.put(new SendInvoice("inv-FC-7102", "FC-7102", "ravi@mail"));
        queue.put(new SendInvoice("inv-FC-7101", "FC-7101", "asha@mail"));                     // retried by an impatient client
        System.out.println("checkout returned immediately; 3 commands queued");
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                SendInvoice c;
                while ((c = queue.poll(200, TimeUnit.MILLISECONDS)) != null) handle(c);       // (4)!
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        worker.join();
        System.out.println("invoices sent: " + sent);
    }
}
