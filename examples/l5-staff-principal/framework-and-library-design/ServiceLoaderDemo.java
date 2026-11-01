import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Service Provider Interface (SPI) with java.util.ServiceLoader: the library defines an interface, providers ship
 * implementations plus a META-INF/services file naming them, and the library discovers them at run time without
 * compile-time dependencies. Here the services file is written to a temp folder, exactly as it would sit in a jar.
 */
public class ServiceLoaderDemo {

    /** The SPI owned by FreshCart's delivery library. */
    public interface DeliveryPartner {
        String name();
        long quotePaise(int distanceKm);
    }

    public static class QuickDropPartner implements DeliveryPartner {
        public String name() { return "QuickDrop"; }
        public long quotePaise(int km) { return 2500 + 400L * km; }
    }

    public static class ParcelGoPartner implements DeliveryPartner {
        public String name() { return "ParcelGo"; }
        public long quotePaise(int km) { return 2800 + 350L * km; }
    }

    public static class BrokenPartner implements DeliveryPartner {
        public BrokenPartner() { throw new IllegalStateException("missing API key"); }
        public String name() { return "Broken"; }
        public long quotePaise(int km) { return 0; }
    }

    public static void main(String[] args) throws Exception {
        Path jarLike = Files.createTempDirectory("spi");
        Path services = jarLike.resolve("META-INF/services/" + DeliveryPartner.class.getName());
        Files.createDirectories(services.getParent());
        Files.writeString(services, """
                # one provider class per line, as shipped inside each partner's jar
                ServiceLoaderDemo$QuickDropPartner
                ServiceLoaderDemo$ParcelGoPartner
                ServiceLoaderDemo$BrokenPartner
                """);                                                                             // (1)!
        System.out.println("META-INF/services/" + DeliveryPartner.class.getName());

        try (URLClassLoader loader = new URLClassLoader(new URL[] {jarLike.toUri().toURL()}, ServiceLoaderDemo.class.getClassLoader())) {
            ServiceLoader<DeliveryPartner> partners = ServiceLoader.load(DeliveryPartner.class, loader);
            partners.stream().forEach(p -> System.out.println("found provider type " + p.type().getSimpleName() + " (not instantiated yet)")); // (2)!

            for (var it = partners.iterator(); ; ) {
                try {
                    if (!it.hasNext()) break;
                    DeliveryPartner p = it.next();
                    System.out.println(p.name() + " quote for 5 km: Rs " + p.quotePaise(5) / 100.0);
                } catch (ServiceConfigurationError e) {                                            // (3)!
                    System.out.println("skipped a provider: " + e.getCause().getMessage());
                }
            }
        }
    }
}
