import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A weighted decision matrix: score options against criteria the team agreed on, then check sensitivity: if a
 * small change in weights flips the winner, the decision is close and the trade-off needs discussion, not maths.
 */
public class DecisionMatrixDemo {

    record Option(String name, Map<String, Integer> scores) {}                                     // scores 1..5

    static String winner(List<Option> options, Map<String, Integer> weights, Map<String, Integer> totalsOut) {
        totalsOut.clear();
        for (Option o : options) {
            int total = 0;
            for (var w : weights.entrySet()) total += w.getValue() * o.scores().get(w.getKey());       // (1)!
            totalsOut.put(o.name(), total);
        }
        return totalsOut.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }

    public static void main(String[] args) {
        List<Option> options = List.of(
                new Option("enum + switch", Map.of("simplicity", 5, "add-a-rule", 2, "testability", 3, "runtime-config", 1)),
                new Option("Strategy beans", Map.of("simplicity", 3, "add-a-rule", 4, "testability", 5, "runtime-config", 2)),
                new Option("rules engine", Map.of("simplicity", 1, "add-a-rule", 5, "testability", 3, "runtime-config", 5)));
        Map<String, Integer> weights = new LinkedHashMap<>();                                       // (2)!
        weights.put("simplicity", 3); weights.put("add-a-rule", 3); weights.put("testability", 2); weights.put("runtime-config", 1);

        Map<String, Integer> totals = new LinkedHashMap<>();
        System.out.println("baseline winner: " + winner(options, weights, totals) + " " + totals);
        for (String criterion : weights.keySet()) {                                                 // (3)!
            Map<String, Integer> nudged = new LinkedHashMap<>(weights);
            nudged.merge(criterion, 2, Integer::sum);
            String w = winner(options, nudged, totals);
            System.out.println("  if " + criterion + " weighs +2: " + w + (w.equals("Strategy beans") ? "" : "  <- flips"));
        }
    }
}
