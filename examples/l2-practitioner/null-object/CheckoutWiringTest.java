import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckoutWiringTest {

    private final Map<String, Object> beans = Map.of(
            "payments", (CheckoutWiringDemo.PaymentGateway) (id, p) -> "PAY-" + id,
            "loyalty", new CheckoutWiringDemo.Loyalty() {
                public long pointsFor(String c, long p) { return p / 10_000; }
                public boolean degraded() { return false; }
            });

    @Test
    void disabledFeaturesGetNullObjectsAndAreReported() {
        var c = CheckoutWiringDemo.wire(Map.of("loyalty.enabled", "true"), beans);
        assertEquals(List.of("sms", "metrics"), c.disabled());
        assertSame(CheckoutWiringDemo.NoMetrics.INSTANCE, c.metrics());
        var receipt = CheckoutWiringDemo.checkout(c, "FC-1", "u1", 50_000);
        assertEquals(5, receipt.points());
        assertFalse(receipt.loyaltyDegraded());
        assertEquals(1, ((CheckoutWiringDemo.DisabledNotifier) c.notifier()).suppressed.get());
    }

    @Test
    void loyaltySpecialCaseIsSafeButVisible() {
        var c = CheckoutWiringDemo.wire(Map.of(), beans);
        var receipt = CheckoutWiringDemo.checkout(c, "FC-2", "u2", 50_000);
        assertEquals(0, receipt.points());
        assertTrue(receipt.loyaltyDegraded());
        assertEquals(List.of("u2:50000"), ((CheckoutWiringDemo.LoyaltyUnavailable) c.loyalty()).owed);
    }

    @Test
    void requiredCollaboratorNeverGetsANullObject() {
        assertThrows(NullPointerException.class, () -> CheckoutWiringDemo.wire(Map.of(), Map.of()));
    }

    @Test
    void couponSpecialCases() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        assertSame(CouponCheckoutDemo.NoCoupon.INSTANCE, CouponCheckoutDemo.resolve("  ", today));
        assertInstanceOf(CouponCheckoutDemo.InvalidCoupon.class, CouponCheckoutDemo.resolve("NOPE", today));
        assertInstanceOf(CouponCheckoutDemo.ExpiredCoupon.class, CouponCheckoutDemo.resolve("diwali25", today));
        assertEquals(10_000, CouponCheckoutDemo.resolve("FRESH10", today).discount(250_000));
    }

    @Test
    void noOpNotifierSendsNothing() {
        List<String> outbox = new ArrayList<>();
        NoOpNotifierDemo.notifierFor("1", false, outbox).orderPlaced("X");
        assertTrue(outbox.isEmpty());
    }
}
