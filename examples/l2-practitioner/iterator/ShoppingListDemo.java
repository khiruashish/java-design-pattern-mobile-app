import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Map;

/** Minimal Iterator: a picker's shopping list iterates in aisle order, whatever order items were added in. */
public class ShoppingListDemo {

    record Item(String name, int aisle) { }

    static final class PickList implements Iterable<Item> {                                  // (1)!
        private final Map<Integer, List<Item>> byAisle = new TreeMap<>();
        void add(Item i) { byAisle.computeIfAbsent(i.aisle(), a -> new ArrayList<>()).add(i); }
        public Iterator<Item> iterator() {
            return byAisle.values().stream().flatMap(List::stream).iterator();                   // (2)!
        }
    }

    public static void main(String[] args) {
        PickList list = new PickList();
        list.add(new Item("Milk", 7)); list.add(new Item("Bread", 2)); list.add(new Item("Curd", 7)); list.add(new Item("Rice", 4));
        for (Item i : list) System.out.println("aisle " + i.aisle() + ": " + i.name());
    }
}
