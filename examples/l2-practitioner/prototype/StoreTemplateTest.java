import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreTemplateTest {

    private StoreTemplateDemo.StoreConfig source() {
        var s = new StoreTemplateDemo.StoreConfig("src", "pune", List.of("411001"),
                List.of(new StoreTemplateDemo.Slot("a", 60)), Map.of("picker", 5));
        s.paymentTerminalKey = "secret"; s.ordersToday = 10; s.rating = 4.5;
        return s;
    }

    @Test
    void copyGetsNewIdentityAndNoSecretsOrState() {
        var copy = source().newStoreFromThis(() -> "new-1", "v1");
        assertEquals("new-1", copy.storeId);
        assertNull(copy.paymentTerminalKey);
        assertEquals(0, copy.ordersToday);
        assertEquals("src@v1", copy.templateRef);
    }

    @Test
    void copyIsDeepForMutableCollections() {
        var src = source();
        var copy = src.newStoreFromThis(() -> "new-1", "v1");
        copy.pincodes.add("411002");
        copy.staffByRole.put("picker", 1);
        assertEquals(List.of("411001"), src.pincodes);
        assertEquals(5, src.staffByRole.get("picker"));
    }

    @Test
    void registeredTemplatesAreIsolatedFromLaterSourceEdits() {
        var src = source();
        var registry = new StoreTemplateDemo.TemplateRegistry();
        registry.publish("t", src);
        src.pincodes.add("999999");
        var created = registry.newStore("t", () -> "s1");
        assertEquals(List.of("411001"), created.pincodes);
        assertNotEquals(created.storeId, registry.newStore("t", () -> "s2").storeId);
    }

    @Test
    void newStoreCannotGoLiveWithoutItsOwnKey() {
        var copy = source().newStoreFromThis(() -> "n", "v1");
        assertTrue(copy.problemsBeforeGoLive().contains("payment terminal key not set"));
    }
}
