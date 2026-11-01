import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceModernisationTest {

    static final Function<InvoiceModernisationDemo.Order, String> LEGACY =
            o -> String.format("INV%-8s%09d%07d", o.id(), o.netPaise(), o.netPaise() * 5 / 100);
    static final Function<InvoiceModernisationDemo.Order, InvoiceModernisationDemo.Invoice> CORRECT =
            o -> new InvoiceModernisationDemo.Invoice(o.id(), o.netPaise(), Math.round(o.netPaise() * 0.05));

    static InvoiceModernisationDemo.Order order(String id, String segment) { return new InvoiceModernisationDemo.Order(id, segment, 10_010); }

    @Test
    void aclParsesLegacyFixedWidthRecords() {
        assertEquals(new InvoiceModernisationDemo.Invoice("FC-9", 123456, 6172), InvoiceModernisationDemo.fromLegacy("INVFC-9    0001234560006172"));
    }

    @Test
    void segmentsDefaultToLegacy() {
        int[] modernCalls = {0};
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, o -> { modernCalls[0]++; return CORRECT.apply(o); });
        m.invoice(order("A", "B2C"));
        assertEquals(0, modernCalls[0]);
    }

    @Test
    void shadowServesLegacyAndToleratesOnePaiseRounding() {
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, CORRECT);
        m.modes.put("B2C", InvoiceModernisationDemo.Mode.SHADOW);
        var served = m.invoice(order("A", "B2C"));
        assertEquals(500, served.gstPaise());                 // legacy truncates 500.5 to 500
        assertEquals(0, m.shadowStats.get("B2C")[1]);
    }

    @Test
    void promotionNeedsEnoughCleanSamples() {
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, CORRECT);
        m.modes.put("B2C", InvoiceModernisationDemo.Mode.SHADOW);
        for (int i = 0; i < 3; i++) m.invoice(order("A" + i, "B2C"));
        assertTrue(m.promote("B2C", 5).contains("only 3"));
        for (int i = 3; i < 5; i++) m.invoice(order("A" + i, "B2C"));
        assertTrue(m.promote("B2C", 5).contains("promoted"));
        assertEquals(InvoiceModernisationDemo.Mode.NEW, m.modes.get("B2C"));
    }

    @Test
    void failuresInNewModeFallBackAndRollBack() {
        boolean[] down = {false};
        var m = new InvoiceModernisationDemo.Migrator(LEGACY, o -> { if (down[0]) throw new IllegalStateException("503"); return CORRECT.apply(o); });
        m.modes.put("B2C", InvoiceModernisationDemo.Mode.NEW);
        down[0] = true;
        for (int i = 0; i < 3; i++) assertEquals(500, m.invoice(order("A" + i, "B2C")).gstPaise());   // legacy served
        assertEquals(InvoiceModernisationDemo.Mode.NEW, m.modes.get("B2C"));                          // not enough samples yet
        m.invoice(order("A3", "B2C"));
        assertEquals(InvoiceModernisationDemo.Mode.SHADOW, m.modes.get("B2C"));
    }
}
