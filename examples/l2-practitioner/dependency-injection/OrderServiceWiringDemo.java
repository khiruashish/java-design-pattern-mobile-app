import java.util.ArrayList;
import java.util.List;

/** Minimal DI: OrderService receives its dependencies; production and tests just pass different ones. */
public class OrderServiceWiringDemo {

    interface StockChecker { boolean inStock(String sku); }
    interface SmsSender { void send(String phone, String text); }

    static final class OrderService {
        private final StockChecker stock; private final SmsSender sms;
        OrderService(StockChecker stock, SmsSender sms) { this.stock = stock; this.sms = sms; }   // (1)!
        String place(String phone, String sku) {
            if (!stock.inStock(sku)) return "sorry, " + sku + " is out of stock";
            sms.send(phone, "Order for " + sku + " confirmed");
            return "placed " + sku;
        }
    }

    public static void main(String[] args) {
        // "Production" wiring (simplified)
        OrderService prod = new OrderService(sku -> !sku.equals("MANGO"), (p, t) -> System.out.println("  SMS to " + p + ": " + t));
        System.out.println(prod.place("+919800000001", "MILK"));

        // "Test" wiring: fakes that record what happened
        List<String> sent = new ArrayList<>();
        OrderService test = new OrderService(sku -> false, (p, t) -> sent.add(t));          // (2)!
        System.out.println(test.place("+919800000002", "MILK") + ", SMS sent in test: " + sent.size());
    }
}
