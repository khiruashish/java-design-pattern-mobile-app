import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared kernel: a small model two contexts agree to share and change together. Published language: a documented,
 * versioned exchange format that many consumers can read, with tolerant readers so producers can evolve.
 */
public class SharedKernelPublishedLanguageDemo {

    // ---------- Shared kernel: tiny, stable, jointly owned by Checkout and Pricing ----------
    record Money(long paise) {                                                            // (1)!
        Money { if (paise < 0) throw new IllegalArgumentException("negative"); }
        Money plus(Money o) { return new Money(paise + o.paise); }
    }

    // ---------- Published language: OrderPlaced as a documented map-based message ----------
    static Map<String, Object> orderPlacedV2(String orderId, Money total, String pin) {   // (2)!
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "OrderPlaced");
        m.put("version", 2);
        m.put("orderId", orderId);
        m.put("totalPaise", total.paise());
        m.put("deliveryPin", pin);
        m.put("channel", "app");                                 // added in v2
        return m;
    }

    /** A tolerant reader: takes only the fields it needs, ignores unknown ones, defaults optional ones. */
    record DeliveryView(String orderId, String pin, String channel) {
        static DeliveryView read(Map<String, Object> msg) {                                // (3)!
            if (!"OrderPlaced".equals(msg.get("type"))) throw new IllegalArgumentException("not an OrderPlaced");
            return new DeliveryView((String) msg.get("orderId"), (String) msg.get("deliveryPin"),
                    (String) msg.getOrDefault("channel", "unknown"));
        }
    }

    public static void main(String[] args) {
        Money total = new Money(45_000).plus(new Money(6_800));
        Map<String, Object> v2 = orderPlacedV2("FC-88", total, "560034");
        Map<String, Object> v1 = new LinkedHashMap<>(v2);
        v1.put("version", 1);
        v1.remove("channel");
        Map<String, Object> v3 = new LinkedHashMap<>(v2);
        v3.put("version", 3);
        v3.put("giftWrap", true);

        System.out.println("published: " + v2);
        System.out.println("reads v1: " + DeliveryView.read(v1));
        System.out.println("reads v2: " + DeliveryView.read(v2));
        System.out.println("reads v3 (unknown field ignored): " + DeliveryView.read(v3));
    }
}
