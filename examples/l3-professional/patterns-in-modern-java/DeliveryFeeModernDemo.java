import java.util.List;

/** Minimal: GreenValley's delivery fee with the three modern tools together: records, a sealed interface, a switch. */
public class DeliveryFeeModernDemo {

    sealed interface Destination permits City, Suburb, Village { }                                    // (1)!
    record City(double km) implements Destination { }
    record Suburb(double km, boolean tollRoad) implements Destination { }
    record Village(String name, double km) implements Destination { }

    static long feeRupees(Destination d) {
        return switch (d) {                                                                           // (2)!
            case City c when c.km() <= 3 -> 0;
            case City c -> 20 + Math.round((c.km() - 3) * 5);
            case Suburb s -> 40 + Math.round(s.km() * 4) + (s.tollRoad() ? 30 : 0);
            case Village v -> 80 + Math.round(v.km() * 6);
        };
    }

    public static void main(String[] args) {
        for (Destination d : List.of(new City(2.5), new City(6), new Suburb(10, true), new Village("Khed", 18)))
            System.out.println(d + " -> Rs " + feeRupees(d));
    }
}
