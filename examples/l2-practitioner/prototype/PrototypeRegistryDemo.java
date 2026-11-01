import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Prototype registry: named, pre-configured objects; clients ask for a copy by name and customise it. */
public class PrototypeRegistryDemo {

    static final class Banner {
        String title; String colour; List<String> skus; String city;
        Banner(String title, String colour, List<String> skus, String city) { this.title = title; this.colour = colour; this.skus = new ArrayList<>(skus); this.city = city; }
        Banner copy() { return new Banner(title, colour, skus, city); }                     // (1)!
        public String toString() { return "[" + city + "] " + title + " (" + colour + ") " + skus; }
    }

    static final class BannerRegistry {
        private final Map<String, Banner> prototypes = new TreeMap<>();
        void register(String key, Banner prototype) { prototypes.put(key, prototype.copy()); }   // (2)!
        Banner create(String key) {
            Banner p = prototypes.get(key);
            if (p == null) throw new IllegalArgumentException("no banner template '" + key + "', have " + prototypes.keySet());
            return p.copy();                                                                // (3)!
        }
    }

    public static void main(String[] args) {
        BannerRegistry registry = new BannerRegistry();
        registry.register("monsoon", new Banner("Monsoon essentials", "teal", List.of("UMBRELLA", "TEA", "PAKORA-MIX"), "ALL"));
        registry.register("weekend", new Banner("Weekend breakfast", "orange", List.of("EGGS", "BREAD", "JUICE"), "ALL"));

        Banner pune = registry.create("monsoon"); pune.city = "pune";
        Banner mumbai = registry.create("monsoon"); mumbai.city = "mumbai"; mumbai.skus.add("RAINCOAT");
        System.out.println(pune);
        System.out.println(mumbai);
        System.out.println("template untouched: " + registry.create("monsoon"));
        try { registry.create("diwali"); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
