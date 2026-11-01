import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Tell, Don't Ask: the slot decides if it can be booked, atomically. */
public class TellDontAskDemo {

    // ===================== ASK: caller reads state, decides, then writes =====================
    static final class SlotAsk {
        private int booked;
        private final int capacity;
        SlotAsk(int capacity) { this.capacity = capacity; }
        int getBooked() { return booked; }
        int getCapacity() { return capacity; }
        void setBooked(int booked) { this.booked = booked; }
    }

    static boolean bookAsk(SlotAsk slot) {                                                 // (1)!
        if (slot.getBooked() < slot.getCapacity()) {     // check...
            slot.setBooked(slot.getBooked() + 1);         // ...then act: another thread can run in between
            return true;
        }
        return false;
    }

    // ===================== TELL: the object owns the decision =====================
    static final class Slot {
        private final int capacity;
        private final AtomicInteger booked = new AtomicInteger();
        private final List<String> events = new ArrayList<>();
        Slot(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
            this.capacity = capacity;
        }

        /** Books one place if available. Safe to call from many threads. */
        boolean tryBook() {                                                                 // (2)!
            int current;
            do {
                current = booked.get();
                if (current >= capacity) return false;
            } while (!booked.compareAndSet(current, current + 1));
            return true;
        }

        int booked() { return booked.get(); }
    }

    static int runConcurrently(int customers, java.util.function.BooleanSupplier book) throws InterruptedException {
        AtomicInteger successes = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < customers; i++) pool.submit(() -> { if (book.getAsBoolean()) successes.incrementAndGet(); });
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        return successes.get();
    }

    public static void main(String[] args) throws InterruptedException {
        // Two customers hit the last place at the same moment. Replay the interleaving step by step:
        SlotAsk ask = new SlotAsk(20);
        ask.setBooked(19);
        boolean aliceSeesRoom = ask.getBooked() < ask.getCapacity();   // Alice checks: 19 < 20
        boolean bobSeesRoom = ask.getBooked() < ask.getCapacity();     // Bob checks:   19 < 20
        if (aliceSeesRoom) ask.setBooked(ask.getBooked() + 1);         // Alice writes 20
        if (bobSeesRoom) ask.setBooked(ask.getBooked() + 1);           // Bob writes 21
        System.out.println("ask style:  last place sold twice, booked = " + ask.getBooked() + "/" + ask.getCapacity());   // (3)!

        boolean tellAlwaysCorrect = true;
        for (int run = 0; run < 50; run++) {
            Slot tell = new Slot(20);
            int ok = runConcurrently(200, tell::tryBook);
            tellAlwaysCorrect &= ok == 20 && tell.booked() == 20;
        }
        System.out.println("tell style: exactly 20 of 200 booked in all 50 runs = " + tellAlwaysCorrect);
    }
}
