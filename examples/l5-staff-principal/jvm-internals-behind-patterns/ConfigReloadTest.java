import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReloadTest {

    static ConfigReloadDemo.ConfigStore store() {
        return new ConfigReloadDemo.ConfigStore(new ConfigReloadDemo.PricingConfig(1, Map.of("MILK-1L", 6800L), 2500));
    }

    @Test
    void validConfigIsPublishedAndListenersNotified() {
        var s = store();
        List<Integer> seen = new ArrayList<>();
        s.onChange(c -> seen.add(c.version()));
        assertEquals("now serving v2", s.reload(2, Map.of("MILK-1L", 7000L), 2000));
        assertEquals(2, s.snapshot().version());
        assertEquals(List.of(2), seen);
    }

    @Test
    void invalidConfigIsRejectedAndOldOneKept() {
        var s = store();
        assertTrue(s.reload(2, Map.of("MILK-1L", 0L), 2500).startsWith("rejected"));
        assertEquals(1, s.snapshot().version());
    }

    @Test
    void staleVersionsAreIgnored() {
        var s = store();
        s.reload(3, Map.of("MILK-1L", 7000L), 2500);
        assertEquals("ignored stale v2", s.reload(2, Map.of("MILK-1L", 7100L), 2500));
    }

    @Test
    void snapshotIsImmutableEvenIfCallerMutatesItsMap() {
        var s = store();
        Map<String, Long> input = new HashMap<>(Map.of("MILK-1L", 7000L));
        s.reload(2, input, 2500);
        input.put("MILK-1L", 1L);
        assertEquals(7000L, s.snapshot().prices().get("MILK-1L"));
        assertThrows(UnsupportedOperationException.class, () -> s.snapshot().prices().put("X", 1L));
    }

    @Test
    void requestUsesOneSnapshot() {
        var s = store();
        assertEquals(6800L * 2 + 2500, ConfigReloadDemo.basketTotal(s));
    }
}
