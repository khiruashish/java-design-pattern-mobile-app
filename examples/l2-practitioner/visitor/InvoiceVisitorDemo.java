import java.util.List;

/** Realistic Visitor: one cart model, several independent operations (GST, invoice lines, CSV export), each in one place. */
public class InvoiceVisitorDemo {

    sealed interface Line permits Loose, Packaged, Subscription, GiftCard { }
    record Loose(String name, int grams, long paisePerKg) implements Line { }
    record Packaged(String name, int qty, long unitPaise, int gstPercent) implements Line { }
    record Subscription(String plan, long monthlyPaise) implements Line { }
    record GiftCard(String code, long valuePaise) implements Line { }

    static long net(Line l) {                                                                       // (1)!
        return switch (l) {
            case Loose x -> x.paisePerKg() * x.grams() / 1000;
            case Packaged p -> p.unitPaise() * p.qty();
            case Subscription s -> s.monthlyPaise();
            case GiftCard g -> g.valuePaise();
        };
    }

    static long gst(Line l) {
        return switch (l) {
            case Loose x -> 0;                                                                      // unbranded fresh produce: exempt
            case Packaged p -> net(p) * p.gstPercent() / 100;
            case Subscription s -> net(s) * 18 / 100;                                               // services: 18%
            case GiftCard g -> 0;                                                                   // taxed when redeemed
        };
    }

    static String csv(Line l) {                                                                     // (2)!
        String kind = switch (l) { case Loose x -> "loose"; case Packaged p -> "packaged"; case Subscription s -> "subscription"; case GiftCard g -> "giftcard"; };
        String name = switch (l) { case Loose x -> x.name(); case Packaged p -> p.name() + " x" + p.qty(); case Subscription s -> s.plan(); case GiftCard g -> "card " + g.code().substring(0, 4) + "****"; };   // (3)!
        return String.join(",", kind, name, String.valueOf(net(l)), String.valueOf(gst(l)));
    }

    public static void main(String[] args) {
        List<Line> order = List.of(new Loose("Tomatoes", 750, 4_000), new Packaged("Atta 5kg", 1, 25_000, 5),
                new Subscription("FreshPass monthly", 9_900), new GiftCard("GC8842113", 50_000));
        System.out.println("kind,name,net_paise,gst_paise");
        order.forEach(l -> System.out.println(csv(l)));
        System.out.printf("total Rs %.2f incl. GST Rs %.2f%n", order.stream().mapToLong(l -> net(l) + gst(l)).sum() / 100.0, order.stream().mapToLong(InvoiceVisitorDemo::gst).sum() / 100.0);
    }
}
