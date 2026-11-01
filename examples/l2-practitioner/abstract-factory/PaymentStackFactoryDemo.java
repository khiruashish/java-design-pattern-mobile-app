import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Production-grade Abstract Factory: a payment "stack" (charge client, webhook verifier, refund client) per environment.
 * The whole point: the three products MUST come from the same environment. A sandbox charge with a live webhook
 * verifier means real webhooks are rejected (or worse, fake ones accepted).
 * - one factory per environment, created from validated configuration
 * - every product carries its environment; a consistency check runs at startup
 * - production refuses sandbox configuration
 */
public class PaymentStackFactoryDemo {

    enum Env { SANDBOX, LIVE }

    interface EnvTagged { Env env(); }
    interface ChargeClient extends EnvTagged { String charge(String orderId, long cents); }
    interface WebhookVerifier extends EnvTagged { boolean verify(String payload, String signature); }
    interface RefundClient extends EnvTagged { String refund(String paymentId, long cents); }

    interface PaymentStackFactory {                                                         // (1)!
        ChargeClient charges();
        WebhookVerifier webhooks();
        RefundClient refunds();
    }

    record Config(String endpoint, String apiKey, String webhookSecret) { }

    static final class PayLinkStackFactory implements PaymentStackFactory {
        private final Env env; private final Config config;

        PayLinkStackFactory(Env env, Config config) {
            List<String> problems = new ArrayList<>();                                     // (2)!
            if (config.apiKey() == null || config.apiKey().isBlank()) problems.add("apiKey missing");
            if (config.webhookSecret() == null || config.webhookSecret().length() < 16) problems.add("webhookSecret too short");
            boolean sandboxUrl = config.endpoint().contains("sandbox");
            if (env == Env.LIVE && sandboxUrl) problems.add("LIVE stack points at a sandbox endpoint");
            if (env == Env.SANDBOX && !sandboxUrl) problems.add("SANDBOX stack points at a live endpoint");
            if (env == Env.LIVE && config.apiKey() != null && config.apiKey().startsWith("test_")) problems.add("LIVE stack uses a test_ key");
            if (!problems.isEmpty()) throw new IllegalStateException("PayLink " + env + " config invalid: " + problems);
            this.env = env; this.config = config;
        }

        public ChargeClient charges() {
            return new ChargeClient() {
                public Env env() { return env; }
                public String charge(String id, long c) { return env + " charge " + id + " " + c + " via " + config.endpoint(); }
            };
        }
        public WebhookVerifier webhooks() {
            return new WebhookVerifier() {
                public Env env() { return env; }
                public boolean verify(String payload, String sig) { return sig.equals(Integer.toHexString((payload + config.webhookSecret()).hashCode())); }   // (3)!
            };
        }
        public RefundClient refunds() {
            return new RefundClient() {
                public Env env() { return env; }
                public String refund(String pid, long c) { return env + " refund " + pid + " " + c; }
            };
        }
    }

    /** What the application actually holds: one consistent stack. */
    record PaymentStack(ChargeClient charges, WebhookVerifier webhooks, RefundClient refunds) {
        PaymentStack {                                                                     // (4)!
            if (charges.env() != webhooks.env() || charges.env() != refunds.env())
                throw new IllegalStateException("mixed payment stack: " + charges.env() + "/" + webhooks.env() + "/" + refunds.env());
        }
        static PaymentStack from(PaymentStackFactory f) { return new PaymentStack(f.charges(), f.webhooks(), f.refunds()); }
    }

    static PaymentStack boot(String deployment, Map<String, String> secrets) {
        Env env = switch (deployment) { case "prod" -> Env.LIVE; case "staging", "dev" -> Env.SANDBOX; default -> throw new IllegalStateException("unknown deployment " + deployment); };
        Function<String, String> secret = k -> secrets.get(deployment + "." + k);
        return PaymentStack.from(new PayLinkStackFactory(env, new Config(secret.apply("endpoint"), secret.apply("apiKey"), secret.apply("webhookSecret"))));   // (5)!
    }

    public static void main(String[] args) {
        Map<String, String> secrets = Map.of(
                "prod.endpoint", "https://api.paylink.example", "prod.apiKey", "live_9f2c", "prod.webhookSecret", "whsec_live_0123456789",
                "staging.endpoint", "https://sandbox.paylink.example", "staging.apiKey", "test_1a2b", "staging.webhookSecret", "whsec_test_0123456789",
                "dev.endpoint", "https://api.paylink.example", "dev.apiKey", "test_1a2b", "dev.webhookSecret", "short");

        PaymentStack prod = boot("prod", secrets);
        System.out.println(prod.charges().charge("FC-2501", 49_950));
        String payload = "{\"payment\":\"plk_1\",\"status\":\"captured\"}";
        String goodSig = Integer.toHexString((payload + "whsec_live_0123456789").hashCode());
        String sandboxSig = Integer.toHexString((payload + "whsec_test_0123456789").hashCode());
        System.out.println("live webhook with live signature: " + prod.webhooks().verify(payload, goodSig));
        System.out.println("live webhook with sandbox signature: " + prod.webhooks().verify(payload, sandboxSig));
        System.out.println(boot("staging", secrets).refunds().refund("plk_1", 10_000));

        try { boot("dev", secrets); } catch (IllegalStateException e) { System.out.println("boot failed: " + e.getMessage()); }

        PaymentStackFactory live = new PayLinkStackFactory(Env.LIVE, new Config("https://api.paylink.example", "live_9f2c", "whsec_live_0123456789"));
        PaymentStackFactory sandbox = new PayLinkStackFactory(Env.SANDBOX, new Config("https://sandbox.paylink.example", "test_1a2b", "whsec_test_0123456789"));
        try { new PaymentStack(live.charges(), sandbox.webhooks(), live.refunds()); }
        catch (IllegalStateException e) { System.out.println("assembly refused: " + e.getMessage()); }
    }
}
