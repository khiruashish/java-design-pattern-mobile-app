import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** Singletonitis: global singletons everywhere make code hard to test and hide dependencies. Inject them instead. */
public class SingletonitisDemo {

    // ---- Before ----
    static final class SystemClockSingleton {                                // (1)!
        private static final SystemClockSingleton INSTANCE = new SystemClockSingleton();
        static SystemClockSingleton getInstance() { return INSTANCE; }
        LocalTime now() { return LocalTime.now(ZoneId.of("Asia/Kolkata")); }
    }
    static final class ConfigSingleton {
        private static final ConfigSingleton INSTANCE = new ConfigSingleton();
        static ConfigSingleton getInstance() { return INSTANCE; }
        int cutoffHour() { return 22; }
    }
    static final class SlotServiceBefore {
        boolean canBookTomorrow() {                                          // hidden dependencies: you can't see or replace them
            return SystemClockSingleton.getInstance().now().getHour() < ConfigSingleton.getInstance().cutoffHour();
        }
    }

    // ---- After ----
    record SlotService(Clock clock, int cutoffHour) {                       // (2)!
        boolean canBookTomorrow() { return LocalTime.now(clock).getHour() < cutoffHour; }
    }

    static Clock at(String isoInstant) { return Clock.fixed(Instant.parse(isoInstant), ZoneId.of("Asia/Kolkata")); }

    public static void main(String[] args) {
        boolean live = new SlotServiceBefore().canBookTomorrow();
        System.out.println("before: result depends on when you run it (got a boolean: " + (live || !live) + ")");
        System.out.println("after, 21:30 IST: " + new SlotService(at("2026-10-31T16:00:00Z"), 22).canBookTomorrow());   // (3)!
        System.out.println("after, 22:30 IST: " + new SlotService(at("2026-10-31T17:00:00Z"), 22).canBookTomorrow());
    }
}
