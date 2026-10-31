import java.lang.reflect.Constructor;

/** Enums as Singleton (safe against reflection and serialization) and as a compact State machine. */
public class EnumSingletonStateDemo {

    // ---------- classic singleton: private constructor, but reflection can still call it ----------
    static final class ClassicRegistry {
        static final ClassicRegistry INSTANCE = new ClassicRegistry();
        private ClassicRegistry() { }
    }

    // ---------- enum singleton ----------
    enum FeatureFlags {                                                                              // (1)!
        INSTANCE;
        private volatile boolean expressDelivery = true;
        boolean expressDelivery() { return expressDelivery; }
    }

    // ---------- enum as State: each constant implements the transitions it allows ----------
    enum OrderState {                                                                               // (2)!
        PLACED { OrderState next(String e) { return switch (e) { case "pack" -> PACKED; case "cancel" -> CANCELLED; default -> invalid(e); }; } },
        PACKED { OrderState next(String e) { return switch (e) { case "dispatch" -> OUT_FOR_DELIVERY; case "cancel" -> CANCELLED; default -> invalid(e); }; } },
        OUT_FOR_DELIVERY { OrderState next(String e) { return switch (e) { case "deliver" -> DELIVERED; default -> invalid(e); }; } },
        DELIVERED { OrderState next(String e) { return invalid(e); } },
        CANCELLED { OrderState next(String e) { return invalid(e); } };

        abstract OrderState next(String event);
        OrderState invalid(String e) { throw new IllegalStateException("can't '" + e + "' when " + this); }
    }

    public static void main(String[] args) throws Exception {
        Constructor<ClassicRegistry> c = ClassicRegistry.class.getDeclaredConstructor();
        c.setAccessible(true);
        System.out.println("classic: second instance via reflection? " + (c.newInstance() != ClassicRegistry.INSTANCE));
        try {
            Constructor<FeatureFlags> e = FeatureFlags.class.getDeclaredConstructor(String.class, int.class);
            e.setAccessible(true);
            e.newInstance("SECOND", 1);
        } catch (IllegalArgumentException ex) { System.out.println("enum: reflection refused (" + ex.getMessage() + ")"); }   // (3)!

        OrderState s = OrderState.PLACED;
        for (String event : new String[] {"pack", "dispatch", "deliver"}) { s = s.next(event); System.out.println(event + " -> " + s); }
        try { s.next("cancel"); } catch (IllegalStateException ex) { System.out.println(ex.getMessage()); }
    }
}
