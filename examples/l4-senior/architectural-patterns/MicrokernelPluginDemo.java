import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Microkernel: a small core with a plugin contract; features arrive as plugins the core discovers and isolates. */
public class MicrokernelPluginDemo {

    interface PaymentPlugin {                                                                    // (1)!
        String id();
        boolean supports(String country);
        String pay(String orderId, long paise);
        default int apiVersion() { return 1; }
    }

    static final class Kernel {
        static final int SUPPORTED_API = 1;
        private final Map<String, PaymentPlugin> plugins = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        void register(PaymentPlugin p) {                                                         // (2)!
            if (p.apiVersion() != SUPPORTED_API) { log.add("refused " + p.id() + ": api v" + p.apiVersion()); return; }
            plugins.put(p.id(), p);
            log.add("loaded " + p.id());
        }

        List<String> methodsFor(String country) { return plugins.values().stream().filter(p -> p.supports(country)).map(PaymentPlugin::id).toList(); }

        String pay(String pluginId, String orderId, long paise) {
            PaymentPlugin p = plugins.get(pluginId);
            if (p == null) return "unknown method " + pluginId;
            try {
                return p.pay(orderId, paise);
            } catch (RuntimeException e) {                                                       // (3)!
                log.add(pluginId + " failed: " + e.getMessage());
                return "payment method unavailable, choose another";
            }
        }
    }

    record Upi() implements PaymentPlugin {
        public String id() { return "upi"; }
        public boolean supports(String c) { return c.equals("IN"); }
        public String pay(String o, long p) { return "UPI collect request for " + o; }
    }
    record Card() implements PaymentPlugin {
        public String id() { return "card"; }
        public boolean supports(String c) { return true; }
        public String pay(String o, long p) { throw new IllegalStateException("gateway timeout"); }
    }
    record CashOnDelivery() implements PaymentPlugin {
        public String id() { return "cod"; }
        public boolean supports(String c) { return c.equals("IN"); }
        public String pay(String o, long p) { return p > 200_000 ? "COD limit is Rs 2000" : "Pay rider on delivery for " + o; }
    }
    record FuturePlugin() implements PaymentPlugin {
        public String id() { return "wallet-v2"; }
        public boolean supports(String c) { return true; }
        public String pay(String o, long p) { return "?"; }
        public int apiVersion() { return 2; }
    }

    public static void main(String[] args) {
        Kernel kernel = new Kernel();
        for (PaymentPlugin p : List.of(new Upi(), new Card(), new CashOnDelivery(), new FuturePlugin())) kernel.register(p);
        System.out.println("methods in IN: " + kernel.methodsFor("IN") + ", in SG: " + kernel.methodsFor("SG"));
        System.out.println(kernel.pay("upi", "FC-1", 51_800));
        System.out.println(kernel.pay("card", "FC-2", 51_800));
        System.out.println(kernel.pay("cod", "FC-3", 300_000));
        System.out.println("kernel log: " + kernel.log);
    }
}
