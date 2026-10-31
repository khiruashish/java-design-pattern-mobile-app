import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Production-grade Bridge: invoice archiving (abstraction) over blob storage back ends (implementor).
 * - the implementor interface is SMALL and primitive (put/get bytes); policies live in the abstraction
 * - the back end is chosen from a config URL at the composition root (like JDBC URLs)
 * - the abstraction adds retries, checksums and naming rules ONCE for every back end
 * - a refined abstraction (AuditedInvoiceArchive) adds behaviour without touching any back end
 */
public class InvoiceArchiveDemo {

    // ===================== Implementor: primitive storage operations =====================
    interface BlobStore {                                                                    // (1)!
        void put(String key, byte[] data);
        Optional<byte[]> get(String key);
        String name();
    }

    static final class InMemoryBlobStore implements BlobStore {
        final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
        public void put(String k, byte[] d) { blobs.put(k, d.clone()); }
        public Optional<byte[]> get(String k) { return Optional.ofNullable(blobs.get(k)).map(byte[]::clone); }
        public String name() { return "memory"; }
    }

    /** Simulates an object store that sometimes times out (first N puts fail). */
    static final class FlakyObjectStore implements BlobStore {
        final InMemoryBlobStore inner = new InMemoryBlobStore(); int failuresLeft;
        FlakyObjectStore(int failures) { failuresLeft = failures; }
        public void put(String k, byte[] d) { if (failuresLeft-- > 0) throw new IllegalStateException("timeout talking to object store"); inner.put(k, d); }
        public Optional<byte[]> get(String k) { return inner.get(k); }
        public String name() { return "object-store"; }
    }

    static final Map<String, Function<String, BlobStore>> BACKENDS = Map.of(                  // (2)!
            "mem", url -> new InMemoryBlobStore(),
            "flaky", url -> new FlakyObjectStore(Integer.parseInt(url.substring(url.indexOf("failures=") + 9))));

    static BlobStore storeFor(String url) {
        String scheme = url.substring(0, url.indexOf("://"));
        Function<String, BlobStore> f = BACKENDS.get(scheme);
        if (f == null) throw new IllegalArgumentException("no blob store for scheme '" + scheme + "'");
        return f.apply(url);
    }

    // ===================== Abstraction: invoice archiving rules =====================
    static class InvoiceArchive {
        protected final BlobStore store; private final int maxAttempts;
        InvoiceArchive(BlobStore store, int maxAttempts) { this.store = store; this.maxAttempts = maxAttempts; }

        String save(String orderId, String invoiceText) {
            String key = "invoices/" + orderId.substring(3, 7) + "/" + orderId + ".txt";       // naming rule lives here, not in back ends
            byte[] data = invoiceText.getBytes(StandardCharsets.UTF_8);
            String checksum = sha256(data);
            for (int attempt = 1; ; attempt++) {                                              // (3)!
                try {
                    store.put(key, data);
                    store.put(key + ".sha256", checksum.getBytes(StandardCharsets.UTF_8));
                    return key + " (" + attempt + " attempt" + (attempt > 1 ? "s" : "") + ")";
                } catch (IllegalStateException e) {
                    if (attempt == maxAttempts) throw new IllegalStateException("could not archive " + orderId + " on " + store.name() + ": " + e.getMessage(), e);
                }
            }
        }

        String load(String key) {
            byte[] data = store.get(key).orElseThrow(() -> new IllegalArgumentException("no invoice " + key));
            String expected = new String(store.get(key + ".sha256").orElseThrow(), StandardCharsets.UTF_8);
            if (!sha256(data).equals(expected)) throw new IllegalStateException("checksum mismatch for " + key);   // (4)!
            return new String(data, StandardCharsets.UTF_8);
        }

        static String sha256(byte[] d) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d)).substring(0, 16); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
    }

    // ===================== Refined abstraction: adds an audit trail for finance =====================
    static final class AuditedInvoiceArchive extends InvoiceArchive {                         // (5)!
        final List<String> audit = new ArrayList<>();
        AuditedInvoiceArchive(BlobStore s, int attempts) { super(s, attempts); }
        @Override String save(String orderId, String text) { String r = super.save(orderId, text); audit.add("saved " + orderId + " to " + store.name()); return r; }
        @Override String load(String key) { audit.add("read " + key); return super.load(key); }
    }

    public static void main(String[] args) {
        InvoiceArchive dev = new InvoiceArchive(storeFor("mem://local"), 3);
        String key = dev.save("FC-2026-11-0042", "Invoice FC-2026-11-0042: Rs 649.00");
        System.out.println("dev saved " + key);

        AuditedInvoiceArchive prod = new AuditedInvoiceArchive(storeFor("flaky://bucket?failures=2"), 3);
        String saved = prod.save("FC-2026-11-0043", "Invoice FC-2026-11-0043: Rs 1,197.45");
        System.out.println("prod saved " + saved);
        String k2 = saved.replaceAll(" \\(.*", "");
        System.out.println("prod load: " + prod.load(k2));
        System.out.println("audit: " + prod.audit);

        InMemoryBlobStore raw = new InMemoryBlobStore();
        InvoiceArchive tampered = new InvoiceArchive(raw, 1);
        String k3 = tampered.save("FC-2026-11-0045", "Invoice: Rs 100").replaceAll(" \\(.*", "");
        raw.put(k3, "Invoice: Rs 1".getBytes(StandardCharsets.UTF_8));
        try { tampered.load(k3); } catch (IllegalStateException e) { System.out.println("tamper detected: " + e.getMessage()); }

        try { new InvoiceArchive(storeFor("flaky://bucket?failures=5"), 3).save("FC-2026-11-0046", "x"); }
        catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        try { storeFor("ftp://old-server"); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
