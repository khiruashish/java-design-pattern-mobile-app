import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * A "seam" (Feathers) is a place where you can change behaviour without editing the code under test.
 * Dependency Injection, Strategy and Decorator create seams; `new` and static calls inside a method remove them.
 */
public class SeamsDemo {

    interface Sms { void send(String phone, String text); }
    static final class RealSms implements Sms { public void send(String p, String t) { throw new IllegalStateException("would call the real SMS provider"); } }

    // ---------- before: hard-wired; can't test without sending a real SMS at the real time of day ----------
    static final class SlotReminderBefore {
        String remind(String phone) {
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));                                 // (1)!
            if (now.isAfter(LocalTime.of(21, 0))) return "too late, skipped";
            new RealSms().send(phone, "Your slot is tomorrow 8-10 am");
            return "sent";
        }
    }

    // ---------- after: the clock and the SMS sender are injected (seams) ----------
    record SlotReminder(Clock clock, Sms sms) {                                                       // (2)!
        String remind(String phone) {
            LocalTime now = LocalTime.now(clock);
            if (now.isAfter(LocalTime.of(21, 0))) return "too late, skipped";
            sms.send(phone, "Your slot is tomorrow 8-10 am");
            return "sent";
        }
    }

    /** A decorator is also a seam: wrap the real collaborator to record or fault-inject without changing it. */
    record RecordingSms(Sms inner, List<String> log) implements Sms {                                 // (3)!
        public void send(String p, String t) { log.add(p + ": " + t); inner.send(p, t); }
    }

    static Clock at(String isoInstant) { return Clock.fixed(Instant.parse(isoInstant), ZoneId.of("Asia/Kolkata")); }

    public static void main(String[] args) {
        String outcome;
        try { outcome = new SlotReminderBefore().remind("98450"); } catch (IllegalStateException e) { outcome = "tried the real SMS provider"; }
        System.out.println("before: untestable; outcome depends on the wall clock (" + (outcome.contains("SMS") || outcome.contains("skipped") ? "skipped or real SMS" : outcome) + ")");

        List<String> log = new ArrayList<>();
        Sms quiet = new RecordingSms((p, t) -> { }, log);
        System.out.println("after, 18:30 IST: " + new SlotReminder(at("2026-10-31T13:00:00Z"), quiet).remind("98450") + " " + log);
        System.out.println("after, 21:30 IST: " + new SlotReminder(at("2026-10-31T16:00:00Z"), quiet).remind("98450"));
    }
}
