import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundServiceTest {

    private final RefundServiceDemo.InMemoryStore store = new RefundServiceDemo.InMemoryStore();
    private final List<String> gatewayCalls = new ArrayList<>();
    private final RefundServiceDemo.PaymentGateway gateway = (orderId, cents) -> {
        gatewayCalls.add(orderId + ":" + cents);
        return "RF-TEST";
    };

    private RefundServiceDemo.RefundService serviceAt(String now) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        return new RefundServiceDemo.RefundService(store, store, gateway, line -> { }, clock);
    }

    @Test
    void sameRefundKeyPaysOnlyOnce() {
        store.add(new RefundServiceDemo.DeliveredOrder("FC-1", 1_000, Instant.parse("2026-11-01T00:00:00Z")));
        var service = serviceAt("2026-11-01T12:00:00Z");
        service.refund("k-1", "FC-1", 500);
        service.refund("k-1", "FC-1", 500);
        assertEquals(List.of("FC-1:500"), gatewayCalls);
    }

    @Test
    void refundWindowEndsExactly48HoursAfterDelivery() {
        store.add(new RefundServiceDemo.DeliveredOrder("FC-2", 1_000, Instant.parse("2026-11-01T00:00:00Z")));
        serviceAt("2026-11-03T00:00:00Z").refund("k-2", "FC-2", 100);          // exactly at the deadline: allowed
        assertThrows(RefundServiceDemo.RefundRejected.class,
                () -> serviceAt("2026-11-03T00:00:01Z").refund("k-3", "FC-2", 100));
    }
}
