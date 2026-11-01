import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MVC: the controller turns input into model changes; the model notifies views; views render the model. */
public class MvcDemo {

    interface ModelListener { void changed(CartModel model); }

    static final class CartModel {                                       // (1)!
        private final Map<String, Integer> qty = new LinkedHashMap<>();
        private final List<ModelListener> listeners = new ArrayList<>();
        void subscribe(ModelListener l) { listeners.add(l); }
        void add(String item) { qty.merge(item, 1, Integer::sum); listeners.forEach(l -> l.changed(this)); }
        void remove(String item) {
            qty.computeIfPresent(item, (k, v) -> v > 1 ? v - 1 : null);
            listeners.forEach(l -> l.changed(this));
        }
        Map<String, Integer> items() { return Map.copyOf(qty); }
        int count() { return qty.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    static final class CartBadgeView implements ModelListener {         // (2)!
        public void changed(CartModel m) { System.out.println("  [badge] " + m.count() + " items"); }
    }
    static final class CartListView implements ModelListener {
        public void changed(CartModel m) { System.out.println("  [list]  " + new java.util.TreeMap<>(m.items())); }
    }

    static final class CartController {                                 // (3)!
        private final CartModel model;
        CartController(CartModel model) { this.model = model; }
        void onInput(String command) {
            String[] parts = command.split(" ", 2);
            switch (parts[0]) {
                case "add" -> model.add(parts[1]);
                case "remove" -> model.remove(parts[1]);
                default -> System.out.println("  unknown command: " + command);
            }
        }
    }

    public static void main(String[] args) {
        CartModel model = new CartModel();
        model.subscribe(new CartBadgeView());
        model.subscribe(new CartListView());
        CartController controller = new CartController(model);
        for (String input : List.of("add milk", "add bread", "add milk", "remove milk", "checkout")) {
            System.out.println("> " + input);
            controller.onInput(input);
        }
    }
}
