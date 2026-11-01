import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Production-grade persisted mementos: a new store's onboarding form (many steps) is saved as a draft so the manager can
 * resume on another device. The memento is serialised with:
 * - a schema VERSION, and a migration from older versions on restore
 * - a checksum to detect corruption, a size limit, and an expiry (drafts older than 30 days are discarded)
 * - only the form's own data; restore goes through the originator, which validates
 */
public class FormDraftStoreDemo {

    static final class OnboardingForm {                                                             // originator
        private final Map<String, String> fields = new LinkedHashMap<>();
        private int step = 1;
        void fill(String field, String value) { fields.put(field, value); }
        void next() { step++; }

        record Snapshot(int step, Map<String, String> fields) { }
        Snapshot snapshot() { return new Snapshot(step, Map.copyOf(fields)); }
        void restore(Snapshot s) {                                                                   // (1)!
            if (s.step() < 1 || s.step() > 5) throw new IllegalArgumentException("invalid step " + s.step());
            fields.clear(); fields.putAll(new java.util.TreeMap<>(s.fields())); step = s.step();
        }
        public String toString() { return "step " + step + " " + new java.util.TreeMap<>(fields); }
    }

    static final class DraftStore {                                                                  // caretaker
        static final int VERSION = 2, MAX_BYTES = 2_048;
        static final Duration TTL = Duration.ofDays(30);
        final Map<String, String> storage = new HashMap<>();                                         // pretend: a database table

        void save(String key, OnboardingForm.Snapshot s, Instant now) {
            StringBuilder body = new StringBuilder("v=" + VERSION + ";saved=" + now + ";step=" + s.step());
            new java.util.TreeMap<>(s.fields()).forEach((k, v) -> body.append(';').append(k).append('=').append(v.replace(";", ",")));
            String payload = body.toString();
            if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("draft too large");   // (2)!
            storage.put(key, crc(payload) + "|" + payload);
        }

        OnboardingForm.Snapshot load(String key, Instant now) {
            String raw = storage.get(key);
            if (raw == null) return null;
            String crc = raw.substring(0, raw.indexOf('|')), payload = raw.substring(raw.indexOf('|') + 1);
            if (!crc.equals(crc(payload))) throw new IllegalStateException("draft " + key + " is corrupted");   // (3)!
            Map<String, String> kv = new LinkedHashMap<>();
            for (String part : payload.split(";")) { int eq = part.indexOf('='); kv.put(part.substring(0, eq), part.substring(eq + 1)); }
            if (Instant.parse(kv.remove("saved")).plus(TTL).isBefore(now)) { storage.remove(key); return null; }   // (4)!
            int version = Integer.parseInt(kv.remove("v"));
            if (version == 1 && kv.containsKey("gst")) kv.put("gstin", kv.remove("gst"));            // (5)!
            int step = Integer.parseInt(kv.remove("step"));
            return new OnboardingForm.Snapshot(step, kv);
        }

        static String crc(String s) { CRC32 c = new CRC32(); c.update(s.getBytes(StandardCharsets.UTF_8)); return Long.toHexString(c.getValue()); }
    }

    public static void main(String[] args) {
        Instant now = Instant.parse("2026-11-02T10:00:00Z");
        DraftStore store = new DraftStore();

        OnboardingForm phone = new OnboardingForm();
        phone.fill("storeName", "FreshCart Whitefield"); phone.next(); phone.fill("gstin", "29ABCDE1234F1Z5"); phone.next();
        store.save("store-77", phone.snapshot(), now);

        OnboardingForm laptop = new OnboardingForm();
        laptop.restore(store.load("store-77", now.plus(Duration.ofDays(2))));
        System.out.println("resumed on laptop: " + laptop);

        String v1 = "v=1;saved=" + now + ";step=2;storeName=Old Store;gst=29XYZ";
        store.storage.put("store-12", DraftStore.crc(v1) + "|" + v1);
        System.out.println("migrated v1 draft: " + store.load("store-12", now).fields());

        store.storage.put("store-13", store.storage.get("store-77").replace("Whitefield", "Whitef1eld"));
        try { store.load("store-13", now); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }

        System.out.println("40-day-old draft: " + store.load("store-77", now.plus(Duration.ofDays(40))) + ", still stored: " + store.storage.containsKey("store-77"));
        try { phone.fill("notes", "x".repeat(3_000)); store.save("store-78", phone.snapshot(), now); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
