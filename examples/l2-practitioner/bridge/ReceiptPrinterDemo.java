import java.util.List;

/** Minimal Bridge: receipt TYPE (short or detailed) x receipt FORMAT (plain text or HTML). */
public class ReceiptPrinterDemo {

    interface Format { String line(String label, String value); String wrap(String content); }   // (1)!
    record PlainText() implements Format {
        public String line(String l, String v) { return String.format("%-10s %8s%n", l, v); }
        public String wrap(String c) { return c.stripTrailing(); }
    }
    record Html() implements Format {
        public String line(String l, String v) { return "<tr><td>" + l + "</td><td>" + v + "</td></tr>"; }
        public String wrap(String c) { return "<table>" + c + "</table>"; }
    }

    record Item(String name, long paise) { }

    abstract static class Receipt {                                                          // (2)!
        final Format f; Receipt(Format f) { this.f = f; }
        abstract String print(List<Item> items);
        static String rs(long p) { return String.format("%.2f", p / 100.0); }
    }
    static final class ShortReceipt extends Receipt {
        ShortReceipt(Format f) { super(f); }
        String print(List<Item> items) { return f.wrap(f.line("TOTAL", rs(items.stream().mapToLong(Item::paise).sum()))); }
    }
    static final class DetailedReceipt extends Receipt {
        DetailedReceipt(Format f) { super(f); }
        String print(List<Item> items) {
            StringBuilder sb = new StringBuilder();
            items.forEach(i -> sb.append(f.line(i.name(), rs(i.paise()))));
            sb.append(f.line("TOTAL", rs(items.stream().mapToLong(Item::paise).sum())));
            return f.wrap(sb.toString());
        }
    }

    public static void main(String[] args) {
        List<Item> items = List.of(new Item("Milk", 6800), new Item("Bread", 4500));
        System.out.println(new DetailedReceipt(new PlainText()).print(items));
        System.out.println(new ShortReceipt(new Html()).print(items));
    }
}
