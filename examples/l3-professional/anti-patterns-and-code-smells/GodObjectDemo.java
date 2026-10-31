import java.util.ArrayList;
import java.util.List;

/** A God Object knows and does too much; splitting it by responsibility gives small classes with one reason to change. */
public class GodObjectDemo {

    // ---- Before: one class handles cart, pricing, stock, payment, SMS and reporting. ----
    static final class FreshCartManager {                                   // (1)!
        final List<String> cart = new ArrayList<>(); final List<Long> prices = new ArrayList<>();
        int milkStock = 10; long revenue = 0; final List<String> sms = new ArrayList<>();
        void addToCart(String item, long price) { cart.add(item); prices.add(price); }
        long total() { long t = prices.stream().mapToLong(Long::longValue).sum(); return t >= 49_900 ? t : t + 2_500; }
        boolean checkout(String phone) {
            if (cart.contains("milk") && milkStock < 1) return false;
            if (cart.contains("milk")) milkStock--;
            revenue += total();
            sms.add(phone + ": order placed, Rs " + total() / 100);
            cart.clear(); prices.clear();
            return true;
        }
        String dailyReport() { return "revenue Rs " + revenue / 100 + ", milk left " + milkStock; }
        // ... in real code bases: 4,000 more lines, 60 fields, 25 collaborators
    }

    // ---- After: each class has one job; a small service coordinates. ----
    record Cart(List<String> items, List<Long> prices) {                    // (2)!
        static Cart empty() { return new Cart(new ArrayList<>(), new ArrayList<>()); }
        void add(String item, long price) { items.add(item); prices.add(price); }
    }
    static final class Pricing { long total(Cart c) { long t = c.prices().stream().mapToLong(Long::longValue).sum(); return t >= 49_900 ? t : t + 2_500; } }
    static final class Stock { int milk = 10; boolean take(List<String> items) { if (!items.contains("milk")) return true; if (milk < 1) return false; milk--; return true; } }
    static final class Notifier { final List<String> sent = new ArrayList<>(); void placed(String phone, long total) { sent.add(phone + ": order placed, Rs " + total / 100); } }
    static final class SalesLedger { long revenue; void record(long amount) { revenue += amount; } }

    record CheckoutService(Pricing pricing, Stock stock, Notifier notifier, SalesLedger ledger) {   // (3)!
        boolean checkout(Cart cart, String phone) {
            if (!stock.take(cart.items())) return false;
            long total = pricing.total(cart);
            ledger.record(total);
            notifier.placed(phone, total);
            return true;
        }
    }

    public static void main(String[] args) {
        FreshCartManager god = new FreshCartManager();
        god.addToCart("milk", 6_800); god.addToCart("bread", 4_500);
        god.checkout("98xxxx0001");
        System.out.println("before: " + god.sms + " | " + god.dailyReport());

        var service = new CheckoutService(new Pricing(), new Stock(), new Notifier(), new SalesLedger());
        Cart cart = Cart.empty();
        cart.add("milk", 6_800); cart.add("bread", 4_500);
        service.checkout(cart, "98xxxx0001");
        System.out.println("after:  " + service.notifier().sent + " | revenue Rs " + service.ledger().revenue / 100 + ", milk left " + service.stock().milk);
    }
}
