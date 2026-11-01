import java.util.Map;

/** Minimal Interpreter: a surge-fee rule as a tiny expression tree, evaluated against the current conditions. */
public class SurgeFeeDemo {

    sealed interface Fee permits Fixed, PerKm, Plus, IfRaining { }                                  // (1)!
    record Fixed(long rupees) implements Fee { }
    record PerKm(long rupees) implements Fee { }
    record Plus(Fee a, Fee b) implements Fee { }
    record IfRaining(Fee then, Fee otherwise) implements Fee { }

    static long fee(Fee f, double km, boolean raining) {                                            // (2)!
        return switch (f) {
            case Fixed x -> x.rupees();
            case PerKm p -> Math.round(p.rupees() * km);
            case Plus(Fee a, Fee b) -> fee(a, km, raining) + fee(b, km, raining);
            case IfRaining(Fee t, Fee o) -> raining ? fee(t, km, raining) : fee(o, km, raining);
        };
    }

    public static void main(String[] args) {
        Fee rule = new Plus(new Fixed(20), new IfRaining(new PerKm(10), new PerKm(6)));
        for (Map.Entry<Double, Boolean> c : new java.util.TreeMap<>(Map.of(3.0, false, 5.0, true)).entrySet())
            System.out.println(c.getKey() + " km, raining=" + c.getValue() + " -> Rs " + fee(rule, c.getKey(), c.getValue()));
    }
}
