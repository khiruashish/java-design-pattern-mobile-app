import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Realistic DI: one composition root, several "modules" (like Spring profiles) that bind different implementations per environment. */
public class EnvironmentWiringDemo {

    interface Payments { String charge(String orderId, long cents); }
    interface Sms { String send(String phone, String text); }
    interface Storage { String put(String key); }

    record Services(Payments payments, Sms sms, Storage storage) { }

    static final class CheckoutService {
        private final Payments payments; private final Sms sms; private final Storage storage;
        CheckoutService(Services s) { payments = s.payments(); sms = s.sms(); storage = s.storage(); }   // (1)!
        List<String> checkout(String orderId, long cents, String phone) {
            return List.of(payments.charge(orderId, cents), sms.send(phone, "Paid " + orderId), storage.put("invoices/" + orderId));
        }
    }

    // Modules: each returns a complete, consistent set of bindings
    static Services devModule() {                                                          // (2)!
        return new Services((id, c) -> "FAKE charge " + id, (p, t) -> "console SMS '" + t + "'", k -> "memory://" + k);
    }
    static Services stagingModule() {
        return new Services((id, c) -> "sandbox charge " + id, (p, t) -> "SMS to test numbers only", k -> "s3://freshcart-staging/" + k);
    }
    static Services prodModule() {
        return new Services((id, c) -> "LIVE charge " + id + " " + c, (p, t) -> "SMS to " + p, k -> "s3://freshcart-prod/" + k);
    }

    static final Map<String, java.util.function.Supplier<Services>> MODULES = Map.of(
            "dev", EnvironmentWiringDemo::devModule, "staging", EnvironmentWiringDemo::stagingModule, "prod", EnvironmentWiringDemo::prodModule);

    static CheckoutService wire(String env) {                                             // (3)!
        var module = MODULES.get(env);
        if (module == null) throw new IllegalStateException("unknown environment " + env);
        return new CheckoutService(module.get());
    }

    public static void main(String[] args) {
        for (String env : List.of("dev", "staging", "prod")) {
            System.out.println(env + ": " + wire(env).checkout("FC-2901", 49_950, "+919800000001"));
        }
        Function<Services, Services> withFakePayments = s -> new Services((id, c) -> "TEST charge " + id, s.sms(), s.storage());   // (4)!
        System.out.println("test override: " + new CheckoutService(withFakePayments.apply(devModule())).checkout("FC-2902", 100, "x").get(0));
    }
}
