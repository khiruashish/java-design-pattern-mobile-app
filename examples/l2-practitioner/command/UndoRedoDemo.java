import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Undo/redo: each command knows how to reverse itself; two stacks remember history. */
public class UndoRedoDemo {

    static final class Cart { final Map<String, Integer> lines = new LinkedHashMap<>(); public String toString() { return lines.toString(); } }

    interface UndoableCommand { void execute(); void undo(); }                                // (1)!

    record AddItem(Cart cart, String sku, int qty) implements UndoableCommand {
        public void execute() { cart.lines.merge(sku, qty, Integer::sum); }
        public void undo() { cart.lines.computeIfPresent(sku, (k, v) -> v - qty == 0 ? null : v - qty); }
    }

    static final class RemoveItem implements UndoableCommand {
        private final Cart cart; private final String sku; private Integer removedQty;         // (2)!
        RemoveItem(Cart cart, String sku) { this.cart = cart; this.sku = sku; }
        public void execute() { removedQty = cart.lines.remove(sku); }
        public void undo() { if (removedQty != null) cart.lines.put(sku, removedQty); }
    }

    static final class History {
        private final Deque<UndoableCommand> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
        void run(UndoableCommand c) { c.execute(); undo.push(c); redo.clear(); }               // (3)!
        void undo() { if (!undo.isEmpty()) { UndoableCommand c = undo.pop(); c.undo(); redo.push(c); } }
        void redo() { if (!redo.isEmpty()) { UndoableCommand c = redo.pop(); c.execute(); undo.push(c); } }   // (4)!
    }

    public static void main(String[] args) {
        Cart cart = new Cart(); History h = new History();
        h.run(new AddItem(cart, "MILK-1L", 2));
        h.run(new AddItem(cart, "BREAD", 1));
        h.run(new RemoveItem(cart, "MILK-1L"));
        System.out.println("after 3 edits: " + cart);
        h.undo(); System.out.println("undo:          " + cart);
        h.undo(); System.out.println("undo:          " + cart);
        h.redo(); System.out.println("redo:          " + cart);
        h.run(new AddItem(cart, "EGGS-6", 1));
        h.redo(); System.out.println("new edit, redo ignored: " + cart);
    }
}
