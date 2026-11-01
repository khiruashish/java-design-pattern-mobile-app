import java.io.IOException;
import java.io.InputStream;

/**
 * Class loading and singleton guarantees: a class is initialised lazily, exactly once per class loader, under a
 * JVM lock (JLS 12.4.2). The holder idiom uses that for free lazy, thread-safe singletons. But "once" is per
 * class loader: two loaders give two "singletons".
 */
public class ClassLoadingSingletonDemo {

    static final StringBuilder events = new StringBuilder();

    static final class PriceTable {
        static { events.append("[PriceTable initialised]"); }
        private PriceTable() { events.append("[instance created]"); }
        private static final class Holder { static final PriceTable INSTANCE = new PriceTable(); }      // (1)!
        static PriceTable get() { return Holder.INSTANCE; }
        static String version() { return "v1"; }
    }

    public static void main(String[] args) throws Exception {
        events.append("[main started]");
        System.out.println("version: " + PriceTable.version());
        events.append("[before get]");
        PriceTable a = PriceTable.get();
        PriceTable b = PriceTable.get();
        System.out.println("events: " + events);                                                         // (2)!
        System.out.println("same instance in one loader: " + (a == b));

        ClassLoader isolated = new ClassLoader(null) {                                                    // (3)!
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                String path = name.replace('.', '/') + ".class";
                try (InputStream in = ClassLoadingSingletonDemo.class.getClassLoader().getResourceAsStream(path)) {
                    if (in == null) throw new ClassNotFoundException(name);
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) { throw new ClassNotFoundException(name, e); }
            }
        };
        Class<?> other = isolated.loadClass(PriceTable.class.getName());
        System.out.println("same class name, same Class object? " + (other == PriceTable.class)
                + " (loaders: app vs " + (other.getClassLoader() == isolated ? "isolated" : "?") + ")");
    }
}
