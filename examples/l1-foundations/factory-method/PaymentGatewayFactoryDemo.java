import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production shape: a factory that builds payment-gateway clients from configuration.
 * - validates config and fails fast with a clear message
 * - refuses live gateways outside production and sandbox gateways in production
 * - caches one client per merchant (clients are expensive: TLS, connection pools)
 */
public class PaymentGatewayFactoryDemo {

    interface PaymentGateway { String charge(String orderId, long cents); }

    record SandboxGateway(String merchantId) implements PaymentGateway {
        public String charge(String orderId, long cents) { return "SANDBOX ok " + orderId + " " + cents + " (" + merchantId + ")"; }
    }

    record CardNetworkGateway(String merchantId, String endpoint, int timeoutMs) implements PaymentGateway {
        public String charge(String orderId, long cents) { return "LIVE ok " + orderId + " " + cents + " via " + endpoint + " timeout=" + timeoutMs + "ms"; }
    }

    record UpiGateway(String merchantId, String vpa) implements PaymentGateway {
        public String charge(String orderId, long cents) { return "UPI collect " + orderId + " " + cents + " to " + vpa; }
    }

    static final class GatewayFactory {
        private final String environment;
        private final Map<String, String> config;
        private final Map<String, PaymentGateway> cache = new ConcurrentHashMap<>();
        final AtomicInteger created = new AtomicInteger();

        GatewayFactory(String environment, Map<String, String> config) {
            this.environment = environment;
            this.config = Map.copyOf(config);
        }

        /** The factory method. Same merchant -> same client instance. */
        PaymentGateway forMerchant(String merchantId) {
            return cache.computeIfAbsent(merchantId, this::create);                       // (1)!
        }

        private PaymentGateway create(String merchantId) {
            String type = required(merchantId, "type");
            boolean prod = environment.equals("prod");
            PaymentGateway gateway = switch (type) {
                case "sandbox" -> {
                    if (prod) throw new IllegalStateException(merchantId + ": sandbox gateway is not allowed in prod");   // (2)!
                    yield new SandboxGateway(merchantId);
                }
                case "card" -> {
                    if (!prod) throw new IllegalStateException(merchantId + ": live card gateway is only allowed in prod");
                    int timeout = Integer.parseInt(config.getOrDefault(merchantId + ".timeoutMs", "3000"));
                    if (timeout < 100 || timeout > 30_000) throw new IllegalArgumentException(merchantId + ": timeoutMs must be 100..30000");
                    yield new CardNetworkGateway(merchantId, required(merchantId, "endpoint"), timeout);
                }
                case "upi" -> new UpiGateway(merchantId, required(merchantId, "vpa"));
                default -> throw new IllegalArgumentException(merchantId + ": unknown gateway type '" + type + "'");
            };
            created.incrementAndGet();
            return gateway;
        }

        private String required(String merchantId, String key) {                        // (3)!
            String value = config.get(merchantId + "." + key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException(merchantId + ": missing config '" + merchantId + "." + key + "'");
            return value;
        }
    }

    public static void main(String[] args) {
        Map<String, String> config = Map.of(
                "pune-01.type", "card", "pune-01.endpoint", "https://cards.example/v2", "pune-01.timeoutMs", "2500",
                "pune-02.type", "upi", "pune-02.vpa", "freshcart.pune@bank",
                "dev-01.type", "sandbox",
                "bad-01.type", "card");

        GatewayFactory prod = new GatewayFactory("prod", config);
        System.out.println(prod.forMerchant("pune-01").charge("FC-3001", 4_250));
        System.out.println(prod.forMerchant("pune-02").charge("FC-3002", 1_999));
        System.out.println("cached: " + (prod.forMerchant("pune-01") == prod.forMerchant("pune-01")) + ", clients created: " + prod.created.get());

        for (String merchant : new String[] {"dev-01", "bad-01", "nope-99"}) {
            try { prod.forMerchant(merchant); } catch (RuntimeException e) { System.out.println("rejected: " + e.getMessage()); }
        }
        System.out.println(new GatewayFactory("staging", config).forMerchant("dev-01").charge("FC-T1", 100));
    }
}
