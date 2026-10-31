import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Active Object: an object with its own thread and request queue. Callers invoke methods that return futures;
 * the object processes requests one at a time, so its state needs no locks.
 */
public class ActiveObjectDemo {

    static final class RiderDispatcher implements AutoCloseable {
        private final ExecutorService thread = Executors.newSingleThreadExecutor();                      // (1)!
        private final Map<String, Integer> load = new LinkedHashMap<>();                                // touched only by that thread

        RiderDispatcher(List<String> riders) { riders.forEach(r -> load.put(r, 0)); }

        CompletableFuture<String> assign(String orderId) {                                              // (2)!
            return CompletableFuture.supplyAsync(() -> {
                String rider = load.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
                load.merge(rider, 1, Integer::sum);
                return orderId + " -> " + rider;
            }, thread);
        }
        CompletableFuture<Map<String, Integer>> snapshot() { return CompletableFuture.supplyAsync(() -> Map.copyOf(load), thread); }

        @Override public void close() { thread.close(); }                                              // (3)!
    }

    public static void main(String[] args) {
        try (RiderDispatcher dispatcher = new RiderDispatcher(List.of("Arjun", "Bina", "Chetan"));
             ExecutorService callers = Executors.newFixedThreadPool(6)) {
            List<CompletableFuture<String>> assignments = new ArrayList<>();
            for (int i = 1; i <= 30; i++) {
                String id = "FC-" + i;
                assignments.add(CompletableFuture.supplyAsync(() -> dispatcher.assign(id), callers).thenCompose(f -> f));
            }
            CompletableFuture.allOf(assignments.toArray(CompletableFuture[]::new)).join();
            System.out.println("30 orders from 6 caller threads, load per rider: " + new java.util.TreeMap<>(dispatcher.snapshot().join()));
        }
    }
}
