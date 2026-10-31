import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records real calls between components and prints them as a Mermaid sequence diagram,
 * so the diagram in the docs can be checked against what the code actually does.
 */
public class SequenceTraceDemo {

    interface Inventory { int reserve(String sku, int quantity); }
    interface Payments { String charge(String orderId, long cents); }
    interface Checkout { String placeOrder(String orderId, String sku, int quantity); }

    /** Collects diagram lines. One recorder per traced request. */
    static final class SequenceRecorder {
        private final List<String> lines = new ArrayList<>(List.of("sequenceDiagram"));
        private final ArrayList<String> callers = new ArrayList<>(List.of("App"));

        @SuppressWarnings("unchecked")
        <T> T trace(Class<T> type, String participant, T target) {                     // (1)!
            InvocationHandler handler = (proxy, method, args) -> {
                String caller = callers.get(callers.size() - 1);
                lines.add("    " + caller + "->>" + participant + ": " + method.getName() + "(" + join(args) + ")");
                callers.add(participant);
                try {
                    Object result = method.invoke(target, args);
                    lines.add("    " + participant + "-->>" + caller + ": " + result);
                    return result;
                } catch (InvocationTargetException e) {                                 // (2)!
                    lines.add("    " + participant + "--x" + caller + ": " + e.getCause().getMessage());
                    throw e.getCause();
                } finally {
                    callers.remove(callers.size() - 1);
                }
            };
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        }

        String diagram() { return String.join("\n", lines); }

        private static String join(Object[] args) {
            if (args == null) return "";
            List<String> parts = new ArrayList<>();
            for (Object a : args) parts.add(String.valueOf(a));
            return String.join(", ", parts);
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> stock = new java.util.HashMap<>(Map.of("MILK-1L", 5));
        SequenceRecorder recorder = new SequenceRecorder();

        Inventory inventory = recorder.trace(Inventory.class, "Inventory", (sku, qty) -> {
            int available = stock.getOrDefault(sku, 0);
            if (available < qty) throw new IllegalStateException("only " + available + " left");
            stock.put(sku, available - qty);
            return available - qty;
        });
        Payments payments = recorder.trace(Payments.class, "Payments", (orderId, cents) -> "PAID-" + orderId);
        Checkout checkout = recorder.trace(Checkout.class, "Checkout", (orderId, sku, qty) -> {
            inventory.reserve(sku, qty);
            return payments.charge(orderId, qty * 129L);                                // (3)!
        });

        checkout.placeOrder("FC-4001", "MILK-1L", 2);
        try {
            checkout.placeOrder("FC-4002", "MILK-1L", 9);
        } catch (IllegalStateException e) {
            // already recorded in the diagram as a failed call
        }
        System.out.println(recorder.diagram());
    }
}
