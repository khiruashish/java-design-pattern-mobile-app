import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The refactoring loop: pin current behaviour with a golden master, then change structure in small steps, re-checking each one. */
public class RefactoringWorkflowDemo {

    record Order(int items, long subtotal, String city, boolean express) {}

    // Step 0: legacy code, untouched.
    static String v0(Order o) {
        String s = "";
        if (o.city().equals("BLR") || o.city().equals("PUN")) { if (o.express()) s = "30 min"; else s = "2 h"; }
        else { if (o.express()) { if (o.items() > 20) s = "not available"; else s = "45 min"; } else s = "next day"; }
        if (o.subtotal() > 100_000 && !s.equals("not available")) s = s + " (priority)";
        return s;
    }

    // Step 1: extract method for the city check.
    static boolean metro(Order o) { return o.city().equals("BLR") || o.city().equals("PUN"); }
    static String v1(Order o) {
        String s = "";
        if (metro(o)) { if (o.express()) s = "30 min"; else s = "2 h"; }
        else { if (o.express()) { if (o.items() > 20) s = "not available"; else s = "45 min"; } else s = "next day"; }
        if (o.subtotal() > 100_000 && !s.equals("not available")) s = s + " (priority)";
        return s;
    }

    // Step 2: extract the base promise into its own method with guard clauses.
    static String basePromise(Order o) {
        if (metro(o)) return o.express() ? "30 min" : "2 h";
        if (!o.express()) return "next day";
        return o.items() > 20 ? "not available" : "45 min";
    }
    static String v2(Order o) {
        String s = basePromise(o);
        if (o.subtotal() > 100_000 && !s.equals("not available")) s = s + " (priority)";
        return s;
    }

    // Step 3: make "not available" explicit instead of comparing strings.
    static String v3(Order o) {
        if (!metro(o) && o.express() && o.items() > 20) return "not available";
        String base = basePromise(o);
        return o.subtotal() > 100_000 ? base + " (priority)" : base;
    }

    static List<Order> sampleInputs() {                                         // (1)!
        List<Order> inputs = new ArrayList<>();
        for (int items : new int[] {1, 20, 21})
            for (long subtotal : new long[] {5_000, 100_000, 100_001})
                for (String city : new String[] {"BLR", "PUN", "MYS"})
                    for (boolean express : new boolean[] {true, false})
                        inputs.add(new Order(items, subtotal, city, express));
        return inputs;
    }

    public static void main(String[] args) {
        List<Order> inputs = sampleInputs();
        List<String> golden = inputs.stream().map(RefactoringWorkflowDemo::v0).toList();     // (2)!
        List<Function<Order, String>> steps = List.of(RefactoringWorkflowDemo::v1, RefactoringWorkflowDemo::v2, RefactoringWorkflowDemo::v3);
        for (int i = 0; i < steps.size(); i++) {
            List<String> now = inputs.stream().map(steps.get(i)).toList();
            System.out.println("step " + (i + 1) + ": " + (now.equals(golden) ? "green" : "RED, revert") + " on " + inputs.size() + " inputs");   // (3)!
        }
    }
}
