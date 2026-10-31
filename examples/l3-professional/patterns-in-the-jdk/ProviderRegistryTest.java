import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {

    private static ProviderRegistryDemo.Registry registry(Class<?>... impls) throws Exception {
        return new ProviderRegistryDemo.Registry(ProviderRegistryDemo.registering(List.of(impls)), List.of("IN"));
    }

    @Test
    void candidatesAreOrderedByPriority() throws Exception {
        var r = registry(ProviderRegistryDemo.GlobalSms.class, ProviderRegistryDemo.LegacyGateway.class, ProviderRegistryDemo.TextBlastIndia.class);
        assertEquals(List.of("textblast-in", "globalsms", "expensive-legacy"), r.candidates("IN"));
    }

    @Test
    void providersAreInstantiatedOnlyWhenUsed() throws Exception {
        var r = registry(ProviderRegistryDemo.TextBlastIndia.class, ProviderRegistryDemo.LegacyGateway.class);
        r.send("IN", "+911", "hi");
        assertTrue(r.instances.containsKey("textblast-in"));
        assertFalse(r.instances.containsKey("expensive-legacy"));
    }

    @Test
    void proxyCountsCallsAndFailuresAndFallsBack() throws Exception {
        var r = new ProviderRegistryDemo.Registry(
                ProviderRegistryDemo.registering(List.of(ProviderRegistryDemo.GlobalSms.class)), List.of("SG"));
        assertThrows(IllegalStateException.class, () -> r.send("SG", "+651", "hi"));
        assertEquals(1, r.stats.get("globalsms").failures().get());
    }

    @Test
    void startupFailsOnDuplicateIdsOrMissingCoverage() {
        assertThrows(IllegalStateException.class, () -> registry(ProviderRegistryDemo.TextBlastIndia.class, ProviderRegistryDemo.TextBlastIndiaV2.class));
        assertThrows(IllegalStateException.class, () -> new ProviderRegistryDemo.Registry(
                ProviderRegistryDemo.registering(List.of(ProviderRegistryDemo.TextBlastIndia.class)), List.of("AE")));
    }

    @Test
    void lazyPriceIteratorSkipsBadRows() throws Exception {
        byte[] gz = SupplierPriceImportDemo.gzip("h\nokra,kg,60\nbad\nkale,kg,x\nleek,kg,90\n");
        var rows = new SupplierPriceImportDemo.PriceRows(new ByteArrayInputStream(gz));
        List<SupplierPriceImportDemo.PriceRow> out = new ArrayList<>();
        rows.forEachRemaining(out::add);
        assertEquals(List.of("okra", "leek"), out.stream().map(SupplierPriceImportDemo.PriceRow::product).toList());
        assertEquals(2, rows.skipped);
    }
}
