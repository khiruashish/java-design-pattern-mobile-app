import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormDraftStoreTest {

    private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
    private final FormDraftStoreDemo.DraftStore store = new FormDraftStoreDemo.DraftStore();

    @Test
    void roundTripsASnapshot() {
        var snap = new FormDraftStoreDemo.OnboardingForm.Snapshot(3, Map.of("storeName", "A", "gstin", "G"));
        store.save("k", snap, now);
        assertEquals(snap, store.load("k", now));
    }

    @Test
    void migratesVersionOneFieldNames() {
        String v1 = "v=1;saved=" + now + ";step=2;gst=G";
        store.storage.put("k", FormDraftStoreDemo.DraftStore.crc(v1) + "|" + v1);
        assertEquals(Map.of("gstin", "G"), store.load("k", now).fields());
    }

    @Test
    void detectsCorruption() {
        store.save("k", new FormDraftStoreDemo.OnboardingForm.Snapshot(1, Map.of("storeName", "Abc")), now);
        store.storage.put("k", store.storage.get("k").replace("Abc", "Abd"));
        assertThrows(IllegalStateException.class, () -> store.load("k", now));
    }

    @Test
    void expiredDraftsAreDiscarded() {
        store.save("k", new FormDraftStoreDemo.OnboardingForm.Snapshot(1, Map.of()), now);
        assertNull(store.load("k", now.plus(Duration.ofDays(31))));
        assertNull(store.storage.get("k"));
    }

    @Test
    void originatorRejectsInvalidSnapshots() {
        assertThrows(IllegalArgumentException.class,
                () -> new FormDraftStoreDemo.OnboardingForm().restore(new FormDraftStoreDemo.OnboardingForm.Snapshot(9, Map.of())));
    }
}
