import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Double-checked locking: lazy, with a lock only on the first call. `volatile` is essential. */
public class DclSingletonDemo {

    static final class PaymentGatewayClient {
        static final AtomicInteger constructions = new AtomicInteger();
        private static volatile PaymentGatewayClient instance;                         // (1)!

        private final String endpoint;

        private PaymentGatewayClient(String endpoint) {
            constructions.incrementAndGet();
            this.endpoint = endpoint;
        }

        static PaymentGatewayClient getInstance() {
            PaymentGatewayClient local = instance;                                     // (2)!
            if (local == null) {
                synchronized (PaymentGatewayClient.class) {
                    local = instance;
                    if (local == null) {                                               // (3)!
                        local = new PaymentGatewayClient(System.getProperty("pg.endpoint", "https://pg.example/v1"));
                        instance = local;
                    }
                }
            }
            return local;
        }

        String endpoint() { return endpoint; }
    }

    public static void main(String[] args) throws Exception {
        int threads = 64;
        Set<PaymentGatewayClient> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> { start.await(); seen.add(PaymentGatewayClient.getInstance()); return null; });
            }
            start.countDown();                                                         // (4)!
        }
        System.out.println("threads: " + threads);
        System.out.println("distinct instances seen: " + seen.size());
        System.out.println("constructor calls: " + PaymentGatewayClient.constructions.get());
        System.out.println("endpoint: " + PaymentGatewayClient.getInstance().endpoint());
    }
}
