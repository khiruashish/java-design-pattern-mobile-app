import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandProcessorTest {

    @Test
    void retriesTransientFailuresWithBackoff() {
        var p = new CommandProcessorDemo.Processor(4, 2);
        assertTrue(p.submit(new CommandProcessorDemo.RefundPayment("k1", "FC-1", 100)).startsWith("refunded"));
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200)), p.sleeps);
    }

    @Test
    void executesEachIdempotencyKeyOnce() {
        var p = new CommandProcessorDemo.Processor(1, 0);
        p.submit(new CommandProcessorDemo.RestockSku("k1", "MILK", 10));
        p.submit(new CommandProcessorDemo.RestockSku("k1", "MILK", 10));
        assertEquals(1, p.completed.size());
        assertTrue(p.audit.contains("k1 duplicate"));
    }

    @Test
    void permanentErrorsAreNotRetried() {
        var p = new CommandProcessorDemo.Processor(5, 0);
        assertEquals("rejected", p.submit(new CommandProcessorDemo.RefundPayment("k1", "FC-1", -5)));
        assertTrue(p.sleeps.isEmpty());
        assertEquals(List.of("k1"), p.deadLetters);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        var p = new CommandProcessorDemo.Processor(3, 99);
        assertEquals("dead-lettered", p.submit(new CommandProcessorDemo.RefundPayment("k1", "FC-1", 100)));
        assertEquals(2, p.sleeps.size());
    }
}
