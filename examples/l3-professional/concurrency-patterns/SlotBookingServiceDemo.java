import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production: DairyDay's morning-slot booking opens at 9 pm and thousands of customers tap "book" at once.
 * - no overbooking: capacity is claimed with compare-and-set, never check-then-act
 * - retries are idempotent: the same request id books at most once and gets the same answer
 * - the payment-hold call downstream sees at most 8 concurrent requests (Semaphore bulkhead), with a timeout
 * - the availability page reads an immutable snapshot, never a half-updated map
 * - one virtual thread per request
 */
public class SlotBookingServiceDemo {

    sealed interface Result permits Booked, Full, Busy { }
    record Booked(String slot, int seat) implements Result { }
    record Full(String slot) implements Result { }
    record Busy(String reason) implements Result { }

    static final class Slot {
        final String id; final int capacity; final AtomicInteger taken = new AtomicInteger();
        Slot(String id, int capacity) { this.id = id; this.capacity = capacity; }
        int tryClaim() {                                                                                   // (1)!
            while (true) {
                int now = taken.get();
                if (now >= capacity) return -1;
                if (taken.compareAndSet(now, now + 1)) return now + 1;
            }
        }
        void release() { taken.decrementAndGet(); }
    }

    static final class BookingService {
        private final Map<String, Slot> slots = new LinkedHashMap<>();
        private final Map<String, CompletableFuture<Result>> byRequestId = new ConcurrentHashMap<>();
        private final Semaphore paymentBulkhead = new Semaphore(8);
        final AtomicInteger inFlight = new AtomicInteger(), maxInFlight = new AtomicInteger(), paymentCalls = new AtomicInteger();
        private final AtomicReference<Map<String, Integer>> availability = new AtomicReference<>(Map.of());

        BookingService(Map<String, Integer> capacities) { capacities.forEach((id, cap) -> slots.put(id, new Slot(id, cap))); publish(); }

        Result book(String requestId, String slotId) throws InterruptedException {
            CompletableFuture<Result> mine = new CompletableFuture<>();
            CompletableFuture<Result> existing = byRequestId.putIfAbsent(requestId, mine);                 // (2)!
            if (existing != null) return existing.join();                                                // a retry waits for the first attempt's answer
            try { Result r = doBook(slotId); mine.complete(r); return r; }
            catch (InterruptedException | RuntimeException e) { byRequestId.remove(requestId, mine); mine.completeExceptionally(e); throw e; }
        }

        private Result doBook(String slotId) throws InterruptedException {
            Slot slot = slots.get(slotId);
            int seat = slot.tryClaim();
            if (seat < 0) return new Full(slotId);
            if (!paymentBulkhead.tryAcquire(2, TimeUnit.SECONDS)) { slot.release(); return new Busy("payment queue full"); }   // (3)!
            try {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                paymentCalls.incrementAndGet();
                Thread.sleep(5);                                                                         // the payment-hold call
            } finally { inFlight.decrementAndGet(); paymentBulkhead.release(); }
            publish();
            return new Booked(slotId, seat);
        }

        private void publish() {                                                                       // (4)!
            Map<String, Integer> snap = new TreeMap<>();
            slots.values().forEach(s -> snap.put(s.id, s.capacity - s.taken.get()));
            availability.set(Map.copyOf(snap));
        }
        Map<String, Integer> availability() { publish(); return new TreeMap<>(availability.get()); }
    }

    public static void main(String[] args) throws Exception {
        BookingService service = new BookingService(Map.of("06-07", 50, "07-08", 50, "08-09", 50));
        List<String> slotIds = List.of("06-07", "07-08", "08-09");
        List<String> requestIds = new ArrayList<>();
        List<Future<Result>> futures = new ArrayList<>();
        try (ExecutorService perRequest = Executors.newVirtualThreadPerTaskExecutor()) {                // (5)!
            for (int i = 0; i < 600; i++) {
                int customer = i % 500;                                                                  // customers 0-99 retry once
                String slot = slotIds.get(customer % 3 == 0 ? 0 : customer % 5 == 0 ? 2 : 1);
                requestIds.add("cust-" + customer);
                futures.add(perRequest.submit(() -> service.book("cust-" + customer, slot)));
            }
        }
        Map<String, Result> answers = new java.util.HashMap<>();
        boolean consistent = true;
        for (int i = 0; i < futures.size(); i++) {
            Result r = futures.get(i).get(), earlier = answers.putIfAbsent(requestIds.get(i), r);
            if (earlier != null && !earlier.equals(r)) consistent = false;
        }
        Map<String, Integer> outcome = new TreeMap<>();
        answers.values().forEach(r -> outcome.merge(r.getClass().getSimpleName(), 1, Integer::sum));
        System.out.println("600 requests from 500 customers -> " + outcome + ", retries got the same answer: " + consistent);
        System.out.println("seats left: " + service.availability());
        System.out.println("payment-hold calls = seats booked: " + (service.paymentCalls.get() == 150)
                + ", max concurrent payment calls <= 8: " + (service.maxInFlight.get() <= 8));
    }
}
