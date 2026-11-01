import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** java.io is the textbook Decorator: each stream wraps another and adds one ability. InputStreamReader is an Adapter. */
public class IoDecoratorsDemo {

    /** Our own decorator: counts bytes passing through, without changing them. */
    static final class CountingInputStream extends FilterInputStream {                                  // (1)!
        long count;
        CountingInputStream(InputStream in) { super(in); }
        @Override public int read() throws IOException { int b = super.read(); if (b >= 0) count++; return b; }
        @Override public int read(byte[] buf, int off, int len) throws IOException { int n = super.read(buf, off, len); if (n > 0) count += n; return n; }
    }

    static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) { gz.write(text.getBytes(StandardCharsets.UTF_8)); }
        return bytes.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        StringBuilder csv = new StringBuilder("sku,price\n");
        for (int i = 1; i <= 300; i++) csv.append("SKU-").append(i).append(",").append(40 + i % 50).append("\n");
        byte[] compressed = gzip(csv.toString());
        CountingInputStream onTheWire = new CountingInputStream(new ByteArrayInputStream(compressed));
        CountingInputStream afterUnzip;
        try (BufferedReader reader = new BufferedReader(                                                  // (2)!
                new InputStreamReader(                                                                  // (3)!
                        afterUnzip = new CountingInputStream(new GZIPInputStream(onTheWire)), StandardCharsets.UTF_8))) {
            System.out.println("header: " + reader.readLine() + ", first row: " + reader.readLine() + ", remaining rows: " + reader.lines().count());
        }
        System.out.println("decompressed bytes: " + afterUnzip.count + ", compressed bytes on the wire under a third of that: " + (onTheWire.count < afterUnzip.count / 3));
    }
}
