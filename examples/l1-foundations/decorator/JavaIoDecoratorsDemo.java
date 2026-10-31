import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** java.io is the textbook Decorator: each stream wraps another and adds one capability. */
public class JavaIoDecoratorsDemo {

    /** Our own decorator: counts bytes read through it. */
    static final class CountingInputStream extends FilterInputStream {                   // (1)!
        long count;
        CountingInputStream(InputStream in) { super(in); }
        @Override public int read() throws IOException { int b = super.read(); if (b >= 0) count++; return b; }
        @Override public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len); if (n > 0) count += n; return n;
        }
    }

    public static void main(String[] args) throws IOException {
        String csv = "sku,qty\n" + "MILK-1L,42\n".repeat(200);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (BufferedWriter out = new BufferedWriter(                                     // (2)!
                new OutputStreamWriter(new GZIPOutputStream(file), StandardCharsets.UTF_8))) {
            out.write(csv);
        }
        System.out.println("raw " + csv.getBytes(StandardCharsets.UTF_8).length + " bytes, gzipped " + file.size() + " bytes");

        CountingInputStream counter = new CountingInputStream(new ByteArrayInputStream(file.toByteArray()));
        try (BufferedReader in = new BufferedReader(                                      // (3)!
                new InputStreamReader(new GZIPInputStream(counter), StandardCharsets.UTF_8))) {
            long lines = in.lines().count();
            System.out.println("read back " + lines + " lines; compressed bytes read: " + counter.count);
        }
    }
}
