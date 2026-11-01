import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Minimal: DairyDay's "order by 10 pm for tomorrow's delivery" rule, tested at exact moments with a fixed Clock (a stub). */
public class SlotCutoffDemo {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    record DeliveryPlanner(Clock clock) {                                                              // (1)!
        LocalDate deliveryDate() {
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(IST));
            return now.toLocalTime().isBefore(LocalTime.of(22, 0)) ? now.toLocalDate().plusDays(1) : now.toLocalDate().plusDays(2);
        }
    }

    static Clock istAt(String localDateTime) {                                                        // (2)!
        return Clock.fixed(ZonedDateTime.parse(localDateTime + "+05:30[Asia/Kolkata]").toInstant(), IST);
    }

    public static void main(String[] args) {
        for (String t : new String[] {"2026-10-31T21:59:59", "2026-10-31T22:00:00", "2026-12-31T23:30:00"})
            System.out.println("ordered " + t + " -> delivered " + new DeliveryPlanner(istAt(t)).deliveryDate());
        System.out.println("same instant, UTC clock: " + new DeliveryPlanner(Clock.fixed(Instant.parse("2026-10-31T16:45:00Z"), ZoneId.of("UTC"))).deliveryDate());
    }
}
