import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Realistic: a FreshCart "test kit" that bundles builders, fakes, spies and a fixed clock, so each checkout test
 * is a few readable lines. The tests are run by a tiny runner here; in the repo they'd be JUnit tests.
 */
public class CheckoutTestKitDemo {

    // ----- production code -----
    record Item(String sku, int qty, long unitPaise) { }
    record Cart(String customerId, String tier, List<Item> items) {
        long subtotal() { return items.stream().mapToLong(i -> i.qty() * i.unitPaise()).sum(); }
    }
    interface Inventory { boolean reserve(String sku, int qty); void release(String sku, int qty); }
    interface Payments { String charge(String customerId, long paise); }
    interface Events { void publish(String event); }

    record Checkout(Inventory inventory, Payments payments, Events events, Clock clock) {
        String placeOrder(Cart cart) {
            List<Item> reserved = new ArrayList<>();
            for (Item i : cart.items()) {
                if (!inventory.reserve(i.sku(), i.qty())) {
                    reserved.forEach(r -> inventory.release(r.sku(), r.qty()));                    // don't leak earlier reservations
                    return "OUT_OF_STOCK:" + i.sku();
                }
                reserved.add(i);
            }
            long delivery = cart.tier().equals("gold") || cart.subtotal() >= 49_900 ? 0 : 2_500;
            String ref = payments.charge(cart.customerId(), cart.subtotal() + delivery);
            events.publish("OrderPlaced " + ref + " at " + clock.instant());
            return "PLACED total=" + (cart.subtotal() + delivery);
        }
    }

    // ----- the test kit -----
    static final class Kit {                                                                         // (1)!
        final Map<String, Integer> stock = new LinkedHashMap<>();
        final List<String> charges = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final Clock clock = Clock.fixed(Instant.parse("2026-10-31T09:00:00Z"), ZoneOffset.UTC);

        Kit inStock(String sku, int qty) { stock.put(sku, qty); return this; }
        Checkout checkout() {
            Inventory fakeInventory = new Inventory() {                                                  // fake: real logic, in memory
                public boolean reserve(String sku, int qty) {
                    int have = stock.getOrDefault(sku, 0);
                    if (have < qty) return false;
                    stock.put(sku, have - qty); return true;
                }
                public void release(String sku, int qty) { stock.merge(sku, qty, Integer::sum); }
            };
            Payments spyPayments = (c, p) -> { charges.add(c + ":" + p); return "PAY-" + charges.size(); };   // spy
            return new Checkout(fakeInventory, spyPayments, events::add, clock);
        }
        static CartBuilder aCart() { return new CartBuilder(); }
    }
    static final class CartBuilder {
        private String customer = "u1", tier = "silver"; private final List<Item> items = new ArrayList<>();
        CartBuilder gold() { tier = "gold"; return this; }
        CartBuilder with(String sku, int qty, long unitPaise) { items.add(new Item(sku, qty, unitPaise)); return this; }
        Cart build() { return new Cart(customer, tier, items.isEmpty() ? List.of(new Item("MILK", 1, 6_400)) : List.copyOf(items)); }
    }

    // ----- tests -----
    static void smallSilverCartPaysDelivery() {
        Kit kit = new Kit().inStock("MILK", 10);                                                     // arrange
        String result = kit.checkout().placeOrder(Kit.aCart().build());                              // act
        expect(result.equals("PLACED total=8900"), result);                                          // assert
        expect(kit.charges.equals(List.of("u1:8900")), kit.charges.toString());
    }
    static void goldCustomersGetFreeDelivery() {
        Kit kit = new Kit().inStock("MILK", 10);
        String result = kit.checkout().placeOrder(Kit.aCart().gold().build());
        expect(result.equals("PLACED total=6400"), result);
    }
    static void outOfStockChargesNothing() {                                                         // (2)!
        Kit kit = new Kit().inStock("MILK", 10).inStock("PANEER", 0);
        String result = kit.checkout().placeOrder(Kit.aCart().with("MILK", 1, 6_400).with("PANEER", 1, 9_000).build());
        expect(result.equals("OUT_OF_STOCK:PANEER"), result);
        expect(kit.charges.isEmpty() && kit.events.isEmpty(), "charged " + kit.charges);
        expect(kit.stock.get("MILK") == 10, "MILK stock " + kit.stock.get("MILK") + ", reservation leaked");
    }
    static void eventCarriesTheClockTime() {
        Kit kit = new Kit().inStock("MILK", 1);
        kit.checkout().placeOrder(Kit.aCart().build());
        expect(kit.events.equals(List.of("OrderPlaced PAY-1 at 2026-10-31T09:00:00Z")), kit.events.toString());   // (3)!
    }

    static void expect(boolean ok, String actual) { if (!ok) throw new AssertionError("got " + actual); }

    public static void main(String[] args) {
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("small silver cart pays delivery", CheckoutTestKitDemo::smallSilverCartPaysDelivery);
        tests.put("gold customers get free delivery", CheckoutTestKitDemo::goldCustomersGetFreeDelivery);
        tests.put("out of stock charges nothing", CheckoutTestKitDemo::outOfStockChargesNothing);
        tests.put("event carries the clock time", CheckoutTestKitDemo::eventCarriesTheClockTime);
        tests.forEach((name, t) -> {
            try { t.run(); System.out.println("PASS " + name); } catch (AssertionError e) { System.out.println("FAIL " + name + ": " + e.getMessage()); }
        });
    }
}
