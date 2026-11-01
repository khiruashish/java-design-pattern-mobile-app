import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Strangler fig: a routing facade sends migrated endpoints to new services and everything else to the legacy system. */
public class StranglerFigDemo {

    static final Function<String, String> LEGACY = path -> "legacy monolith handled " + path;       // (1)!
    static final Function<String, String> CATALOG_SERVICE = path -> "catalog-service handled " + path;
    static final Function<String, String> SLOTS_SERVICE = path -> "slots-service handled " + path;

    static final class Facade {
        private final Map<String, Function<String, String>> migrated = new LinkedHashMap<>();
        void migrate(String prefix, Function<String, String> service) { migrated.put(prefix, service); }    // (2)!
        void rollback(String prefix) { migrated.remove(prefix); }
        String handle(String path) {
            return migrated.entrySet().stream().filter(e -> path.startsWith(e.getKey())).findFirst()
                    .map(e -> e.getValue().apply(path)).orElseGet(() -> LEGACY.apply(path));               // (3)!
        }
    }

    public static void main(String[] args) {
        Facade facade = new Facade();
        List<String> traffic = List.of("/catalog/dairy", "/slots/today", "/checkout", "/account");
        System.out.println("phase 0:"); traffic.forEach(p -> System.out.println("  " + facade.handle(p)));
        facade.migrate("/catalog", CATALOG_SERVICE);
        facade.migrate("/slots", SLOTS_SERVICE);
        System.out.println("phase 1:"); traffic.forEach(p -> System.out.println("  " + facade.handle(p)));
        facade.rollback("/slots");
        System.out.println("slots rolled back: " + facade.handle("/slots/today"));
    }
}
