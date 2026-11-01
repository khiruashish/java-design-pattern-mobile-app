import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Realistic: GreenValley farms upload gzipped price lists to FreshCart. The importer is built only from JDK patterns:
 * Decorator (stream stack), Iterator (lazy rows), Optional (bad rows), Strategy (Comparator + Collator for display).
 */
public class SupplierPriceImportDemo {

    record PriceRow(String product, String unit, long paise) { }

    /** Iterator over parsed rows, reading lazily: a 2 GB file never sits in memory. */
    static final class PriceRows implements Iterator<PriceRow> {                                        // (1)!
        private final BufferedReader reader; private PriceRow next; int skipped;
        PriceRows(InputStream gzipped) throws IOException {
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(gzipped), StandardCharsets.UTF_8));
            reader.readLine();                                                                       // header
            advance();
        }
        private void advance() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    Optional<PriceRow> row = parse(line);
                    if (row.isPresent()) { next = row.get(); return; }
                    skipped++;
                }
                next = null; reader.close();
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        public boolean hasNext() { return next != null; }
        public PriceRow next() { if (next == null) throw new NoSuchElementException(); PriceRow r = next; advance(); return r; }
    }

    static Optional<PriceRow> parse(String line) {                                                      // (2)!
        String[] f = line.split(",");
        if (f.length != 3) return Optional.empty();
        try { return Optional.of(new PriceRow(f[0].trim(), f[1].trim(), Math.round(Double.parseDouble(f[2].trim()) * 100))); }
        catch (NumberFormatException e) { return Optional.empty(); }
    }

    static byte[] gzip(String s) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) { gz.write(s.getBytes(StandardCharsets.UTF_8)); }
        return out.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        String upload = "product,unit,price\nspinach,bunch,30\nOkra,kg,60.5\nbeetroot,kg,abc\nApples,kg,180\ncarrot,kg\nbanana,dozen,55\n";
        PriceRows rows = new PriceRows(new ByteArrayInputStream(gzip(upload)));
        List<PriceRow> imported = new java.util.ArrayList<>();
        rows.forEachRemaining(imported::add);

        Collator byName = Collator.getInstance(Locale.ENGLISH);
        Comparator<PriceRow> display = Comparator.comparing(PriceRow::product, byName);                  // (3)!
        imported.stream().sorted(display).forEach(r -> System.out.printf("%-9s %-6s Rs %.2f%n", r.product(), r.unit(), r.paise() / 100.0));
        System.out.println("imported " + imported.size() + ", skipped " + rows.skipped + " bad rows");
    }
}
