import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CircuitBreakerDecoratorTest {

    private final Instant[] now = {Instant.parse("2026-11-02T10:00:00Z")};

    private ResilientClientDemo.CircuitBreaker breaker(ResilientClientDemo.InventoryClient inner) {
        return new ResilientClientDemo.CircuitBreaker(inner, 2, Duration.ofSeconds(30), () -> now[0]);
    }

    @Test
    void opensAfterConsecutiveFailuresAndFailsFastWithoutCallingTheRemote() {
        var remote = new ResilientClientDemo.RemoteInventoryClient("down");
        var cb = breaker(remote);
        assertThrows(ResilientClientDemo.RemoteUnavailable.class, () -> cb.available("s", "k"));
        assertThrows(ResilientClientDemo.RemoteUnavailable.class, () -> cb.available("s", "k"));
        assertThrows(ResilientClientDemo.CircuitOpen.class, () -> cb.available("s", "k"));
        assertEquals(2, remote.calls);
    }

    @Test
    void letsATrialCallThroughAfterTheCoolDownAndClosesOnSuccess() {
        var remote = new ResilientClientDemo.RemoteInventoryClient("down", "down", "ok");
        var cb = breaker(remote);
        for (int i = 0; i < 2; i++) assertThrows(ResilientClientDemo.RemoteUnavailable.class, () -> cb.available("s", "k"));
        now[0] = now[0].plusSeconds(31);
        assertEquals(12, cb.available("s", "k"));
        assertEquals(12, cb.available("s", "k"));
    }

    @Test
    void retryDecoratorRetriesOnlyUpToItsLimit() {
        var remote = new ResilientClientDemo.RemoteInventoryClient("down", "down", "down");
        var retry = new ResilientClientDemo.Retry(remote, 2);
        assertThrows(ResilientClientDemo.RemoteUnavailable.class, () -> retry.available("s", "k"));
        assertEquals(2, remote.calls);
    }
}
