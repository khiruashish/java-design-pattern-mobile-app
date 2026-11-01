/** Classic GoF Builder: a Director runs the same construction steps against different builders. */
public class GofBuilderDemo {

    interface InvoiceBuilder {                                                            // (1)!
        void header(String orderId);
        void line(String item, int qty, long cents);
        void total(long cents);
        String result();
    }

    static final class TextInvoiceBuilder implements InvoiceBuilder {
        private final StringBuilder out = new StringBuilder();
        public void header(String id) { out.append("INVOICE ").append(id).append('\n'); }
        public void line(String item, int qty, long c) { out.append("  %-6s x%d %6s%n".formatted(item, qty, money(c))); }
        public void total(long c) { out.append("  TOTAL      ").append(money(c)); }
        public String result() { return out.toString(); }
    }

    static final class CsvInvoiceBuilder implements InvoiceBuilder {
        private final StringBuilder out = new StringBuilder("order,item,qty,cents\n");
        private String orderId;
        public void header(String id) { orderId = id; }
        public void line(String item, int qty, long c) { out.append(orderId).append(',').append(item).append(',').append(qty).append(',').append(c).append('\n'); }
        public void total(long c) { out.append(orderId).append(",TOTAL,,").append(c); }
        public String result() { return out.toString(); }
    }

    /** Director: knows the ORDER of steps, not the output format. */
    static final class InvoiceDirector {                                                  // (2)!
        String build(InvoiceBuilder b, String orderId, String[][] lines) {
            b.header(orderId);
            long total = 0;
            for (String[] l : lines) {
                int qty = Integer.parseInt(l[1]); long cents = Long.parseLong(l[2]) * qty;
                b.line(l[0], qty, cents);
                total += cents;
            }
            b.total(total);
            return b.result();                                                            // (3)!
        }
    }

    static String money(long c) { return "$%d.%02d".formatted(c / 100, c % 100); }

    public static void main(String[] args) {
        String[][] lines = {{"MILK", "2", "129"}, {"EGGS", "1", "349"}};
        InvoiceDirector director = new InvoiceDirector();
        System.out.println(director.build(new TextInvoiceBuilder(), "FC-501", lines));
        System.out.println(director.build(new CsvInvoiceBuilder(), "FC-501", lines));
    }
}
