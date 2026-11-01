import java.util.ArrayDeque;
import java.util.Deque;

/** A caretaker with bounded undo/redo history of snapshots for a promo banner editor. */
public class UndoHistoryDemo {

    record Banner(String headline, String colour, int discount) { }                               // (1)!

    static final class History {
        private final Deque<Banner> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
        private final int limit;
        History(int limit) { this.limit = limit; }

        void saved(Banner before) {
            undo.push(before);
            if (undo.size() > limit) undo.removeLast();                                              // (2)!
            redo.clear();                                                                           // (3)!
        }
        Banner undo(Banner current) { if (undo.isEmpty()) return current; redo.push(current); return undo.pop(); }
        Banner redo(Banner current) { if (redo.isEmpty()) return current; undo.push(current); return redo.pop(); }   // (4)!
        int undoDepth() { return undo.size(); }
    }

    public static void main(String[] args) {
        History h = new History(3);
        Banner b = new Banner("Mango mania", "yellow", 10);
        for (Banner next : new Banner[] {new Banner("Mango mania", "orange", 10), new Banner("Mango mania", "orange", 15),
                new Banner("Mango madness", "orange", 15), new Banner("Mango madness", "red", 20)}) {
            h.saved(b); b = next;
        }
        System.out.println("current: " + b + ", undo depth " + h.undoDepth() + " (limit 3, oldest dropped)");
        b = h.undo(b); b = h.undo(b);
        System.out.println("undo x2: " + b);
        b = h.redo(b);
        System.out.println("redo:    " + b);
        b = h.undo(h.undo(h.undo(b)));
        System.out.println("undo as far as possible: " + b + " (the original yellow banner was dropped)");
    }
}
