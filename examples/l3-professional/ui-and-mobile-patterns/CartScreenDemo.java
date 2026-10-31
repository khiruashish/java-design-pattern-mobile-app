import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** FreshCart cart screen in MVVM: repository for data, ViewModel for screen state and actions, a dumb view. */
public class CartScreenDemo {

    record CartLine(String sku, String name, long pricePaise, int qty) {}

    /** Local cart storage plus a catalogue lookup. */
    static final class CartRepository {
        private final Map<String, CartLine> lines = new TreeMap<>();
        private final Map<String, Integer> stock;
        CartRepository(Map<String, Integer> stock) { this.stock = stock; }
        List<CartLine> lines() { return List.copyOf(lines.values()); }
        boolean setQty(String sku, String name, long price, int qty) {
            if (qty > stock.getOrDefault(sku, 0)) return false;
            if (qty == 0) lines.remove(sku); else lines.put(sku, new CartLine(sku, name, price, qty));
            return true;
        }
    }

    record CartUiState(List<String> rows, String subtotal, String deliveryNote, boolean checkoutEnabled, String message) {}   // (1)!

    static final class CartViewModel {
        private final CartRepository repo;
        private final List<Consumer<CartUiState>> observers = new ArrayList<>();
        private String message = "";
        CartViewModel(CartRepository repo) { this.repo = repo; }

        void observe(Consumer<CartUiState> o) { observers.add(o); o.accept(state()); }

        void add(String sku, String name, long price) { change(sku, name, price, +1); }
        void remove(String sku, String name, long price) { change(sku, name, price, -1); }

        private void change(String sku, String name, long price, int delta) {      // (2)!
            int current = repo.lines().stream().filter(l -> l.sku().equals(sku)).mapToInt(CartLine::qty).findFirst().orElse(0);
            boolean ok = repo.setQty(sku, name, price, Math.max(0, current + delta));
            message = ok ? "" : "Only " + current + " " + name + " available";
            observers.forEach(o -> o.accept(state()));
        }

        private CartUiState state() {                                           // (3)!
            List<CartLine> lines = repo.lines();
            long subtotal = lines.stream().mapToLong(l -> l.pricePaise() * l.qty()).sum();
            long toFree = 49_900 - subtotal;
            String note = lines.isEmpty() ? "" : toFree <= 0 ? "Free delivery" : "Add Rs " + (toFree + 99) / 100 + " for free delivery";
            List<String> rows = lines.stream().map(l -> l.qty() + " x " + l.name()).toList();
            return new CartUiState(rows, "Rs " + subtotal / 100, note, !lines.isEmpty(), message);
        }
    }

    public static void main(String[] args) {
        CartViewModel vm = new CartViewModel(new CartRepository(Map.of("MANGO-1KG", 1, "MILK-1L", 20)));
        vm.observe(s -> System.out.println("  " + s.rows() + " " + s.subtotal() + " | " + s.deliveryNote()
                + " | checkout=" + s.checkoutEnabled() + (s.message().isEmpty() ? "" : " | " + s.message())));
        System.out.println("> add milk x2");
        vm.add("MILK-1L", "Milk 1L", 6_800);
        vm.add("MILK-1L", "Milk 1L", 6_800);
        System.out.println("> add mango x2");
        vm.add("MANGO-1KG", "Alphonso 1kg", 45_000);
        vm.add("MANGO-1KG", "Alphonso 1kg", 45_000);
        System.out.println("> remove mango");
        vm.remove("MANGO-1KG", "Alphonso 1kg", 45_000);
    }
}
