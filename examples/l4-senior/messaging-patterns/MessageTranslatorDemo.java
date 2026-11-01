import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Message translator: convert each partner's format into one canonical model at the edge, so internal consumers
 * only ever see one shape. N formats need N translators, not N x M.
 */
public class MessageTranslatorDemo {

    record PriceUpdate(String sku, long pricePaise, String effectiveDate) {}                       // (1)!

    static final Map<String, Function<String, PriceUpdate>> TRANSLATORS = new LinkedHashMap<>();
    static {
        TRANSLATORS.put("dairyday-csv", line -> {                                                 // (2)!
            String[] f = line.split(",");
            return new PriceUpdate(f[0].trim(), Math.round(Double.parseDouble(f[1].trim()) * 100), f[2].trim());
        });
        TRANSLATORS.put("greenvalley-kv", text -> {
            Map<String, String> kv = new LinkedHashMap<>();
            for (String pair : text.split(";")) { String[] p = pair.split("="); kv.put(p[0].trim(), p[1].trim()); }
            String[] dmy = kv.get("from").split("/");
            return new PriceUpdate("GV-" + kv.get("item"), Long.parseLong(kv.get("paise")), dmy[2] + "-" + dmy[1] + "-" + dmy[0]);   // (3)!
        });
    }

    public static void main(String[] args) {
        List<String[]> inbound = List.of(
                new String[] {"dairyday-csv", "MILK-1L, 68.50, 2026-11-02"},
                new String[] {"greenvalley-kv", "item=TOMATO-1KG; paise=3200; from=02/11/2026"});
        for (String[] in : inbound) System.out.println(in[0] + " -> " + TRANSLATORS.get(in[0]).apply(in[1]));
    }
}
