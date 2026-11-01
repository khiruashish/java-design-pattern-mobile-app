import java.lang.reflect.Constructor;

/** Minimal: an enum singleton can't be duplicated by reflection; a private-constructor singleton can. */
public class EnumSingletonDemo {

    static final class ClassicRegistry {
        static final ClassicRegistry INSTANCE = new ClassicRegistry();
        private ClassicRegistry() {}
    }

    enum EnumRegistry { INSTANCE }                                                                         // (1)!

    public static void main(String[] args) throws Exception {
        Constructor<ClassicRegistry> c = ClassicRegistry.class.getDeclaredConstructor();
        c.setAccessible(true);
        System.out.println("classic: second instance created = " + (c.newInstance() != ClassicRegistry.INSTANCE));
        try {
            Constructor<EnumRegistry> e = EnumRegistry.class.getDeclaredConstructor(String.class, int.class);
            e.setAccessible(true);
            e.newInstance("COPY", 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("enum: " + ex.getMessage());                                               // (2)!
        }
    }
}
