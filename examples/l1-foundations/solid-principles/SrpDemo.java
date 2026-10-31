import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** SRP: one class per actor. Finance owns tax, Marketing owns the layout, Platform owns delivery. */
public class SrpDemo {

    record Line(String product, int quantity, BigDecimal unitPrice) {
        Line {
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (unitPrice.signum() < 0) throw new IllegalArgumentException("price must not be negative");
        }
        BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }

    // ===================== BEFORE: three actors, one class =====================
    static final class InvoiceServiceBefore {                                          // (1)!
        final List<String> outbox = new ArrayList<>();

        void sendInvoice(String email, List<Line> lines) {
            BigDecimal net = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = net.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);   // Finance's rule
            String body = "FreshCart invoice | net " + net + " | tax " + tax + " | total " + net.add(tax); // Marketing's layout
            outbox.add(email + " <- " + body);                                                       // Platform's delivery
        }
    }

    // ===================== AFTER: one reason to change per class =====================
    record InvoiceTotals(BigDecimal net, BigDecimal tax, BigDecimal total) { }

    /** Owned by Finance. Changes when tax law changes. */
    static final class InvoiceCalculator {                                             // (2)!
        private final BigDecimal taxRate;
        InvoiceCalculator(BigDecimal taxRate) {
            if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("tax rate must be in [0, 1]");
            this.taxRate = taxRate;
        }
        InvoiceTotals totals(List<Line> lines) {
            if (lines.isEmpty()) throw new IllegalArgumentException("invoice needs at least one line");
            BigDecimal net = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = net.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            return new InvoiceTotals(net, tax, net.add(tax));
        }
    }

    /** Owned by Marketing. Changes when the brand or layout changes. */
    static final class InvoiceRenderer {                                               // (3)!
        String render(String orderId, List<Line> lines, InvoiceTotals t) {
            StringBuilder sb = new StringBuilder("FreshCart invoice " + orderId + "\n");
            for (Line l : lines) sb.append("  %-10s x%d  %8s%n".formatted(l.product(), l.quantity(), l.subtotal()));
            sb.append("  net %s | tax %s | TOTAL %s".formatted(t.net(), t.tax(), t.total()));
            return sb.toString();
        }
    }

    /** Owned by Platform. Changes when the email provider changes. */
    interface InvoiceSender { void send(String email, String body); }                  // (4)!

    /** Coordinates the three; owns none of their rules. */
    static final class InvoiceService {
        private final InvoiceCalculator calculator;
        private final InvoiceRenderer renderer;
        private final InvoiceSender sender;
        InvoiceService(InvoiceCalculator c, InvoiceRenderer r, InvoiceSender s) { calculator = c; renderer = r; sender = s; }

        InvoiceTotals sendInvoice(String orderId, String email, List<Line> lines) {
            InvoiceTotals totals = calculator.totals(lines);
            sender.send(email, renderer.render(orderId, lines, totals));
            return totals;
        }
    }

    public static void main(String[] args) {
        List<Line> lines = List.of(new Line("MILK-1L", 2, new BigDecimal("1.29")), new Line("BREAD-WHT", 1, new BigDecimal("2.49")));

        InvoiceServiceBefore before = new InvoiceServiceBefore();
        before.sendInvoice("asha@example.com", lines);
        System.out.println("BEFORE: " + before.outbox.get(0));

        InvoiceSender console = (email, body) -> System.out.println("AFTER: to " + email + "\n" + body);
        new InvoiceService(new InvoiceCalculator(new BigDecimal("0.05")), new InvoiceRenderer(), console)
                .sendInvoice("FC-8001", "asha@example.com", lines);

        // Finance changes the tax rate: only the calculator's configuration changes.
        InvoiceTotals newTax = new InvoiceCalculator(new BigDecimal("0.12")).totals(lines);
        System.out.println("Tax change only touches InvoiceCalculator: " + newTax);

        try {
            new InvoiceCalculator(new BigDecimal("0.05")).totals(List.of());
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
