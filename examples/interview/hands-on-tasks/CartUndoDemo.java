import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

/** Task 2 solution: cart edits as commands with undo and redo; each command remembers what it needs to reverse itself. */
public class CartUndoDemo {

    static final class Cart {
        final Map<String, Integer> lines = new TreeMap<>();
        int qty(String sku) { return lines.getOrDefault(sku, 0); }
        void set(String sku, int qty) { if (qty == 0) lines.remove(sku); else lines.put(sku, qty); }
        @Override public String toString() { return lines.toString(); }
    }

    interface Command { void execute(Cart cart); void undo(Cart cart); String label(); }

    record SetQuantity(String sku, int qty, int[] previous) implements Command {
        SetQuantity(String sku, int qty) { this(sku, qty, new int[1]); }
        public void execute(Cart cart) { previous[0] = cart.qty(sku); cart.set(sku, qty); }
        public void undo(Cart cart) { cart.set(sku, previous[0]); }
        public String label() { return "set " + sku + " to " + qty; }
    }

    record ClearCart(Map<String, Integer> snapshot) implements Command {
        ClearCart() { this(new TreeMap<>()); }
        public void execute(Cart cart) { snapshot.clear(); snapshot.putAll(cart.lines); cart.lines.clear(); }
        public void undo(Cart cart) { cart.lines.putAll(snapshot); }
        public String label() { return "clear cart"; }
    }

    static final class Editor {
        final Cart cart = new Cart();
        final Deque<Command> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
        void run(Command c) { c.execute(cart); undo.push(c); redo.clear(); System.out.println(c.label() + " -> " + cart); }
        void undo() { if (undo.isEmpty()) return; Command c = undo.pop(); c.undo(cart); redo.push(c); System.out.println("undo " + c.label() + " -> " + cart); }
        void redo() { if (redo.isEmpty()) { System.out.println("nothing to redo (a new edit cleared the redo stack)"); return; } Command c = redo.pop(); c.execute(cart); undo.push(c); System.out.println("redo " + c.label() + " -> " + cart); }
    }

    public static void main(String[] args) {
        Editor e = new Editor();
        e.run(new SetQuantity("MILK-1L", 2));
        e.run(new SetQuantity("CURD-500G", 1));
        e.run(new SetQuantity("MILK-1L", 5));
        e.run(new ClearCart());
        e.undo();
        e.undo();
        e.redo();
        e.run(new SetQuantity("BREAD", 1));
        e.redo();
    }
}
