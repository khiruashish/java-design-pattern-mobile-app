import java.util.List;

/** Java picks overloads by the STATIC type at compile time. Visitor uses two virtual calls ("double dispatch") to get the runtime type. */
public class DoubleDispatchDemo {

    interface Item { String accept(LabelPrinter p); }
    record LooseProduce(String name, int grams) implements Item {
        public String accept(LabelPrinter p) { return p.print(this); }                           // (1)!
    }
    record Packaged(String name, String barcode) implements Item {
        public String accept(LabelPrinter p) { return p.print(this); }
    }

    static final class LabelPrinter {
        String print(Item i) { return "generic label"; }
        String print(LooseProduce l) { return "weigh-and-print label: " + l.name() + " " + l.grams() + " g"; }
        String print(Packaged p) { return "barcode label: " + p.barcode(); }
    }

    public static void main(String[] args) {
        LabelPrinter printer = new LabelPrinter();
        List<Item> items = List.of(new LooseProduce("Tomatoes", 750), new Packaged("Atta 5kg", "8901234567890"));

        for (Item i : items) System.out.println("overload only: " + printer.print(i));           // (2)!
        for (Item i : items) System.out.println("double dispatch: " + i.accept(printer));        // (3)!
    }
}
