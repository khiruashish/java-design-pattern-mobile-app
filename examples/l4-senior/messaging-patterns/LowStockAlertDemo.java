import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Minimal: the warehouse publishes LowStock once; buyers and the app react without the warehouse knowing them. */
public class LowStockAlertDemo {

    record LowStock(String sku, int unitsLeft) {}

    static final List<Consumer<LowStock>> subscribers = new ArrayList<>();

    public static void main(String[] args) {
        subscribers.add(e -> System.out.println("buying team: raise PO to DairyDay for " + e.sku()));   // (1)!
        subscribers.add(e -> System.out.println("app: show 'only " + e.unitsLeft() + " left' on " + e.sku()));
        LowStock event = new LowStock("PANEER-200G", 3);
        subscribers.forEach(s -> s.accept(event));                                                    // (2)!
    }
}
