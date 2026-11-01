import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerCallTemplateTest {

    private final List<Duration> slept = new ArrayList<>();
    private final PartnerCallTemplateDemo.Sleeper sleeper = slept::add;
    private final PartnerCallTemplateDemo.Booking booking = new PartnerCallTemplateDemo.Booking("o1", "411001");

    @Test
    void retriesTransientErrorsWithDoublingBackoff() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper, "503", "503", "QD-1");
        assertEquals("booking QD-1 for o1", call.execute(booking));
        assertEquals(List.of(Duration.ofMillis(200), Duration.ofMillis(400)), slept);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper, "422", "QD-never");
        assertThrows(PartnerCallTemplateDemo.PartnerException.class, () -> call.execute(booking));
        assertEquals(List.of(), slept);
        assertEquals(1, call.metrics.get("quickdrop.book.failed").sum());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper, "503", "503", "503", "QD-late");
        var e = assertThrows(PartnerCallTemplateDemo.PartnerException.class, () -> call.execute(booking));
        assertEquals("quickdrop book failed after 3 attempt(s): HTTP 503", e.getMessage());
    }

    @Test
    void validationRunsBeforeAnyCall() {
        var call = new PartnerCallTemplateDemo.QuickDropBooking(sleeper);   // no scripted responses: a call would fail
        assertThrows(IllegalArgumentException.class, () -> call.execute(new PartnerCallTemplateDemo.Booking("o2", "12")));
    }
}
