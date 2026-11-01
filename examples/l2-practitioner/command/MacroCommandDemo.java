import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A macro command runs several commands as one; undo reverses them in reverse order; a failure rolls back what already ran. */
public class MacroCommandDemo {

    static final class Cart { final Map<String, Integer> lines = new LinkedHashMap<>(); public String toString() { return lines.toString(); } }
    interface UndoableCommand { void execute(); void undo(); }

    record AddItem(Cart cart, String sku, int qty, int stock) implements UndoableCommand {
        public void execute() {
            if (qty > stock) throw new IllegalStateException(sku + " has only " + stock + " left");
            cart.lines.merge(sku, qty, Integer::sum);
        }
        public void undo() { cart.lines.computeIfPresent(sku, (k, v) -> v - qty == 0 ? null : v - qty); }
    }

    record Macro(String name, List<UndoableCommand> steps) implements UndoableCommand {      // (1)!
        public void execute() {
            List<UndoableCommand> done = new ArrayList<>();
            try {
                for (UndoableCommand s : steps) { s.execute(); done.add(s); }
            } catch (RuntimeException e) {
                for (int i = done.size() - 1; i >= 0; i--) done.get(i).undo();                  // (2)!
                throw new IllegalStateException(name + " failed and was rolled back: " + e.getMessage(), e);
            }
        }
        public void undo() { for (int i = steps.size() - 1; i >= 0; i--) steps.get(i).undo(); }   // (3)!
    }

    public static void main(String[] args) {
        Cart cart = new Cart();
        Macro reorder = new Macro("Reorder last week's basket", List.of(
                new AddItem(cart, "MILK-1L", 2, 50), new AddItem(cart, "BREAD", 1, 20), new AddItem(cart, "EGGS-6", 1, 10)));
        reorder.execute();
        System.out.println("reorder: " + cart);
        reorder.undo();
        System.out.println("undo whole macro: " + cart);

        Macro party = new Macro("Party pack", List.of(new AddItem(cart, "CHIPS", 4, 40), new AddItem(cart, "COLA-2L", 6, 3)));
        try { party.execute(); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        System.out.println("cart after failed macro: " + cart);
    }
}
