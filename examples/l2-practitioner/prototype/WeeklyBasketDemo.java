import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal Prototype: "Weekly essentials" is a template basket; each customer gets an independent copy to edit. */
public class WeeklyBasketDemo {

    static final class Basket {
        final String name; final Map<String, Integer> items;
        Basket(String name, Map<String, Integer> items) { this.name = name; this.items = new LinkedHashMap<>(items); }
        Basket copyFor(String customer) { return new Basket(name + " for " + customer, items); }   // (1)!
        public String toString() { return name + " " + items; }
    }

    public static void main(String[] args) {
        Basket essentials = new Basket("Weekly essentials", Map.of("MILK-1L", 7, "BREAD", 2, "EGGS-12", 1));
        Basket asha = essentials.copyFor("asha");
        asha.items.put("MILK-1L", 4);                                                      // (2)!
        asha.items.remove("BREAD");
        Basket ravi = essentials.copyFor("ravi");
        ravi.items.merge("EGGS-12", 1, Integer::sum);
        System.out.println(new java.util.TreeMap<>(essentials.items) + " <- template");
        System.out.println(new java.util.TreeMap<>(asha.items) + " <- asha");
        System.out.println(new java.util.TreeMap<>(ravi.items) + " <- ravi");
    }
}
