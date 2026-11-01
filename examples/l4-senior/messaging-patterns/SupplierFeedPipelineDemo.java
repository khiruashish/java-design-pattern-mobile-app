import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: supplier price feeds flow through a pipeline of small steps (pipes and filters): translate each format
 * to the canonical model, validate, then route by content. Bad messages go to a dead letter channel with a reason.
 */
public class SupplierFeedPipelineDemo {

    record Inbound(String supplier, String payload) {}
    record PriceUpdate(String supplier, String sku, long pricePaise, int changePercent) {}

    static final Map<String, Long> currentPrice = Map.of("MILK-1L", 6800L, "TOMATO-1KG", 3000L, "CURD-500G", 4500L);
    static final Map<String, List<String>> channels = new TreeMap<>();

    static PriceUpdate translate(Inbound in) {                                                     // (1)!
        String[] f = in.payload().split(in.supplier().equals("DairyDay") ? "," : "\\|");
        long paise = in.supplier().equals("DairyDay") ? Math.round(Double.parseDouble(f[1]) * 100) : Long.parseLong(f[1]);
        long old = currentPrice.getOrDefault(f[0], paise);
        return new PriceUpdate(in.supplier(), f[0], paise, (int) Math.round((paise - old) * 100.0 / old));
    }

    static void validate(PriceUpdate u) {                                                          // (2)!
        if (!currentPrice.containsKey(u.sku())) throw new IllegalArgumentException("unknown SKU " + u.sku());
        if (u.pricePaise() <= 0) throw new IllegalArgumentException("non-positive price");
    }

    static String route(PriceUpdate u) {                                                           // (3)!
        return Math.abs(u.changePercent()) > 20 ? "price-review" : "price-apply";
    }

    static void handle(Inbound in) {
        String channel, entry;
        try {
            PriceUpdate u = translate(in);
            validate(u);
            channel = route(u);
            entry = u.sku() + " " + u.pricePaise() + " (" + (u.changePercent() >= 0 ? "+" : "") + u.changePercent() + "%)";
        } catch (RuntimeException e) {
            channel = "dead-letter";
            entry = in.supplier() + " '" + in.payload() + "': " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(entry);
    }

    public static void main(String[] args) {
        List.of(new Inbound("DairyDay", "MILK-1L,70.00"), new Inbound("GreenValley", "TOMATO-1KG|6000"),
                new Inbound("DairyDay", "CURD-500G,abc"), new Inbound("GreenValley", "MANGO-1KG|15000"),
                new Inbound("DairyDay", "CURD-500G,44.00")).forEach(SupplierFeedPipelineDemo::handle);
        channels.forEach((c, entries) -> { System.out.println(c + ":"); entries.forEach(e -> System.out.println("  " + e)); });
    }
}
