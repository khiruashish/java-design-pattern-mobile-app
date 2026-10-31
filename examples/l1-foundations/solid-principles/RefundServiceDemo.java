import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Refunds with ISP (small role interfaces) and DIP (injected gateway, clock and audit log). */
public class RefundServiceDemo {

    record DeliveredOrder(String id, long paidInCents, Instant deliveredAt) { }

    // ---- ISP: three small interfaces instead of one fat "OrderStore" ----
    interface OrderLookup { Optional<DeliveredOrder> find(String orderId); }                 // (1)!
    interface RefundLedger { boolean alreadyRefunded(String refundKey); void record(String refundKey, String orderId, long cents); }
    interface PaymentGateway { String refund(String orderId, long cents); }                  // (2)!
    interface AuditLog { void write(String line); }

    static final class RefundRejected extends RuntimeException {
        RefundRejected(String message) { super(message); }
    }

    static final class RefundService {
        static final long REFUND_WINDOW_HOURS = 48;

        private final OrderLookup orders;
        private final RefundLedger ledger;
        private final PaymentGateway gateway;
        private final AuditLog audit;
        private final Clock clock;                                                            // (3)!

        RefundService(OrderLookup orders, RefundLedger ledger, PaymentGateway gateway, AuditLog audit, Clock clock) {
            this.orders = orders; this.ledger = ledger; this.gateway = gateway; this.audit = audit; this.clock = clock;
        }

        /** Idempotent: the same refundKey never pays twice. */
        String refund(String refundKey, String orderId, long cents) {                          // (4)!
            if (ledger.alreadyRefunded(refundKey)) {
                audit.write("refund_duplicate key=" + refundKey);
                return "already refunded";
            }
            DeliveredOrder order = orders.find(orderId).orElseThrow(() -> reject(orderId, "unknown order"));
            if (cents <= 0 || cents > order.paidInCents()) throw reject(orderId, "amount must be between 1 and " + order.paidInCents());
            Instant deadline = order.deliveredAt().plus(REFUND_WINDOW_HOURS, ChronoUnit.HOURS);
            if (clock.instant().isAfter(deadline)) throw reject(orderId, "refund window closed at " + deadline);

            String reference = gateway.refund(orderId, cents);
            ledger.record(refundKey, orderId, cents);
            audit.write("refund_ok order=" + orderId + " cents=" + cents + " ref=" + reference);
            return reference;
        }

        private RefundRejected reject(String orderId, String reason) {
            audit.write("refund_rejected order=" + orderId + " reason=\"" + reason + "\"");
            return new RefundRejected(reason);
        }
    }

    // ---- One in-memory class can implement several small roles ----
    static final class InMemoryStore implements OrderLookup, RefundLedger {                  // (5)!
        private final Map<String, DeliveredOrder> orders = new HashMap<>();
        private final Set<String> refundKeys = new HashSet<>();
        void add(DeliveredOrder o) { orders.put(o.id(), o); }
        public Optional<DeliveredOrder> find(String id) { return Optional.ofNullable(orders.get(id)); }
        public boolean alreadyRefunded(String key) { return refundKeys.contains(key); }
        public void record(String key, String orderId, long cents) { refundKeys.add(key); }
    }

    public static void main(String[] args) {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        InMemoryStore store = new InMemoryStore();
        store.add(new DeliveredOrder("FC-6001", 4_250, Instant.parse("2026-11-01T18:30:00Z")));
        store.add(new DeliveredOrder("FC-6002", 1_999, Instant.parse("2026-10-29T09:00:00Z")));

        List<String> auditLines = new ArrayList<>();
        int[] counter = {0};
        PaymentGateway gateway = (orderId, cents) -> "RF-" + (++counter[0]);
        RefundService service = new RefundService(store, store, gateway, auditLines::add, fixed);

        System.out.println("1: " + service.refund("req-1", "FC-6001", 1_000));
        System.out.println("2: " + service.refund("req-1", "FC-6001", 1_000));
        for (String[] bad : new String[][] {{"req-2", "FC-6001", "9999"}, {"req-3", "FC-6002", "500"}, {"req-4", "FC-9999", "100"}}) {
            try {
                service.refund(bad[0], bad[1], Long.parseLong(bad[2]));
            } catch (RefundRejected e) {
                System.out.println("rejected: " + e.getMessage());
            }
        }
        System.out.println("audit:");
        auditLines.forEach(line -> System.out.println("  " + line));
    }
}
