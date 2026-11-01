import java.util.List;
import java.util.Map;

/**
 * An Anti-Corruption Layer translates a foreign model (here a legacy ERP's supplier feed) into our own, so the
 * foreign concepts and codes never leak into the domain.
 */
public class AntiCorruptionLayerDemo {

    // ---------- The legacy ERP's model: cryptic codes, weights in kg as strings, prices with tax mixed in ----------
    record ErpItemRow(String MATNR, String MAKTX, String MEINS, String BRGEW, String VERPR, String MWSKZ, String LVORM) {}   // (1)!

    // ---------- Our domain model ----------
    record Sku(String code) {}
    record Product(Sku sku, String name, int grams, long pricePaiseExTax, int gstPercent) {}

    // ---------- The ACL ----------
    static final class ErpProductTranslator {                                                      // (2)!
        private static final Map<String, Integer> TAX_CODES = Map.of("V0", 0, "V1", 5, "V2", 12, "V3", 18);
        private static final Map<String, String> SKU_BY_MATERIAL = Map.of("000000000010045", "PANEER-200G", "000000000010046", "CURD-400G");

        List<Product> translate(List<ErpItemRow> rows) {
            return rows.stream()
                    .filter(r -> !"X".equals(r.LVORM()))                                                 // deletion flag
                    .filter(r -> SKU_BY_MATERIAL.containsKey(r.MATNR()))
                    .map(this::toProduct)
                    .toList();
        }
        private Product toProduct(ErpItemRow r) {
            int gst = TAX_CODES.getOrDefault(r.MWSKZ(), 18);
            long grossPaise = Math.round(Double.parseDouble(r.VERPR().replace(',', '.')) * 100);
            long exTax = grossPaise * 100 / (100 + gst);                                                // (3)!
            int grams = (int) Math.round(Double.parseDouble(r.BRGEW().replace(',', '.')) * ("KG".equals(r.MEINS()) ? 1000 : 1));
            return new Product(new Sku(SKU_BY_MATERIAL.get(r.MATNR())), r.MAKTX().strip(), grams, exTax, gst);
        }
    }

    public static void main(String[] args) {
        var feed = List.of(
                new ErpItemRow("000000000010045", "PANEER MALAI 200G   ", "KG", "0,200", "93,45", "V1", ""),
                new ErpItemRow("000000000010046", "DAHI 400G", "G", "400", "47,25", "V1", ""),
                new ErpItemRow("000000000010047", "OLD SKU", "KG", "1,0", "10,00", "V3", "X"));
        new ErpProductTranslator().translate(feed).forEach(System.out::println);
    }
}
