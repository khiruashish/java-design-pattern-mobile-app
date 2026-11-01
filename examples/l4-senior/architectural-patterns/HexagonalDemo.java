import java.util.ArrayList;
import java.util.List;

/** Hexagonal architecture: the core defines ports; adapters plug in on both sides and can be swapped. */
public class HexagonalDemo {

    // ---------- Core (no framework, no I/O) ----------
    interface PlaceOrderUseCase { String place(String customer, long amountPaise); }        // (1)!
    interface PaymentPort { boolean charge(String customer, long amountPaise); }           // (2)!
    interface NotificationPort { void send(String customer, String text); }

    static final class OrderService implements PlaceOrderUseCase {
        private final PaymentPort payments; private final NotificationPort notifications;
        private int seq = 1;
        OrderService(PaymentPort payments, NotificationPort notifications) { this.payments = payments; this.notifications = notifications; }
        public String place(String customer, long amount) {
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
            if (!payments.charge(customer, amount)) return "DECLINED";
            String id = "FC-" + seq++;
            notifications.send(customer, "Order " + id + " confirmed");
            return id;
        }
    }

    // ---------- Driven adapters (outbound) ----------
    static final class PayLinkAdapter implements PaymentPort {                              // (3)!
        public boolean charge(String customer, long amount) { return amount < 500_000; }   // stands in for an HTTP call
    }
    static final class FakePayments implements PaymentPort {
        boolean approve = true; final List<String> calls = new ArrayList<>();
        public boolean charge(String customer, long amount) { calls.add(customer + ":" + amount); return approve; }
    }
    static final class SmsAdapter implements NotificationPort {
        final List<String> sent = new ArrayList<>();
        public void send(String customer, String text) { sent.add("SMS " + customer + ": " + text); }
    }
    static final class PushAdapter implements NotificationPort {
        final List<String> sent = new ArrayList<>();
        public void send(String customer, String text) { sent.add("PUSH " + customer + ": " + text); }
    }

    // ---------- Driving adapters (inbound) ----------
    static String httpPost(PlaceOrderUseCase useCase, String body) {                        // e.g. a REST controller
        String[] parts = body.split(",");
        return "201 " + useCase.place(parts[0], Long.parseLong(parts[1]));
    }
    static String cliCommand(PlaceOrderUseCase useCase, String[] argv) {                   // e.g. a support tool
        return "placed " + useCase.place(argv[1], Long.parseLong(argv[2]));
    }

    public static void main(String[] args) {
        SmsAdapter sms = new SmsAdapter();
        PlaceOrderUseCase production = new OrderService(new PayLinkAdapter(), sms);
        System.out.println(httpPost(production, "asha,51800"));
        System.out.println(cliCommand(production, new String[] {"place", "ravi", "12000"}));
        System.out.println(sms.sent);

        FakePayments fake = new FakePayments();
        fake.approve = false;
        PushAdapter push = new PushAdapter();
        PlaceOrderUseCase underTest = new OrderService(fake, push);
        System.out.println("with fakes: " + underTest.place("meera", 9_900) + ", payment calls " + fake.calls + ", notifications " + push.sent);
    }
}
