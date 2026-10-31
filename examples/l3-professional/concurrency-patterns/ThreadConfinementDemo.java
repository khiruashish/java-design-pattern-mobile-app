import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thread confinement: give each thread its own copy of a non-thread-safe object (ThreadLocal), and clean up in pools. */
public class ThreadConfinementDemo {

    static final ThreadLocal<SimpleDateFormat> FORMAT = ThreadLocal.withInitial(() -> {                // (1)!
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    });

    static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    static String handle(String tenant, boolean cleanUp) {
        if (tenant != null) CURRENT_TENANT.set(tenant);
        try { return "tenant seen by handler: " + CURRENT_TENANT.get(); }
        finally { if (cleanUp) CURRENT_TENANT.remove(); }                                              // (2)!
    }

    public static void main(String[] args) throws Exception {
        Date when = Date.from(Instant.parse("2026-10-31T18:30:00Z"));
        try (ExecutorService two = Executors.newFixedThreadPool(2)) {
            System.out.println("ThreadLocal formatter: " + two.submit(() -> FORMAT.get().format(when)).get()
                    + ", separate instances per thread: " + (two.submit(FORMAT::get).get() != FORMAT.get()));
        }
        System.out.println("better: an immutable DateTimeFormatter needs no confinement: "
                + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(when.toInstant()));   // (3)!

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            System.out.println("leaky:   " + pool.submit(() -> handle("FreshCart", false)).get());
            System.out.println("leaky:   next request without a tenant -> " + pool.submit(() -> handle(null, false)).get() + "   <- leaked");
            pool.submit(() -> CURRENT_TENANT.remove()).get();
            System.out.println("cleaned: " + pool.submit(() -> handle("DairyDay", true)).get());
            System.out.println("cleaned: next request without a tenant -> " + pool.submit(() -> handle(null, true)).get());
        }
    }
}
