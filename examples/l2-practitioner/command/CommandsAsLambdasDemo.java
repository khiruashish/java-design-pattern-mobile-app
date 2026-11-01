import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** In modern Java a simple command is just a lambda (Runnable, Consumer, Supplier). Add a record when you need a name, undo or data. */
public class CommandsAsLambdasDemo {

    static final Map<String, Integer> stock = new LinkedHashMap<>(Map.of("MILK-1L", 40));

    public static void main(String[] args) {
        Map<String, Runnable> adminCommands = new LinkedHashMap<>();                          // (1)!
        adminCommands.put("restock-milk", () -> stock.merge("MILK-1L", 100, Integer::sum));
        adminCommands.put("clear-milk", () -> stock.put("MILK-1L", 0));
        adminCommands.put("show", () -> System.out.println("  stock: " + stock));

        for (String typed : new String[] {"show", "restock-milk", "show", "reboot"}) {
            Runnable cmd = adminCommands.get(typed);
            if (cmd == null) { System.out.println("  unknown command '" + typed + "', known: " + adminCommands.keySet()); continue; }
            cmd.run();
        }

        Consumer<String> notify = msg -> System.out.println("  notify ops: " + msg);          // (2)!
        notify.andThen(msg -> System.out.println("  audit: " + msg)).accept("milk restocked");

        record NamedCommand(String name, Runnable action, Runnable undo) { }                  // (3)!
        int[] previous = new int[1];                                                          // remembered for undo
        NamedCommand clear = new NamedCommand("clear-milk", () -> previous[0] = stock.put("MILK-1L", 0), () -> stock.put("MILK-1L", previous[0]));
        clear.action().run(); System.out.println("  after " + clear.name() + ": " + stock);
        clear.undo().run();   System.out.println("  after undo: " + stock);
    }
}
