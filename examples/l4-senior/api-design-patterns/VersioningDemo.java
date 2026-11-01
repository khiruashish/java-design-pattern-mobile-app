import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * API versioning: when a breaking change is unavoidable, serve the old and new contract side by side from one
 * domain model, route by version, tell old clients when the old version will be retired, and measure who still
 * uses it.
 */
public class VersioningDemo {

    record Product(String sku, String name, long pricePaise, String unit) {}                   // one domain model

    static final Map<String, Function<Product, String>> REPRESENTATIONS = new TreeMap<>();    // (1)!
    static {
        REPRESENTATIONS.put("v1", p -> "{\"sku\":\"" + p.sku() + "\",\"name\":\"" + p.name() + "\",\"price\":" + p.pricePaise() / 100.0 + "}");
        REPRESENTATIONS.put("v2", p -> "{\"sku\":\"" + p.sku() + "\",\"name\":\"" + p.name()
                + "\",\"price\":{\"amountPaise\":" + p.pricePaise() + ",\"currency\":\"INR\"},\"unit\":\"" + p.unit() + "\"}");
    }
    static final Map<String, Integer> usage = new TreeMap<>();

    static String get(String path, Product p) {
        String version = path.split("/")[1];                                                   // (2)!
        usage.merge(version, 1, Integer::sum);
        String body = REPRESENTATIONS.get(version).apply(p);
        String deprecation = version.equals("v1") ? "  [Deprecation: true, Sunset: Sat, 01 May 2027 00:00:00 GMT]" : "";   // (3)!
        return version + " " + body + deprecation;
    }

    public static void main(String[] args) {
        Product milk = new Product("MILK-1L", "Toned Milk", 6850, "1 L");
        for (String path : List.of("/v1/products/MILK-1L", "/v2/products/MILK-1L", "/v1/products/MILK-1L"))
            System.out.println(get(path, milk));
        System.out.println("usage by version: " + usage);
    }
}
