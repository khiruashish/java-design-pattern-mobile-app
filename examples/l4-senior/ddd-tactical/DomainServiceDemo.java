/** A domain service holds domain logic that doesn't belong to any single entity or value object. */
public class DomainServiceDemo {

    record Money(long paise) { Money minus(Money o) { return new Money(Math.max(0, paise - o.paise)); } }
    record Order(String id, Money paid, Money deliveryFee, int hoursSinceDelivery, boolean memberAtPurchase) {}
    record Complaint(String orderId, String kind, Money itemValue) {}

    /** Refund rules need the order, the complaint and the customer's status: no single entity owns them. */
    static final class RefundPolicy {                                                     // (1)!
        Money refundFor(Order order, Complaint c) {
            if (order.hoursSinceDelivery() > 48) return new Money(0);                      // (2)!
            Money base = switch (c.kind()) {
                case "MISSING", "DAMAGED" -> c.itemValue();
                case "LATE" -> order.deliveryFee();
                default -> new Money(0);
            };
            if (order.memberAtPurchase() && c.kind().equals("LATE")) base = new Money(base.paise() + 5_000);   // (3)!
            return new Money(Math.min(base.paise(), order.paid().paise()));
        }
    }

    public static void main(String[] args) {
        RefundPolicy policy = new RefundPolicy();
        Order order = new Order("FC-40", new Money(51_800), new Money(2_500), 3, true);
        System.out.println("missing curd:  " + policy.refundFor(order, new Complaint("FC-40", "MISSING", new Money(4_500))).paise());
        System.out.println("late delivery: " + policy.refundFor(order, new Complaint("FC-40", "LATE", new Money(0))).paise());
        Order old = new Order("FC-12", new Money(30_000), new Money(2_500), 72, false);
        System.out.println("damaged, 3 days later: " + policy.refundFor(old, new Complaint("FC-12", "DAMAGED", new Money(9_000))).paise());
    }
}
