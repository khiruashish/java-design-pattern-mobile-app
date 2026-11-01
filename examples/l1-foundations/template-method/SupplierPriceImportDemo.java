import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Realistic Template Method: supplier price files differ in format; validation and safety rules are shared and fixed. */
public class SupplierPriceImportDemo {

    record PriceUpdate(String sku, long cents) { }
    record Result(List<PriceUpdate> applied, List<String> rejected, List<String> needsReview) { }

    abstract static class PriceImport {
        private static final double MAX_AUTO_CHANGE = 0.30;

        final Result run(String file, Map<String, Long> currentPrices) {
            List<PriceUpdate> applied = new ArrayList<>(); List<String> rejected = new ArrayList<>(), review = new ArrayList<>();
            for (String line : lines(file)) {                                              // (1)!
                PriceUpdate u;
                try { u = parse(line); }
                catch (RuntimeException e) { rejected.add("'" + line + "' (" + e.getMessage() + ")"); continue; }
                Long old = currentPrices.get(u.sku());
                if (old == null) { rejected.add(u.sku() + " (unknown sku)"); continue; }
                if (u.cents() <= 0) { rejected.add(u.sku() + " (non-positive price)"); continue; }
                if (Math.abs(u.cents() - old) > old * MAX_AUTO_CHANGE) {                  // (2)!
                    review.add(u.sku() + " " + old + " -> " + u.cents());
                    continue;
                }
                applied.add(u);
            }
            return new Result(applied, rejected, review);
        }

        protected List<String> lines(String file) {                                       // hook: most suppliers have a header row
            return file.lines().skip(1).filter(l -> !l.isBlank()).toList();
        }
        protected abstract PriceUpdate parse(String line);                                // (3)!
    }

    /** GreenValley sends CSV in rupees with decimals: sku,price */
    static final class GreenValleyImport extends PriceImport {
        protected PriceUpdate parse(String line) {
            String[] f = line.split(",");
            return new PriceUpdate(f[0].strip(), Math.round(Double.parseDouble(f[1].strip()) * 100));
        }
    }

    /** DairyDay sends pipe-separated paise, no header: price|sku */
    static final class DairyDayImport extends PriceImport {
        @Override protected List<String> lines(String file) { return file.lines().filter(l -> !l.isBlank()).toList(); }   // (4)!
        protected PriceUpdate parse(String line) {
            String[] f = line.split("\\|");
            return new PriceUpdate(f[1].strip(), Long.parseLong(f[0].strip()));
        }
    }

    public static void main(String[] args) {
        Map<String, Long> current = Map.of("RICE-5KG", 54_900L, "OIL-1L", 18_900L, "MILK-1L", 6_800L, "CURD-500", 4_500L);
        Result gv = new GreenValleyImport().run("sku,price\nRICE-5KG,559.00\nOIL-1L,299.00\nATTA-5KG,265\nOIL-2L,abc\n", current);
        System.out.println("GreenValley applied " + gv.applied() + "\n  rejected " + gv.rejected() + "\n  review " + gv.needsReview());
        Result dd = new DairyDayImport().run("7000|MILK-1L\n0|CURD-500\n", current);
        System.out.println("DairyDay applied " + dd.applied() + "\n  rejected " + dd.rejected() + "\n  review " + dd.needsReview());
    }
}
