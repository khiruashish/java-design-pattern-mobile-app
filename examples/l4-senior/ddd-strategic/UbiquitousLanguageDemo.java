import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/** Ubiquitous language: code uses exactly the words domain experts use, so rules can be read back to them. */
public class UbiquitousLanguageDemo {

    // ---- Before: technical names. What is flag2? What does process() do? ----
    static final class DataManager {                                                    // (1)!
        boolean process(Map<String, Object> rec, LocalDate d, LocalTime t) {
            boolean flag2 = (Boolean) rec.get("f2");
            if (flag2) return false;
            return d.isAfter(LocalDate.of(2026, 11, 1)) || t.isBefore(LocalTime.of(22, 0));
        }
    }

    // ---- After: the same rule in DairyDay's language. ----
    static final class Subscription {                                                   // (2)!
        private final boolean paused;
        Subscription(boolean paused) { this.paused = paused; }
        static final LocalTime SKIP_CUTOFF = LocalTime.of(22, 0);
        boolean canSkip(LocalDate deliveryDay, LocalDate today, LocalTime now) {
            if (paused) return false;
            boolean deliveryIsTomorrow = deliveryDay.equals(today.plusDays(1));
            return !deliveryIsTomorrow || now.isBefore(SKIP_CUTOFF);
        }
    }

    static final Map<String, String> GLOSSARY = Map.of(                                 // (3)!
            "subscription", "a standing order for daily milk deliveries",
            "skip", "cancel one day's delivery; allowed until the skip cutoff",
            "skip cutoff", "22:00 on the day before delivery",
            "pause", "stop deliveries for a date range, at most 30 days");

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 1);
        boolean before = new DataManager().process(Map.of("f2", false), today.plusDays(1), LocalTime.of(21, 30));
        boolean after = new Subscription(false).canSkip(today.plusDays(1), today, LocalTime.of(21, 30));
        System.out.println("same answer at 21:30: " + (before == after) + " (" + after + ")");
        System.out.println("at 22:15: " + new Subscription(false).canSkip(today.plusDays(1), today, LocalTime.of(22, 15)));
        Set<String> used = Set.of("subscription", "skip", "skip cutoff");
        System.out.println("terms used in code that are in the glossary: " + GLOSSARY.keySet().containsAll(used));
        System.out.println("skip cutoff means: " + GLOSSARY.get("skip cutoff"));
    }
}
