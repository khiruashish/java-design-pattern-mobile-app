import java.util.ArrayList;
import java.util.List;

/** Minimal Observer: customers watch a product and get told when its price drops below their target. */
public class PriceDropAlertDemo {

    interface PriceWatcher { void priceChanged(String sku, long newPriceCents); }

    record Customer(String name, long targetCents) implements PriceWatcher {               // (1)!
        public void priceChanged(String sku, long p) {
            if (p <= targetCents) System.out.println("  alert " + name + ": " + sku + " is now " + p + " (target " + targetCents + ")");
        }
    }

    static final class Product {
        private final String sku; private long priceCents;
        private final List<PriceWatcher> watchers = new ArrayList<>();
        Product(String sku, long price) { this.sku = sku; this.priceCents = price; }
        void watch(PriceWatcher w) { watchers.add(w); }
        void setPrice(long p) {
            if (p == priceCents) return;                                                 // (2)!
            priceCents = p;
            watchers.forEach(w -> w.priceChanged(sku, p));
        }
    }

    public static void main(String[] args) {
        Product oil = new Product("OIL-SUN-1L", 18_900);
        oil.watch(new Customer("asha", 17_000));
        oil.watch(new Customer("vikram", 16_000));
        System.out.println("price -> 17500"); oil.setPrice(17_500);
        System.out.println("price -> 16900"); oil.setPrice(16_900);
        System.out.println("price -> 15900"); oil.setPrice(15_900);
    }
}
