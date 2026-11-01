import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GreenValley's nightly supplier price import. Config fails fast; each row becomes a Result with all its problems
 * (notification); good rows are applied, bad ones reported with codes; too many bad rows aborts the whole import
 * (error budget); I/O failures are translated and resources are always closed.
 */
public class SupplierImportDemo {

    // ---------- Fail fast on configuration ----------
    record ImportConfig(int maxErrorPercent, long maxPricePaise) {                  // (1)!
        ImportConfig {
            if (maxErrorPercent < 0 || maxErrorPercent > 50) throw new IllegalArgumentException("maxErrorPercent must be 0..50");
            if (maxPricePaise <= 0) throw new IllegalArgumentException("maxPricePaise must be positive");
        }
    }

    // ---------- Row results with error codes ----------
    record Price(String sku, long paise) {}
    record RowError(int line, String code, String detail) {}
    sealed interface RowResult {}
    record Good(Price price) implements RowResult {}
    record Bad(List<RowError> errors) implements RowResult {}

    static RowResult parseRow(int line, String text, ImportConfig cfg) {           // (2)!
        String[] f = text.split(",", -1);
        if (f.length != 2) return new Bad(List.of(new RowError(line, "E_COLUMNS", "expected 2 columns, got " + f.length)));
        List<RowError> errors = new ArrayList<>();
        String sku = f[0].strip();
        if (!sku.matches("[A-Z0-9-]{3,20}")) errors.add(new RowError(line, "E_SKU", "'" + sku + "'"));
        long paise = -1;
        try {
            paise = Math.round(Double.parseDouble(f[1].strip()) * 100);
            if (paise <= 0 || paise > cfg.maxPricePaise()) errors.add(new RowError(line, "E_PRICE_RANGE", f[1].strip()));
        } catch (NumberFormatException e) {
            errors.add(new RowError(line, "E_PRICE_FORMAT", "'" + f[1].strip() + "'"));
        }
        return errors.isEmpty() ? new Good(new Price(sku, paise)) : new Bad(errors);
    }

    // ---------- Translation of I/O failures ----------
    static final class ImportFailedException extends RuntimeException {
        ImportFailedException(String m, Throwable cause) { super(m, cause); }
    }

    sealed interface ImportReport {}
    record Applied(int applied, List<RowError> rejected) implements ImportReport {}
    record Aborted(String reason, List<RowError> rejected) implements ImportReport {}

    static ImportReport runImport(BufferedReader source, ImportConfig cfg, Map<String, Long> catalogue) {
        List<Price> good = new ArrayList<>();
        List<RowError> rejected = new ArrayList<>();
        int rows = 0;
        try (source) {                                                              // (3)!
            String header = source.readLine();
            if (!"sku,price".equals(header)) throw new ImportFailedException("unexpected header: " + header, null);
            String text;
            int line = 1;
            while ((text = source.readLine()) != null) {
                line++;
                if (text.isBlank()) continue;
                rows++;
                switch (parseRow(line, text, cfg)) {
                    case Good g -> good.add(g.price());
                    case Bad b -> rejected.addAll(b.errors());
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new ImportFailedException("could not read supplier file", e);
        }
        long badRows = rejected.stream().mapToInt(RowError::line).distinct().count();
        if (rows == 0 || badRows * 100 > (long) rows * cfg.maxErrorPercent())          // (4)!
            return new Aborted(badRows + " of " + rows + " rows invalid, over the " + cfg.maxErrorPercent() + "% budget; nothing applied", rejected);
        good.forEach(p -> catalogue.put(p.sku(), p.paise()));                       // (5)!
        return new Applied(good.size(), rejected);
    }

    static void print(String title, ImportReport r) {
        System.out.println(title + ": " + switch (r) {
            case Applied a -> "applied " + a.applied() + ", rejected " + a.rejected().size() + " problem(s)";
            case Aborted a -> "ABORTED, " + a.reason();
        });
        List<RowError> errors = r instanceof Applied a ? a.rejected() : ((Aborted) r).rejected();
        errors.forEach(e -> System.out.println("  line " + e.line() + " " + e.code() + " " + e.detail()));
    }

    public static void main(String[] args) {
        ImportConfig cfg = new ImportConfig(20, 500_000);
        Map<String, Long> catalogue = new LinkedHashMap<>();

        String mostlyGood = "sku,price\nTOMATO-1KG,32.50\nONION-1KG,41\nspinach,25\nOKRA-500G,abc\n\nPOTATO-1KG,28\nCARROT-1KG,39.9\nBEANS-500G,44\nPEAS-500G,61\nCORN-2,22\nGARLIC-200G,58\n";
        print("night 1", runImport(new BufferedReader(new StringReader(mostlyGood)), cfg, catalogue));
        System.out.println("catalogue now has " + catalogue.size() + " prices, TOMATO-1KG=" + catalogue.get("TOMATO-1KG"));

        String broken = "sku,price\nTOMATO-1KG;30\nONION-1KG;40\nPOTATO-1KG,27\n";
        print("night 2", runImport(new BufferedReader(new StringReader(broken)), cfg, catalogue));
        System.out.println("TOMATO-1KG still " + catalogue.get("TOMATO-1KG"));

        BufferedReader failing = new BufferedReader(new StringReader("sku,price\n")) {
            @Override public String readLine() throws IOException { throw new IOException("SFTP connection reset"); }
        };
        try {
            runImport(failing, cfg, catalogue);
        } catch (ImportFailedException e) {
            System.out.println("night 3: " + e.getMessage() + " (cause: " + e.getCause().getMessage() + ")");
        }
        try {
            new ImportConfig(80, 500_000);
        } catch (IllegalArgumentException e) {
            System.out.println("startup: " + e.getMessage());
        }
    }
}
