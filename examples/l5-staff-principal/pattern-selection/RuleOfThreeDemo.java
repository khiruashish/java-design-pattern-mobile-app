/** Minimal: don't introduce a pattern for one case; tolerate two; extract when the third variant arrives. */
public class RuleOfThreeDemo {

    static String advice(int variants) {
        if (variants <= 1) return "write it inline; no abstraction yet";                            // (1)!
        if (variants == 2) return "an if/else is fine; note the duplication";
        return "extract an abstraction (e.g. Strategy) now that the variation is real";               // (2)!
    }

    public static void main(String[] args) {
        String[] history = {"Mon: flat Rs 25 delivery fee", "Mar: free above Rs 199", "Jun: Rs 10 off for DairyDay subscribers"};
        for (int i = 0; i < history.length; i++) System.out.println(history[i] + " -> " + advice(i + 1));
    }
}
