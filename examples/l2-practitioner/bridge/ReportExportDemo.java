import java.util.List;
import java.util.Map;

/** Realistic Bridge: FreshCart's ops reports (what data) x export formats (how it is written). New report or new format = one class. */
public class ReportExportDemo {

    // ===================== Implementor: how rows are written =====================
    interface Exporter {                                                                      // (1)!
        String export(String title, List<String> columns, List<List<Object>> rows);
    }
    record CsvExporter() implements Exporter {
        public String export(String t, List<String> cols, List<List<Object>> rows) {
            StringBuilder sb = new StringBuilder(String.join(",", cols));
            rows.forEach(r -> sb.append("\n").append(String.join(",", r.stream().map(ReportExportDemo::csv).toList())));
            return sb.toString();
        }
    }
    record JsonExporter() implements Exporter {
        public String export(String t, List<String> cols, List<List<Object>> rows) {
            StringBuilder sb = new StringBuilder("{\"report\":\"" + t + "\",\"rows\":[");
            for (int i = 0; i < rows.size(); i++) {
                sb.append(i == 0 ? "{" : ",{");
                for (int c = 0; c < cols.size(); c++) {
                    Object v = rows.get(i).get(c);
                    sb.append(c == 0 ? "" : ",").append('"').append(cols.get(c)).append("\":").append(v instanceof Number ? v : "\"" + v + "\"");
                }
                sb.append("}");
            }
            return sb.append("]}").toString();
        }
    }
    static String csv(Object v) { String s = String.valueOf(v); return s.contains(",") ? "\"" + s + "\"" : s; }   // (2)!

    // ===================== Abstraction: which data =====================
    abstract static class Report {
        protected final Exporter exporter;
        Report(Exporter e) { exporter = e; }
        abstract String generate();
    }
    static final class DailySalesReport extends Report {
        private final Map<String, Long> salesByStore;
        DailySalesReport(Exporter e, Map<String, Long> s) { super(e); salesByStore = s; }
        String generate() {
            List<List<Object>> rows = salesByStore.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(en -> List.<Object>of(en.getKey(), en.getValue() / 100)).toList();
            return exporter.export("daily-sales", List.of("store", "rupees"), rows);
        }
    }
    static final class LowStockReport extends Report {                                       // (3)!
        private final Map<String, Integer> stock; private final int threshold;
        LowStockReport(Exporter e, Map<String, Integer> s, int threshold) { super(e); stock = s; this.threshold = threshold; }
        String generate() {
            List<List<Object>> rows = stock.entrySet().stream().filter(en -> en.getValue() < threshold).sorted(Map.Entry.comparingByKey())
                    .map(en -> List.<Object>of(en.getKey(), en.getValue())).toList();
            return exporter.export("low-stock", List.of("sku", "units"), rows);
        }
    }

    public static void main(String[] args) {
        Map<String, Long> sales = Map.of("Koramangala", 18_45_000_00L, "Indiranagar, 2nd stage", 12_10_500_00L);
        Map<String, Integer> stock = Map.of("MILK-1L", 4, "BREAD", 30, "EGGS-6", 2);
        System.out.println(new DailySalesReport(new CsvExporter(), sales).generate());
        System.out.println(new DailySalesReport(new JsonExporter(), sales).generate());
        System.out.println(new LowStockReport(new CsvExporter(), stock, 5).generate());
    }
}
