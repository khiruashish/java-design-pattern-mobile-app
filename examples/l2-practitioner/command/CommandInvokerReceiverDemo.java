import java.util.ArrayList;
import java.util.List;

/** Command turns a request into an object: the invoker runs commands without knowing what they do or who does the work. */
public class CommandInvokerReceiverDemo {

    // ===================== Receivers: the objects that actually do the work =====================
    static final class Inventory { void reserve(String sku, int qty) { System.out.println("  inventory: reserved " + qty + " x " + sku); } }
    static final class LabelPrinter { void print(String orderId) { System.out.println("  printer: label for " + orderId); } }

    // ===================== Before: the invoker knows every action and every receiver =====================
    static void handleBefore(String action, Inventory inv, LabelPrinter printer) {
        switch (action) {                                                                     // (1)!
            case "reserve" -> inv.reserve("MILK-1L", 2);
            case "label" -> printer.print("FC-7001");
            default -> throw new IllegalArgumentException(action);
        }
    }

    // ===================== After: commands =====================
    interface Command { void execute(); String describe(); }                                  // (2)!
    record ReserveStock(Inventory inv, String sku, int qty) implements Command {
        public void execute() { inv.reserve(sku, qty); }
        public String describe() { return "reserve " + qty + " x " + sku; }
    }
    record PrintLabel(LabelPrinter printer, String orderId) implements Command {
        public void execute() { printer.print(orderId); }
        public String describe() { return "print label " + orderId; }
    }

    static final class PackingStation {                                                       // (3)!
        final List<String> log = new ArrayList<>();
        void run(List<Command> commands) {
            for (Command c : commands) { log.add(c.describe()); c.execute(); }
        }
    }

    public static void main(String[] args) {
        Inventory inv = new Inventory(); LabelPrinter printer = new LabelPrinter();
        System.out.println("before:");
        handleBefore("reserve", inv, printer);
        System.out.println("after:");
        PackingStation station = new PackingStation();
        station.run(List.of(new ReserveStock(inv, "MILK-1L", 2), new ReserveStock(inv, "BREAD", 1), new PrintLabel(printer, "FC-7001")));
        System.out.println("station log: " + station.log);
    }
}
