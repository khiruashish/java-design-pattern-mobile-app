import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** FreshCart store managers export daily sales; the file name decides which exporter is created. */
public class ReportExporterDemo {

    record Sale(String sku, int quantity, long revenueInCents) { }

    interface Exporter {
        String contentType();
        String export(List<Sale> sales);
    }

    static final class CsvExporter implements Exporter {
        public String contentType() { return "text/csv"; }
        public String export(List<Sale> sales) {
            return "sku,quantity,revenue_cents\n" + sales.stream()
                    .map(s -> s.sku() + "," + s.quantity() + "," + s.revenueInCents()).collect(Collectors.joining("\n"));
        }
    }

    static final class JsonExporter implements Exporter {
        public String contentType() { return "application/json"; }
        public String export(List<Sale> sales) {
            return sales.stream().map(s -> "{\"sku\":\"" + s.sku() + "\",\"quantity\":" + s.quantity() + ",\"revenueCents\":" + s.revenueInCents() + "}")
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }

    static final class ExporterFactory {
        private static final Map<String, Supplier<Exporter>> BY_EXTENSION = Map.of(       // (1)!
                "csv", CsvExporter::new,
                "json", JsonExporter::new);

        static Exporter forFileName(String fileName) {                                  // (2)!
            int dot = fileName.lastIndexOf('.');
            String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            Supplier<Exporter> supplier = BY_EXTENSION.get(ext);
            if (supplier == null) throw new IllegalArgumentException("can't export '" + fileName + "', supported: " + BY_EXTENSION.keySet().stream().sorted().toList());
            return supplier.get();
        }
    }

    public static void main(String[] args) {
        List<Sale> sales = List.of(new Sale("MILK-1L", 42, 5_418), new Sale("BREAD-WHT", 17, 4_233));
        for (String file : List.of("pune-01-2026-11-02.csv", "pune-01-2026-11-02.JSON", "pune-01-2026-11-02.xlsx")) {
            try {
                Exporter exporter = ExporterFactory.forFileName(file);                   // (3)!
                System.out.println(file + " (" + exporter.contentType() + ")\n" + exporter.export(sales));
            } catch (IllegalArgumentException e) {
                System.out.println("rejected: " + e.getMessage());
            }
        }
    }
}
