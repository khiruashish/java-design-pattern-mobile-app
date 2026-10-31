/** Chain of Responsibility: a request travels along linked handlers until one handles it. */
public class LinkedHandlersDemo {

    record RefundRequest(String orderId, long paise) { }

    // ===================== Before: one method knows every level and every limit =====================
    static String approveBefore(RefundRequest r) {
        if (r.paise() <= 20_000) return "bot";                                                   // (1)!
        else if (r.paise() <= 100_000) return "agent";
        else if (r.paise() <= 500_000) return "supervisor";
        else return "finance";
    }

    // ===================== After: each handler decides, or passes on =====================
    abstract static class Approver {
        private Approver next;
        Approver then(Approver next) { this.next = next; return next; }                         // (2)!
        final String handle(RefundRequest r) {
            if (canApprove(r)) return name() + " approved Rs " + r.paise() / 100 + " for " + r.orderId();
            if (next == null) return "nobody could approve " + r.orderId();                       // (3)!
            return next.handle(r);
        }
        abstract boolean canApprove(RefundRequest r);
        abstract String name();
    }
    static final class Bot extends Approver { boolean canApprove(RefundRequest r) { return r.paise() <= 20_000; } String name() { return "bot"; } }
    static final class Agent extends Approver { boolean canApprove(RefundRequest r) { return r.paise() <= 100_000; } String name() { return "agent"; } }
    static final class Supervisor extends Approver { boolean canApprove(RefundRequest r) { return r.paise() <= 500_000; } String name() { return "supervisor"; } }

    public static void main(String[] args) {
        System.out.println("before: " + approveBefore(new RefundRequest("FC-1", 50_000)));
        Approver chain = new Bot();
        chain.then(new Agent()).then(new Supervisor());                                          // (4)!
        for (long p : new long[] {15_000, 64_900, 300_000, 900_000}) System.out.println(chain.handle(new RefundRequest("FC-" + p / 100, p)));
    }
}
