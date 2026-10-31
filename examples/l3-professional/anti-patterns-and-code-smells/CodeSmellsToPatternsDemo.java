import java.util.List;

/** Repeated switches on a type code are a smell; moving the behaviour into the type removes every switch at once. */
public class CodeSmellsToPatternsDemo {

    // ---- Before: the same type-code switch appears in several places. ----
    static final String BIKE = "BIKE", CAR = "CAR", CYCLE = "CYCLE";

    static int capacityKg(String vehicle) {                                   // (1)!
        switch (vehicle) {
            case "BIKE": return 20;
            case "CAR": return 150;
            case "CYCLE": return 8;
            default: throw new IllegalArgumentException(vehicle);
        }
    }
    static int minutesFor(String vehicle, int km) {
        switch (vehicle) {
            case "BIKE": return km * 3;
            case "CAR": return km * 4 + 5;   // parking time
            case "CYCLE": return km * 6;
            default: throw new IllegalArgumentException(vehicle);
        }
    }

    // ---- After: an enum with behaviour. A new vehicle is one new constant; the compiler checks the rest. ----
    enum Vehicle {                                                            // (2)!
        BIKE(20) { int minutesFor(int km) { return km * 3; } },
        CAR(150) { int minutesFor(int km) { return km * 4 + 5; } },
        CYCLE(8) { int minutesFor(int km) { return km * 6; } };
        final int capacityKg;
        Vehicle(int capacityKg) { this.capacityKg = capacityKg; }
        abstract int minutesFor(int km);
    }

    public static void main(String[] args) {
        for (String code : List.of(BIKE, CAR, CYCLE)) {
            Vehicle v = Vehicle.valueOf(code);
            System.out.println(code + ": before " + capacityKg(code) + " kg/" + minutesFor(code, 4) + " min, after "
                    + v.capacityKg + " kg/" + v.minutesFor(4) + " min");
        }
        try {
            capacityKg("TRUCK");                                              // (3)!
        } catch (IllegalArgumentException e) {
            System.out.println("type-code typo found only at runtime: " + e.getMessage());
        }
    }
}
