import java.util.ArrayList;
import java.util.List;

/**
 * Plugin architecture: the host owns the lifecycle and the extension points; plugins declare which API version they
 * were built for and contribute to named hooks. The host rejects incompatible plugins, isolates failures, and stops
 * plugins in reverse start order.
 */
public class PluginArchitectureDemo {

    static final int HOST_API_MAJOR = 2;

    interface Plugin {
        String id();
        int apiMajor();                                                                            // (1)!
        void start(Host host);
        default void stop() {}
    }

    interface BasketHook { long adjust(long basketPaise); }

    static final class Host {
        private final List<BasketHook> basketHooks = new ArrayList<>();
        private final List<Plugin> started = new ArrayList<>();

        void registerBasketHook(BasketHook hook) { basketHooks.add(hook); }

        void install(Plugin plugin) {
            if (plugin.apiMajor() != HOST_API_MAJOR) {
                System.out.println("rejected " + plugin.id() + ": built for API " + plugin.apiMajor() + ", host is " + HOST_API_MAJOR);
                return;
            }
            try {
                plugin.start(this);
                started.add(plugin);
                System.out.println("started " + plugin.id());
            } catch (RuntimeException e) {                                                         // (2)!
                System.out.println("failed to start " + plugin.id() + ": " + e.getMessage() + " (host keeps running)");
            }
        }

        long priceBasket(long paise) {
            for (BasketHook hook : basketHooks) paise = hook.adjust(paise);
            return paise;
        }

        void shutdown() {
            for (int i = started.size() - 1; i >= 0; i--) {                                        // (3)!
                started.get(i).stop();
                System.out.println("stopped " + started.get(i).id());
            }
        }
    }

    record SimplePlugin(String id, int apiMajor, BasketHook hook, boolean failOnStart) implements Plugin {
        public void start(Host host) {
            if (failOnStart) throw new IllegalStateException("cannot reach config service");
            host.registerBasketHook(hook);
        }
    }

    public static void main(String[] args) {
        Host host = new Host();
        host.install(new SimplePlugin("greenvalley-organic-discount", 2, p -> p * 95 / 100, false));
        host.install(new SimplePlugin("dairyday-subscription", 1, p -> p - 2000, false));
        host.install(new SimplePlugin("festival-banner", 2, p -> p, true));
        host.install(new SimplePlugin("small-basket-fee", 2, p -> p < 50000 ? p + 2900 : p, false));
        System.out.println("basket Rs 400.00 -> Rs " + host.priceBasket(40000) / 100.0);
        host.shutdown();
    }
}
