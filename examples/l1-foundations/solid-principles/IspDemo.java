import java.util.List;

/** ISP: store devices expose only the capabilities they really have. */
public class IspDemo {

    // ===================== BEFORE: one fat interface =====================
    interface StoreDevice {                                                               // (1)!
        String printLabel(String text);
        String scanBarcode();
        int weighGrams();
    }

    static final class LabelPrinterBefore implements StoreDevice {
        public String printLabel(String text) { return "printed [" + text + "]"; }
        public String scanBarcode() { throw new UnsupportedOperationException("printer cannot scan"); }  // (2)!
        public int weighGrams() { throw new UnsupportedOperationException("printer cannot weigh"); }
    }

    // ===================== AFTER: role interfaces =====================
    interface LabelPrinter { String printLabel(String text); }                            // (3)!
    interface BarcodeScanner { String scanBarcode(); }
    interface Scale { int weighGrams(); }

    record ZebraPrinter(String id) implements LabelPrinter {
        public String printLabel(String text) { return id + " printed [" + text + "]"; }
    }
    /** A checkout counter really has all three, so it implements all three. */
    record CheckoutCounter(String id, String nextBarcode, int grams) implements LabelPrinter, BarcodeScanner, Scale {   // (4)!
        public String printLabel(String text) { return id + " printed [" + text + "]"; }
        public String scanBarcode() { return nextBarcode; }
        public int weighGrams() { return grams; }
    }

    /** Clients ask only for what they use. */
    static final class LooseProduceStation {                                               // (5)!
        private final Scale scale;
        private final LabelPrinter printer;
        LooseProduceStation(Scale scale, LabelPrinter printer) { this.scale = scale; this.printer = printer; }

        String weighAndLabel(String product, long centsPerKg) {
            int grams = scale.weighGrams();
            if (grams <= 0) throw new IllegalStateException("scale returned " + grams + " g, re-zero the scale");
            long price = Math.round(grams * centsPerKg / 1000.0);
            return printer.printLabel("%s %d g $%d.%02d".formatted(product, grams, price / 100, price % 100));
        }
    }

    public static void main(String[] args) {
        StoreDevice old = new LabelPrinterBefore();
        System.out.println("before: " + old.printLabel("BANANA"));
        try { old.weighGrams(); } catch (UnsupportedOperationException e) { System.out.println("before: runtime surprise -> " + e.getMessage()); }

        var counter = new CheckoutCounter("counter-3", "8901234567890", 1_250);
        var station = new LooseProduceStation(counter, new ZebraPrinter("zebra-1"));
        System.out.println("after:  " + station.weighAndLabel("BANANA", 129));
        System.out.println("after:  scanned " + counter.scanBarcode());

        for (Scale broken : List.<Scale>of(() -> 0)) {
            try { new LooseProduceStation(broken, new ZebraPrinter("zebra-1")).weighAndLabel("APPLE", 300); }
            catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
