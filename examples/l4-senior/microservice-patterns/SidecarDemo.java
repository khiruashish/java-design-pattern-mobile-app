import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sidecar: a helper process next to each service instance handles cross-cutting concerns (retries, mTLS identity,
 * metrics) for any language, without changing application code. Here the sidecar wraps the app's outbound calls.
 */
public class SidecarDemo {

    record Call(String target, String body, String identity) {}

    /** The application only knows how to make a plain call. */
    static String appLogic(Function<Call, String> outbound) {                                 // (1)!
        return outbound.apply(new Call("pricing", "price SKU MILK-1L", null));
    }

    /** The sidecar adds identity, retries and metrics around every outbound call. */
    static final class Sidecar implements Function<Call, String> {                            // (2)!
        private final Function<Call, String> network; private final String serviceIdentity;
        final List<String> metrics = new ArrayList<>();
        Sidecar(Function<Call, String> network, String serviceIdentity) { this.network = network; this.serviceIdentity = serviceIdentity; }
        public String apply(Call c) {
            Call withIdentity = new Call(c.target(), c.body(), "spiffe://freshcart/" + serviceIdentity);   // (3)!
            for (int attempt = 1; ; attempt++) {
                try {
                    String r = network.apply(withIdentity);
                    metrics.add(c.target() + " ok attempt=" + attempt);
                    return r;
                } catch (RuntimeException e) {
                    metrics.add(c.target() + " error attempt=" + attempt);
                    if (attempt == 3) throw e;
                }
            }
        }
    }

    public static void main(String[] args) {
        int[] calls = {0};
        Function<Call, String> flakyNetwork = c -> {
            if (++calls[0] == 1) throw new IllegalStateException("connection reset");
            if (c.identity() == null) return "403 no client identity";
            return "200 Rs 68 (caller " + c.identity() + ")";
        };
        System.out.println("without sidecar: " + safely(() -> appLogic(flakyNetwork)));
        calls[0] = 0;
        Sidecar sidecar = new Sidecar(flakyNetwork, "checkout");
        System.out.println("with sidecar:    " + appLogic(sidecar));
        System.out.println("sidecar metrics: " + sidecar.metrics);
    }

    static String safely(java.util.function.Supplier<String> s) { try { return s.get(); } catch (RuntimeException e) { return "failed: " + e.getMessage(); } }
}
