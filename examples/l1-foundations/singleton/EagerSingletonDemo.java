import java.util.Map;

/** Eager initialisation: the instance is created when the class is initialised. */
public class EagerSingletonDemo {

    static final class FeatureFlags {
        private static final FeatureFlags INSTANCE = new FeatureFlags();            // (1)!

        private final Map<String, Boolean> flags;

        private FeatureFlags() {                                                     // (2)!
            System.out.println("FeatureFlags created");
            flags = Map.of("express-delivery", true, "upi-payments", false);
        }

        static FeatureFlags getInstance() { return INSTANCE; }

        boolean isOn(String flag) {
            Boolean value = flags.get(flag);
            if (value == null) throw new IllegalArgumentException("unknown flag: " + flag);  // (3)!
            return value;
        }
    }

    public static void main(String[] args) {
        System.out.println("main started");
        FeatureFlags a = FeatureFlags.getInstance();
        FeatureFlags b = FeatureFlags.getInstance();
        System.out.println("same instance: " + (a == b));
        System.out.println("express-delivery: " + a.isOn("express-delivery"));
        try { a.isOn("drone-delivery"); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
