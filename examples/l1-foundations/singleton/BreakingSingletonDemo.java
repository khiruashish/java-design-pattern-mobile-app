import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Constructor;

/** How classic singletons break, and the defences. */
public class BreakingSingletonDemo {

    /** A naive singleton: private constructor, nothing else. */
    static final class NaiveSettings implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        static final NaiveSettings INSTANCE = new NaiveSettings();
        private NaiveSettings() { }
    }

    /** Hardened: refuses a second construction and keeps identity on deserialization. */
    static final class HardenedSettings implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private static boolean created;
        static final HardenedSettings INSTANCE = new HardenedSettings();

        private HardenedSettings() {
            synchronized (HardenedSettings.class) {
                if (created) throw new IllegalStateException("HardenedSettings is a singleton");   // (1)!
                created = true;
            }
        }

        @Serial private Object readResolve() { return INSTANCE; }                       // (2)!
    }

    static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return in.readObject(); }
    }

    static Object viaReflection(Class<?> type) throws Exception {
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);                                                      // (3)!
        return ctor.newInstance();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("naive, reflection:      second instance = " + (viaReflection(NaiveSettings.class) != NaiveSettings.INSTANCE));
        System.out.println("naive, serialization:   second instance = " + (roundTrip(NaiveSettings.INSTANCE) != NaiveSettings.INSTANCE));

        try {
            viaReflection(HardenedSettings.class);
        } catch (java.lang.reflect.InvocationTargetException e) {
            System.out.println("hardened, reflection:   blocked (" + e.getCause().getMessage() + ")");
        }
        System.out.println("hardened, serialization: same instance = " + (roundTrip(HardenedSettings.INSTANCE) == HardenedSettings.INSTANCE));
    }
}
