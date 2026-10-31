import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvoiceArchiveTest {

    /** Every BlobStore implementor must pass the same contract; the abstraction relies on it. */
    abstract static class BlobStoreContract {
        abstract InvoiceArchiveDemo.BlobStore store();

        @Test
        void returnsWhatWasStored() {
            var s = store();
            s.put("a/b.txt", new byte[] {1, 2, 3});
            assertArrayEquals(new byte[] {1, 2, 3}, s.get("a/b.txt").orElseThrow());
        }

        @Test
        void missingKeyIsEmptyNotAnError() {
            assertTrue(store().get("nope").isEmpty());
        }

        @Test
        void callersCannotMutateStoredBytes() {
            var s = store();
            byte[] data = {9};
            s.put("k", data);
            data[0] = 0;
            assertEquals(9, s.get("k").orElseThrow()[0]);
        }
    }

    @Nested class InMemory extends BlobStoreContract {
        InvoiceArchiveDemo.BlobStore store() { return new InvoiceArchiveDemo.InMemoryBlobStore(); }
    }

    @Nested class FlakyAfterRecovery extends BlobStoreContract {
        InvoiceArchiveDemo.BlobStore store() { return new InvoiceArchiveDemo.FlakyObjectStore(0); }
    }

    @Test
    void archiveRetriesTransientFailures() {
        var archive = new InvoiceArchiveDemo.InvoiceArchive(new InvoiceArchiveDemo.FlakyObjectStore(2), 3);
        assertTrue(archive.save("FC-2026-11-0001", "x").endsWith("(3 attempts)"));
    }

    @Test
    void archiveDetectsCorruption() {
        var raw = new InvoiceArchiveDemo.InMemoryBlobStore();
        var archive = new InvoiceArchiveDemo.InvoiceArchive(raw, 1);
        String key = archive.save("FC-2026-11-0002", "Rs 100").replaceAll(" \\(.*", "");
        raw.put(key, "Rs 1".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> archive.load(key));
    }
}
