import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** What happens when a collection changes while you iterate it depends on the collection's iterator contract. */
public class FailFastVsWeaklyConsistentDemo {

    public static void main(String[] args) {
        List<String> cart = new ArrayList<>(List.of("MILK", "EXPIRED-CURD", "BREAD", "EGGS"));
        try {
            for (String sku : cart) if (sku.startsWith("EXPIRED")) cart.remove(sku);            // (1)!
        } catch (ConcurrentModificationException e) { System.out.println("fail-fast: ConcurrentModificationException"); }

        cart = new ArrayList<>(List.of("MILK", "EXPIRED-CURD", "BREAD"));
        for (Iterator<String> it = cart.iterator(); it.hasNext(); ) if (it.next().startsWith("EXPIRED")) it.remove();   // (2)!
        System.out.println("iterator.remove(): " + cart);
        cart.add("EXPIRED-EGGS");
        cart.removeIf(s -> s.startsWith("EXPIRED"));
        System.out.println("removeIf:          " + cart);

        Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of("MILK", 40, "BREAD", 12));
        for (String sku : stock.keySet()) stock.put(sku + "-PROMO", 1);                          // (3)!
        System.out.println("weakly consistent: no exception, original keys kept: " + stock.keySet().containsAll(List.of("MILK", "BREAD")) + " (new keys may or may not be seen by the loop)");

        List<String> listeners = new CopyOnWriteArrayList<>(List.of("sms", "push"));
        for (String l : listeners) listeners.add(l + "-backup");                                 // (4)!
        System.out.println("snapshot (CopyOnWrite): loop saw 2 items, list now " + listeners);
    }
}
