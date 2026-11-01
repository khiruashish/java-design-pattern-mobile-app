import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** Checked exceptions for recoverable conditions the caller must handle; unchecked for bugs and unrecoverable failures. */
public class CheckedVsUncheckedDemo {

    /** Recoverable and expected: the caller can do something sensible (try another warehouse). */
    static final class OutOfStockException extends Exception {             // (1)!
        final String sku;
        OutOfStockException(String sku) { super(sku + " is out of stock"); this.sku = sku; }
    }

    static int reserve(Map<String, Integer> stock, String sku, int qty) throws OutOfStockException {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive, got " + qty);   // (2)!
        int left = stock.getOrDefault(sku, 0);
        if (left < qty) throw new OutOfStockException(sku);
        stock.put(sku, left - qty);
        return left - qty;
    }

    /** Lambdas can't throw checked exceptions, so wrap them at the boundary, keeping the cause. */
    static String readLabel(String sku) {
        try {
            if (sku.startsWith("BAD")) throw new IOException("label printer offline");
            return "LABEL-" + sku;
        } catch (IOException e) {
            throw new UncheckedIOException(e);                                // (3)!
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> koramangala = new java.util.HashMap<>(Map.of("CURD-400G", 1));
        Map<String, Integer> indiranagar = new java.util.HashMap<>(Map.of("CURD-400G", 9));
        try {
            reserve(koramangala, "CURD-400G", 2);
        } catch (OutOfStockException e) {
            System.out.println("recovered: " + e.getMessage() + ", trying Indiranagar");
            try {
                System.out.println("Indiranagar left: " + reserve(indiranagar, "CURD-400G", 2));
            } catch (OutOfStockException again) {
                System.out.println("no stock anywhere");
            }
        }
        try {
            reserve(indiranagar, "CURD-400G", -1);
        } catch (OutOfStockException e) {
            System.out.println("unreachable");
        } catch (IllegalArgumentException bug) {
            System.out.println("caller bug: " + bug.getMessage());
        }
        try {
            List.of("MILK", "BAD-1").stream().map(CheckedVsUncheckedDemo::readLabel).toList();
        } catch (UncheckedIOException e) {
            System.out.println("wrapped: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
        }
    }
}
