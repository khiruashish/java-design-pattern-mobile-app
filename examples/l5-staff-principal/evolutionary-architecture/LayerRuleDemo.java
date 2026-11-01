import java.util.List;
import java.util.Map;

/** Minimal: one fitness function. Controllers may call services, never repositories directly. */
public class LayerRuleDemo {

    public static void main(String[] args) {
        Map<String, List<String>> calls = Map.of(
                "OrderController", List.of("OrderService"),
                "SlotController", List.of("SlotService", "SlotRepository"));               // a shortcut someone took
        calls.keySet().stream().sorted().forEach(caller -> {
            List<String> bad = calls.get(caller).stream().filter(c -> c.endsWith("Repository")).toList();   // (1)!
            System.out.println(caller + (bad.isEmpty() ? ": ok" : ": FAIL calls " + bad + " directly"));       // (2)!
        });
    }
}
