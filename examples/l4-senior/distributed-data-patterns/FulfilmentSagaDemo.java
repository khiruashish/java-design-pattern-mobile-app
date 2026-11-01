import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Production-grade: an orchestrated order-fulfilment saga whose state survives restarts. Commands go out through
 * an outbox, replies are deduplicated by step, a step that takes too long counts as failed, and completed steps
 * are compensated in reverse order.
 */
public class FulfilmentSagaDemo {

    enum Step { RESERVE_STOCK, CHARGE_PAYMENT, BOOK_RIDER }
    static final Map<Step, String> COMPENSATION = Map.of(
            Step.RESERVE_STOCK, "ReleaseStock", Step.CHARGE_PAYMENT, "RefundPayment", Step.BOOK_RIDER, "CancelRider");

    record SagaState(String orderId, Step awaiting, long stepStartedAt, List<Step> completed, String status) {}
    record Reply(String orderId, Step step, boolean success) {}

    /** Stands in for a database table; survives orchestrator restarts. */
    static final class SagaStore { final Map<String, SagaState> rows = new TreeMap<>(); }
    static final class Outbox { final List<String> commands = new ArrayList<>(); }

    static final class Orchestrator {
        static final long STEP_TIMEOUT_MS = 60_000;
        private final SagaStore store; private final Outbox outbox;
        Orchestrator(SagaStore store, Outbox outbox) { this.store = store; this.outbox = outbox; }

        void start(String orderId, long now) { begin(new SagaState(orderId, null, now, List.of(), "RUNNING"), Step.RESERVE_STOCK, now); }

        String onReply(Reply r, long now) {
            SagaState s = store.rows.get(r.orderId());
            if (s != null && "CANCELLED".equals(s.status()) && r.success() && !s.completed().contains(r.step())) {
                outbox.commands.add(COMPENSATION.get(r.step()) + " " + r.orderId());      // it succeeded after we gave up: undo it
                return "late " + r.step() + " success for cancelled " + r.orderId() + ", compensating";
            }
            if (s == null || !"RUNNING".equals(s.status()) || s.awaiting() != r.step())                         // (1)!
                return "ignored " + r.step() + " reply for " + r.orderId() + " (saga " + (s == null ? "unknown" : s.status() + ", awaiting " + s.awaiting()) + ")";
            if (!r.success()) return compensate(s, r.step() + " failed");
            List<Step> done = new ArrayList<>(s.completed()); done.add(r.step());
            SagaState progressed = new SagaState(s.orderId(), null, now, List.copyOf(done), "RUNNING");
            if (r.step() == Step.BOOK_RIDER) { save(new SagaState(s.orderId(), null, now, progressed.completed(), "COMPLETED")); return r.orderId() + " COMPLETED"; }
            begin(progressed, Step.values()[r.step().ordinal() + 1], now);
            return r.orderId() + " " + r.step() + " done";
        }

        List<String> checkTimeouts(long now) {
            List<String> out = new ArrayList<>();
            for (SagaState s : List.copyOf(store.rows.values()))
                if ("RUNNING".equals(s.status()) && now - s.stepStartedAt() > STEP_TIMEOUT_MS)                     // (2)!
                    out.add(compensate(s, s.awaiting() + " timed out"));
            return out;
        }

        private void begin(SagaState s, Step step, long now) {
            save(new SagaState(s.orderId(), step, now, s.completed(), "RUNNING"));
            outbox.commands.add(step + " " + s.orderId());                                                    // (3)!
        }

        private String compensate(SagaState s, String reason) {
            List<Step> done = new ArrayList<>(s.completed());
            java.util.Collections.reverse(done);
            for (Step step : done) outbox.commands.add(COMPENSATION.get(step) + " " + s.orderId());           // (4)!
            save(new SagaState(s.orderId(), null, s.stepStartedAt(), s.completed(), "CANCELLED"));
            return s.orderId() + " CANCELLED: " + reason;
        }

        private void save(SagaState s) { store.rows.put(s.orderId(), s); }                                     // (5)!
    }

    public static void main(String[] args) {
        SagaStore store = new SagaStore();
        Outbox outbox = new Outbox();
        Orchestrator node1 = new Orchestrator(store, outbox);

        node1.start("FC-1", 0);
        System.out.println(node1.onReply(new Reply("FC-1", Step.RESERVE_STOCK, true), 1_000));
        System.out.println(node1.onReply(new Reply("FC-1", Step.RESERVE_STOCK, true), 1_500));   // redelivered reply
        System.out.println("-- node1 crashes; node2 takes over from the store");
        Orchestrator node2 = new Orchestrator(store, outbox);
        System.out.println(node2.onReply(new Reply("FC-1", Step.CHARGE_PAYMENT, true), 2_000));
        System.out.println(node2.onReply(new Reply("FC-1", Step.BOOK_RIDER, true), 3_000));

        node2.start("FC-2", 10_000);
        System.out.println(node2.onReply(new Reply("FC-2", Step.RESERVE_STOCK, true), 11_000));
        System.out.println(node2.onReply(new Reply("FC-2", Step.CHARGE_PAYMENT, true), 12_000));
        node2.checkTimeouts(60_000).forEach(System.out::println);
        node2.checkTimeouts(80_000).forEach(System.out::println);
        System.out.println(node2.onReply(new Reply("FC-2", Step.BOOK_RIDER, true), 90_000));      // the rider was booked after all

        System.out.println("outbox commands:");
        outbox.commands.forEach(c -> System.out.println("  " + c));
        System.out.println("saga rows: " + store.rows.values().stream().map(s -> s.orderId() + "=" + s.status() + s.completed()).toList());
    }
}
