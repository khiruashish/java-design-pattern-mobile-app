import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The five test doubles (Meszaros), hand-written so you can see what each one is for.
 * The code under test: CheckoutService, with four collaborators.
 */
public class TestDoublesDemo {

    interface PaymentGateway { boolean charge(String orderId, long paise); }
    interface OrderRepository { void save(String orderId, String status); Optional<String> status(String orderId); }
    interface Notifier { void send(String to, String text); }
    interface AuditLog { void record(String event); }

    record CheckoutService(PaymentGateway payments, OrderRepository orders, Notifier notifier, AuditLog audit, Clock clock) {
        String checkout(String orderId, String phone, long paise) {
            if (!payments.charge(orderId, paise)) { orders.save(orderId, "PAYMENT_FAILED"); return "declined"; }
            orders.save(orderId, "PAID");
            notifier.send(phone, "Order " + orderId + " paid at " + clock.instant());
            return "ok";
        }
    }

    // DUMMY: passed only to fill a parameter; never used by this test path.
    static final AuditLog DUMMY_AUDIT = e -> { throw new AssertionError("dummy must not be called"); };   // (1)!

    // STUB: returns canned answers to drive the code down a path.
    static PaymentGateway decliningStub() { return (id, p) -> false; }                                 // (2)!

    // FAKE: a working, simplified implementation (an in-memory repository).
    static final class InMemoryOrders implements OrderRepository {                                    // (3)!
        final Map<String, String> rows = new LinkedHashMap<>();
        public void save(String id, String s) { rows.put(id, s); }
        public Optional<String> status(String id) { return Optional.ofNullable(rows.get(id)); }
    }

    // SPY: records calls so the test can check them AFTER the act step.
    static final class SpyNotifier implements Notifier {                                              // (4)!
        final List<String> sent = new ArrayList<>();
        public void send(String to, String text) { sent.add(to + " | " + text); }
    }

    // MOCK: pre-programmed with EXPECTATIONS; it fails itself if they aren't met.
    static final class MockPayments implements PaymentGateway {                                      // (5)!
        private final String expectedOrder; private final long expectedPaise; private int calls;
        MockPayments(String order, long paise) { expectedOrder = order; expectedPaise = paise; }
        public boolean charge(String id, long p) {
            calls++;
            if (!id.equals(expectedOrder) || p != expectedPaise) throw new AssertionError("unexpected charge " + id + " " + p);
            return true;
        }
        void verify() { if (calls != 1) throw new AssertionError("expected exactly 1 charge, got " + calls); }
    }

    public static void main(String[] args) {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-31T10:15:00Z"), ZoneOffset.UTC);

        InMemoryOrders orders = new InMemoryOrders();
        var declined = new CheckoutService(decliningStub(), orders, new SpyNotifier(), DUMMY_AUDIT, fixed);
        System.out.println("stub declines -> " + declined.checkout("FC-1", "98450", 64_900) + ", saved status " + orders.status("FC-1").orElseThrow());

        MockPayments mock = new MockPayments("FC-2", 64_900);
        SpyNotifier spy = new SpyNotifier();
        var happy = new CheckoutService(mock, orders, spy, DUMMY_AUDIT, fixed);
        System.out.println("mock expects one charge -> " + happy.checkout("FC-2", "98450", 64_900));
        mock.verify();
        System.out.println("mock verified; spy saw " + spy.sent);
        System.out.println("fake now holds " + orders.rows);
    }
}
