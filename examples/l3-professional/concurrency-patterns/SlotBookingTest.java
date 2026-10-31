import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SlotBookingTest {

    /** Starts all callers at the same moment to maximise contention. */
    private static List<SlotBookingServiceDemo.Result> race(SlotBookingServiceDemo.BookingService s, List<String> requestIds, String slot) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<SlotBookingServiceDemo.Result>> fs = new ArrayList<>();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String id : requestIds) fs.add(ex.submit(() -> { go.await(); return s.book(id, slot); }));
            go.countDown();
        }
        List<SlotBookingServiceDemo.Result> out = new ArrayList<>();
        for (var f : fs) out.add(f.get());
        return out;
    }

    @RepeatedTest(5)
    void neverOverbooks() throws Exception {
        var service = new SlotBookingServiceDemo.BookingService(Map.of("S", 20));
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 300; i++) ids.add("r" + i);
        var results = race(service, ids, "S");
        assertEquals(20, results.stream().filter(r -> r instanceof SlotBookingServiceDemo.Booked).count());
        assertEquals(0, service.availability().get("S"));
        assertTrue(service.maxInFlight.get() <= 8);
    }

    @RepeatedTest(5)
    void concurrentRetriesBookOnce() throws Exception {
        var service = new SlotBookingServiceDemo.BookingService(Map.of("S", 5));
        var results = race(service, List.of("same", "same", "same", "same", "same", "same"), "S");
        assertEquals(1, results.stream().distinct().count());
        assertEquals(4, service.availability().get("S"));
        assertEquals(1, service.paymentCalls.get());
    }

    @Test
    void seatNumbersAreUniqueAndFullAfterCapacity() throws Exception {
        var service = new SlotBookingServiceDemo.BookingService(Map.of("S", 3));
        List<SlotBookingServiceDemo.Result> rs = new ArrayList<>();
        for (int i = 0; i < 4; i++) rs.add(service.book("r" + i, "S"));
        assertEquals(List.of(1, 2, 3), rs.subList(0, 3).stream().map(r -> ((SlotBookingServiceDemo.Booked) r).seat()).toList());
        assertInstanceOf(SlotBookingServiceDemo.Full.class, rs.get(3));
    }

    @Test
    void activeObjectSerialisesAccess() {
        try (var d = new ActiveObjectDemo.RiderDispatcher(List.of("A", "B"))) {
            List<java.util.concurrent.CompletableFuture<String>> fs = new ArrayList<>();
            for (int i = 0; i < 100; i++) fs.add(d.assign("O" + i));
            fs.forEach(java.util.concurrent.CompletableFuture::join);
            assertEquals(Map.of("A", 50, "B", 50), d.snapshot().join());
        }
    }
}
