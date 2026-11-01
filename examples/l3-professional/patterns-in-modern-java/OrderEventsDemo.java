import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Realistic: FreshCart's order event stream. Events are sealed records; projections are switches with nested
 * record patterns and guards; side-effect handlers are lambdas registered per event kind.
 */
public class OrderEventsDemo {

    record Money(long paise) { }
    record Item(String sku, int qty) { }

    sealed interface OrderEvent permits Placed, ItemSubstituted, Delivered, Refunded { String orderId(); }
    record Placed(String orderId, List<Item> items, Money total) implements OrderEvent {
        Placed { items = List.copyOf(items); }
    }
    record ItemSubstituted(String orderId, Item from, Item to) implements OrderEvent { }
    record Delivered(String orderId, int minutesLate) implements OrderEvent { }
    record Refunded(String orderId, Money amount, String reason) implements OrderEvent { }

    static String timelineLine(OrderEvent e) {                                                        // (1)!
        return switch (e) {
            case Placed(var id, var items, Money(var p)) -> id + " placed: " + items.size() + " items, Rs " + p / 100;
            case ItemSubstituted(var id, Item(var a, var q1), Item(var b, var q2)) -> id + " swapped " + q1 + "x" + a + " for " + q2 + "x" + b;
            case Delivered(var id, var late) when late > 15 -> id + " delivered LATE by " + late + " min";        // (2)!
            case Delivered(var id, var late) -> id + " delivered on time";
            case Refunded(var id, Money(var p), var why) -> id + " refunded Rs " + p / 100 + " (" + why + ")";
        };
    }

    enum Kind { PLACED, SUBSTITUTED, DELIVERED, REFUNDED }
    static Kind kind(OrderEvent e) {
        return switch (e) {
            case Placed p -> Kind.PLACED;
            case ItemSubstituted s -> Kind.SUBSTITUTED;
            case Delivered d -> Kind.DELIVERED;
            case Refunded r -> Kind.REFUNDED;
        };
    }

    public static void main(String[] args) {
        List<String> sideEffects = new ArrayList<>();
        Map<Kind, List<Consumer<OrderEvent>>> handlers = new EnumMap<>(Kind.class);                 // (3)!
        handlers.computeIfAbsent(Kind.SUBSTITUTED, k -> new ArrayList<>()).add(e -> sideEffects.add("notify customer of swap on " + e.orderId()));
        handlers.computeIfAbsent(Kind.DELIVERED, k -> new ArrayList<>()).add(e -> { if (e instanceof Delivered d && d.minutesLate() > 15) sideEffects.add("issue Rs 50 credit on " + d.orderId()); });

        List<OrderEvent> stream = List.of(
                new Placed("FC-7", List.of(new Item("MILK", 2), new Item("PANEER", 1)), new Money(21_800)),
                new ItemSubstituted("FC-7", new Item("PANEER", 1), new Item("TOFU", 1)),
                new Delivered("FC-7", 22),
                new Refunded("FC-7", new Money(5_000), "late delivery"));
        for (OrderEvent e : stream) {
            System.out.println(timelineLine(e));
            handlers.getOrDefault(kind(e), List.of()).forEach(h -> h.accept(e));
        }
        System.out.println("side effects: " + sideEffects);
    }
}
