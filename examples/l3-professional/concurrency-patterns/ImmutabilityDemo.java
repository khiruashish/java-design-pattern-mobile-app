import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Immutability: share snapshots that can't change, and replace them atomically, instead of mutating shared objects. */
public class ImmutabilityDemo {

    // ---------- before: a mutable price shared between threads ----------
    static final class MutablePrice {
        long mrpPaise = 10_000, salePaise = 9_000;                                                     // invariant: sale <= mrp
        void startUpdate(long mrp) { mrpPaise = mrp; }                                                   // (1)!
        void finishUpdate(long sale) { salePaise = sale; }
    }

    // ---------- after: an immutable snapshot published through an AtomicReference ----------
    record Price(long mrpPaise, long salePaise) {                                                        // (2)!
        Price { if (salePaise > mrpPaise) throw new IllegalArgumentException("sale above MRP"); }
    }
    static final AtomicReference<Price> CURRENT = new AtomicReference<>(new Price(10_000, 9_000));

    public static void main(String[] args) throws Exception {
        MutablePrice shared = new MutablePrice();
        shared.startUpdate(8_000);                                                                    // writer thread is here...
        System.out.println("before: a reader between the two writes sees mrp=" + shared.mrpPaise + " sale=" + shared.salePaise
                + " -> sale above MRP: " + (shared.salePaise > shared.mrpPaise));
        shared.finishUpdate(7_500);

        AtomicInteger inconsistent = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (int w = 0; w < 2; w++) pool.submit(() -> {
                for (int i = 0; i < 50_000; i++) CURRENT.set(new Price(10_000 + i % 100, 9_000 + i % 100));     // (3)!
            });
            for (int r = 0; r < 2; r++) pool.submit(() -> {
                for (int i = 0; i < 50_000; i++) { Price p = CURRENT.get(); if (p.salePaise() > p.mrpPaise()) inconsistent.incrementAndGet(); }
            });
        }
        System.out.println("after: 100,000 snapshot swaps and 100,000 reads, inconsistent reads = " + inconsistent.get());
        Price discounted = CURRENT.updateAndGet(p -> new Price(p.mrpPaise(), p.mrpPaise() * 80 / 100));
        System.out.println("atomic read-modify-write: " + discounted);
    }
}
