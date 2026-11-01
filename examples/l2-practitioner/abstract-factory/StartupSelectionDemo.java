import java.util.Map;
import java.util.function.Supplier;

/** Choose the concrete factory ONCE at startup from configuration, validate it, and inject it everywhere. */
public class StartupSelectionDemo {

    interface Storage { String put(String key); }
    interface Queue { String send(String msg); }
    interface InfraFactory { Storage storage(); Queue queue(); String name(); }

    record LocalInfra() implements InfraFactory {
        public Storage storage() { return k -> "file:/tmp/freshcart/" + k; }
        public Queue queue() { return m -> "in-memory queue <- " + m; }
        public String name() { return "local"; }
    }
    record CloudInfra(String region) implements InfraFactory {
        public Storage storage() { return k -> "s3://freshcart-" + region + "/" + k; }
        public Queue queue() { return m -> "sqs:" + region + " <- " + m; }
        public String name() { return "cloud(" + region + ")"; }
    }

    static final Map<String, Supplier<InfraFactory>> AVAILABLE = Map.of(                    // (1)!
            "local", LocalInfra::new,
            "cloud-mumbai", () -> new CloudInfra("ap-south-1"),
            "cloud-dubai", () -> new CloudInfra("me-central-1"));

    static InfraFactory select(Map<String, String> env) {                                   // (2)!
        String key = env.getOrDefault("FRESHCART_INFRA", "local");
        Supplier<InfraFactory> s = AVAILABLE.get(key);
        if (s == null) throw new IllegalStateException("FRESHCART_INFRA=" + key + " is not one of " + new java.util.TreeSet<>(AVAILABLE.keySet()));
        return s.get();
    }

    /** A service that receives the factory; it never reads configuration itself. */
    record InvoiceArchiver(InfraFactory infra) {                                            // (3)!
        String archive(String orderId) { return infra.storage().put("invoices/" + orderId + ".pdf") + " | " + infra.queue().send("archived " + orderId); }
    }

    public static void main(String[] args) {
        for (Map<String, String> env : java.util.List.of(Map.<String, String>of(), Map.of("FRESHCART_INFRA", "cloud-mumbai"))) {
            InfraFactory infra = select(env);
            System.out.println(infra.name() + ": " + new InvoiceArchiver(infra).archive("FC-2401"));
        }
        try { select(Map.of("FRESHCART_INFRA", "cloud-mars")); }
        catch (IllegalStateException e) { System.out.println("startup failed fast: " + e.getMessage()); }   // (4)!
    }
}
