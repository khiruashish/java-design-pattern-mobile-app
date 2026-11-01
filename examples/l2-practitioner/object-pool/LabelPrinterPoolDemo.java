import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Minimal Object Pool: a store has 2 label printers; packers borrow one, print, and always give it back. */
public class LabelPrinterPoolDemo {

    record Printer(String id) { String print(String orderId) { return id + " printed label for " + orderId; } }

    static final class PrinterPool {
        private final BlockingQueue<Printer> free = new ArrayBlockingQueue<>(2, true);
        PrinterPool() { free.add(new Printer("printer-A")); free.add(new Printer("printer-B")); }

        /** A lease returns the printer when closed, so try-with-resources can't forget. */
        final class Lease implements AutoCloseable {                                      // (1)!
            final Printer printer;
            Lease(Printer p) { printer = p; }
            @Override public void close() { free.add(printer); }
        }

        Lease borrow() throws InterruptedException { return new Lease(free.take()); }    // (2)!
    }

    public static void main(String[] args) throws InterruptedException {
        PrinterPool pool = new PrinterPool();
        List<String> out = new ArrayList<>();
        for (String order : List.of("FC-2601", "FC-2602", "FC-2603")) {
            try (PrinterPool.Lease lease = pool.borrow()) {
                out.add(lease.printer.print(order));
            }
        }
        out.forEach(System.out::println);
    }
}
