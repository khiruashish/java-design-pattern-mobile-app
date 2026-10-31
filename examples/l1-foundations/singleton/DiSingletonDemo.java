import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** "Single instance" without the Singleton pattern: create one object in the composition root and inject it. */
public class DiSingletonDemo {

    // ===================== BEFORE: a static singleton hidden inside the service =====================
    static final class AuditLogStatic {
        static final AuditLogStatic INSTANCE = new AuditLogStatic();
        final List<String> lines = new ArrayList<>();
        void write(String line) { lines.add(Instant.now() + " " + line); }            // (1)!
    }

    static final class RefundServiceBefore {
        void refund(String orderId) { AuditLogStatic.INSTANCE.write("refund " + orderId); }   // (2)!
    }

    // ===================== AFTER: an interface, one instance, injected =====================
    interface AuditLog { void write(String line); }

    static final class ListAuditLog implements AuditLog {
        private final Clock clock;
        final List<String> lines = new ArrayList<>();
        ListAuditLog(Clock clock) { this.clock = clock; }
        public void write(String line) { lines.add(clock.instant() + " " + line); }
    }

    static final class RefundService {
        private final AuditLog audit;
        RefundService(AuditLog audit) { this.audit = audit; }                          // (3)!
        void refund(String orderId) { audit.write("refund " + orderId); }
    }

    static final class SupportDesk {
        private final AuditLog audit;
        SupportDesk(AuditLog audit) { this.audit = audit; }
        void escalate(String ticket) { audit.write("escalate " + ticket); }
    }

    public static void main(String[] args) {
        new RefundServiceBefore().refund("FC-1");
        System.out.println("before: static log has " + AuditLogStatic.INSTANCE.lines.size() + " line(s), timestamp is real time, can't be reset between tests");

        // Composition root: exactly ONE AuditLog, shared by everyone who needs it.
        ListAuditLog audit = new ListAuditLog(Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));  // (4)!
        RefundService refunds = new RefundService(audit);
        SupportDesk support = new SupportDesk(audit);

        refunds.refund("FC-2");
        support.escalate("T-77");
        audit.lines.forEach(line -> System.out.println("after:  " + line));
    }
}
