import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Intrinsic state (shared, immutable) lives in the flyweight; extrinsic state (per use) is stored outside or passed in. */
public class IntrinsicExtrinsicDemo {

    // ===================== Before: every order line carries a full copy of the product details =====================
    record HeavyLine(String name, String category, byte[] thumbnail, int qty, String orderId) { }

    // ===================== After: product details shared; the line keeps only what is unique to it =====================
    record ProductType(String name, String category, byte[] thumbnail) { }                  // (1)!
    record OrderLine(ProductType type, int qty, String orderId) {                            // (2)!
        String label() { return qty + " x " + type.name() + " (" + type.category() + ") for " + orderId; }   // (3)!
    }

    public static void main(String[] args) {
        String[] names = {"Milk 1L", "Bread", "Eggs x6", "Banana", "Rice 5kg"};
        int lines = 10_000;

        List<HeavyLine> heavy = new ArrayList<>();
        for (int i = 0; i < lines; i++) heavy.add(new HeavyLine(names[i % 5], "Daily", new byte[2048], 1 + i % 3, "FC-" + i));

        Map<String, ProductType> types = new HashMap<>();
        List<OrderLine> light = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            ProductType t = types.computeIfAbsent(names[i % 5], n -> new ProductType(n, "Daily", new byte[2048]));
            light.add(new OrderLine(t, 1 + i % 3, "FC-" + i));
        }

        Map<byte[], Boolean> heavyThumbs = new IdentityHashMap<>(), lightThumbs = new IdentityHashMap<>();
        heavy.forEach(h -> heavyThumbs.put(h.thumbnail(), true));
        light.forEach(l -> lightThumbs.put(l.type().thumbnail(), true));
        System.out.println("before: " + heavyThumbs.size() + " thumbnails = " + heavyThumbs.size() * 2 / 1024 + " MB");
        System.out.println("after:  " + lightThumbs.size() + " thumbnails = " + lightThumbs.size() * 2 + " KB, shared by " + lines + " lines");
        System.out.println(light.get(7).label());
    }
}
