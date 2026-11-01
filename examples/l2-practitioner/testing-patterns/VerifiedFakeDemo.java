import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Production: PayLink's payment gateway. Hundreds of tests use an in-memory FAKE instead of the real HTTP adapter.
 * A fake is only useful if it behaves like the real thing, so ONE contract suite runs against:
 *   - the real adapter (here talking to a simulated HTTP server; in CI, the provider's sandbox, nightly)
 *   - the fake used by every other test
 *   - a naive fake someone wrote quickly, which the contract catches
 * Contract: idempotency keys, declines for a magic card, no refund above the captured amount, error type for timeouts.
 */
public class VerifiedFakeDemo {

    sealed interface Result permits Approved, Declined { }
    record Approved(String paymentId, long paise) implements Result { }
    record Declined(String reason) implements Result { }
    static final class GatewayTimeout extends RuntimeException { GatewayTimeout() { super("gateway timeout"); } }

    interface PaymentGateway {
        Result charge(String idempotencyKey, String card, long paise);
        long refund(String paymentId, long paise);        // returns total refunded so far
    }

    // ---------- the real adapter, over a simulated HTTP API ----------
    static final class SimulatedPayLinkServer {                                                          // stands in for the provider's sandbox
        private final Map<String, String> byKey = new LinkedHashMap<>();
        private final Map<String, long[]> payments = new LinkedHashMap<>();   // id -> [captured, refunded]
        Map<String, String> post(String path, Map<String, String> body) {
            if (body.getOrDefault("card", "").equals("4000000000000119")) throw new GatewayTimeout();
            if (path.equals("/charges")) {
                String key = body.get("idempotency_key");
                if (byKey.containsKey(key)) return Map.of("status", "approved", "id", byKey.get(key), "amount", String.valueOf(payments.get(byKey.get(key))[0]));
                if (body.get("card").equals("4000000000000002")) return Map.of("status", "declined", "reason", "card_declined");
                String id = "pl_" + (payments.size() + 1);
                byKey.put(key, id); payments.put(id, new long[] {Long.parseLong(body.get("amount")), 0});
                return Map.of("status", "approved", "id", id, "amount", body.get("amount"));
            }
            long[] p = payments.get(body.get("payment_id"));
            long amount = Long.parseLong(body.get("amount"));
            if (p == null || p[1] + amount > p[0]) return Map.of("status", "error", "reason", "refund_exceeds_capture");
            p[1] += amount;
            return Map.of("status", "ok", "refunded", String.valueOf(p[1]));
        }
    }
    record PayLinkHttpAdapter(SimulatedPayLinkServer server) implements PaymentGateway {             // (1)!
        public Result charge(String key, String card, long paise) {
            Map<String, String> r = server.post("/charges", Map.of("idempotency_key", key, "card", card, "amount", String.valueOf(paise)));
            return r.get("status").equals("approved") ? new Approved(r.get("id"), Long.parseLong(r.get("amount"))) : new Declined(r.get("reason"));
        }
        public long refund(String id, long paise) {
            Map<String, String> r = server.post("/refunds", Map.of("payment_id", id, "amount", String.valueOf(paise)));
            if (r.get("status").equals("error")) throw new IllegalArgumentException(r.get("reason"));
            return Long.parseLong(r.get("refunded"));
        }
    }

    // ---------- the fake every other test uses ----------
    static final class FakePaymentGateway implements PaymentGateway {                                 // (2)!
        public static final String DECLINE_CARD = "4000000000000002", TIMEOUT_CARD = "4000000000000119";
        private final Map<String, Approved> byKey = new LinkedHashMap<>();
        private final Map<String, long[]> payments = new LinkedHashMap<>();
        public Result charge(String key, String card, long paise) {
            if (card.equals(TIMEOUT_CARD)) throw new GatewayTimeout();
            if (byKey.containsKey(key)) return byKey.get(key);
            if (card.equals(DECLINE_CARD)) return new Declined("card_declined");
            Approved a = new Approved("fake_" + (payments.size() + 1), paise);
            byKey.put(key, a); payments.put(a.paymentId(), new long[] {paise, 0});
            return a;
        }
        public long refund(String id, long paise) {
            long[] p = payments.get(id);
            if (p == null || p[1] + paise > p[0]) throw new IllegalArgumentException("refund_exceeds_capture");
            return p[1] += paise;
        }
    }

    // ---------- a quick fake that "works" for happy-path tests ----------
    static final class NaiveFake implements PaymentGateway {
        int n;
        public Result charge(String key, String card, long paise) { return new Approved("naive_" + (++n), paise); }
        public long refund(String id, long paise) { return paise; }
    }

    // ---------- the contract, written once ----------
    record Contract(Supplier<PaymentGateway> factory) {                                                 // (3)!
        List<String> run() {
            List<String> failed = new ArrayList<>();
            check(failed, "approves a normal card", () -> factory.get().charge("k1", "4111111111111111", 64_900) instanceof Approved a && a.paise() == 64_900);
            check(failed, "same idempotency key returns the same payment", () -> {                    // (4)!
                PaymentGateway g = factory.get();
                Result first = g.charge("k2", "4111111111111111", 64_900), retry = g.charge("k2", "4111111111111111", 64_900);
                return first.equals(retry);
            });
            check(failed, "declines the test decline card", () -> factory.get().charge("k3", "4000000000000002", 100) instanceof Declined d && d.reason().equals("card_declined"));
            check(failed, "refund above capture is rejected", () -> {
                PaymentGateway g = factory.get();
                Approved a = (Approved) g.charge("k4", "4111111111111111", 10_000);
                g.refund(a.paymentId(), 6_000);
                try { g.refund(a.paymentId(), 6_000); return false; } catch (IllegalArgumentException e) { return true; }
            });
            check(failed, "timeout card throws GatewayTimeout", () -> {
                try { factory.get().charge("k5", "4000000000000119", 100); return false; } catch (GatewayTimeout e) { return true; }
            });
            return failed;
        }
        static void check(List<String> failed, String name, Supplier<Boolean> t) {
            try { if (!t.get()) failed.add(name); } catch (RuntimeException e) { failed.add(name + " (threw " + e.getClass().getSimpleName() + ")"); }
        }
    }

    public static void main(String[] args) {
        Map<String, Supplier<PaymentGateway>> impls = new LinkedHashMap<>();
        impls.put("PayLinkHttpAdapter (sandbox)", () -> new PayLinkHttpAdapter(new SimulatedPayLinkServer()));
        impls.put("FakePaymentGateway", FakePaymentGateway::new);
        impls.put("NaiveFake", NaiveFake::new);
        impls.forEach((name, f) -> {
            List<String> failed = new Contract(f).run();
            System.out.println(name + ": " + (failed.isEmpty() ? "5/5 contract checks pass" : (5 - failed.size()) + "/5, fails " + failed));
        });
        System.out.println("=> only fakes that pass the same contract as the real adapter may be used in other tests");   // (5)!
    }
}
