import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplierImportTest {

    static final SupplierImportDemo.ImportConfig CFG = new SupplierImportDemo.ImportConfig(20, 500_000);

    static SupplierImportDemo.ImportReport run(String csv, Map<String, Long> catalogue) {
        return SupplierImportDemo.runImport(new BufferedReader(new StringReader(csv)), CFG, catalogue);
    }

    @Test
    void rowCollectsAllItsProblems() {
        var result = SupplierImportDemo.parseRow(7, "bad sku, -3", CFG);
        var bad = assertInstanceOf(SupplierImportDemo.Bad.class, result);
        assertEquals(List.of("E_SKU", "E_PRICE_RANGE"), bad.errors().stream().map(SupplierImportDemo.RowError::code).toList());
    }

    @Test
    void overBudgetAppliesNothing() {
        Map<String, Long> catalogue = new HashMap<>(Map.of("A-1", 100L));
        var report = run("sku,price\nA-1,2\nx,1\ny,1\n", catalogue);
        assertInstanceOf(SupplierImportDemo.Aborted.class, report);
        assertEquals(100L, catalogue.get("A-1"));
    }

    @Test
    void withinBudgetAppliesGoodRowsAndReportsBadOnes() {
        Map<String, Long> catalogue = new HashMap<>();
        StringBuilder csv = new StringBuilder("sku,price\n");
        for (int i = 0; i < 9; i++) csv.append("SKU-").append(i).append(",10\n");
        csv.append("bad,10\n");
        var applied = assertInstanceOf(SupplierImportDemo.Applied.class, run(csv.toString(), catalogue));
        assertEquals(9, applied.applied());
        assertEquals(1, applied.rejected().size());
        assertEquals(1000L, catalogue.get("SKU-0"));
    }

    @Test
    void ioFailuresAreTranslatedAndTheReaderIsClosed() {
        boolean[] closed = {false};
        BufferedReader failing = new BufferedReader(new StringReader("")) {
            @Override public String readLine() throws IOException { throw new IOException("reset"); }
            @Override public void close() { closed[0] = true; }
        };
        var e = assertThrows(SupplierImportDemo.ImportFailedException.class,
                () -> SupplierImportDemo.runImport(failing, CFG, new HashMap<>()));
        assertInstanceOf(IOException.class, e.getCause());
        assertTrue(closed[0]);
    }

    @Test
    void badConfigFailsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new SupplierImportDemo.ImportConfig(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SupplierImportDemo.ImportConfig(10, 0));
    }

    @Test
    void paymentsRetryOnlyRetryableErrorsAndAtMostThreeTimes() {
        int[] calls = {0};
        List<String> log = new ArrayList<>();
        PaymentErrorHandlingDemo.PayLinkClient alwaysBusy = (k, p) -> { calls[0]++; throw new PaymentErrorHandlingDemo.PayLinkHttpException(503, "unavailable"); };
        assertInstanceOf(PaymentErrorHandlingDemo.TryAgainLater.class, PaymentErrorHandlingDemo.pay(alwaysBusy, "O", 1, log));
        assertEquals(3, calls[0]);

        calls[0] = 0;
        PaymentErrorHandlingDemo.PayLinkClient declined = (k, p) -> { calls[0]++; throw new PaymentErrorHandlingDemo.PayLinkHttpException(402, "card_expired"); };
        assertEquals(new PaymentErrorHandlingDemo.Declined("This card has expired"), PaymentErrorHandlingDemo.pay(declined, "O", 1, log));
        assertEquals(1, calls[0]);
    }

    @Test
    void notificationReportsEveryField() {
        var n = NotificationPatternDemo.validate(new NotificationPatternDemo.AddressForm("", "1", "1", ""));
        assertEquals(4, n.errors().size());
    }
}
