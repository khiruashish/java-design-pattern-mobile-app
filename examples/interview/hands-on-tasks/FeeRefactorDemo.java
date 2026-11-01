import java.util.List;

/**
 * Task 3 solution: the legacy fee calculator (a switch on strings with duplicated rules) refactored to a sealed set
 * of fee rules chosen by a factory. A characterisation check proves both versions agree on every sample before the
 * legacy code is deleted.
 */
public class FeeRefactorDemo {

    // ---- before: the code given in the task ----
    static long legacyFee(String customerType, String city, long basketPaise) {
        switch (customerType) {
            case "MEMBER":
                return 0;
            case "REGULAR":
                if (city.equals("Pune")) return basketPaise >= 60000 ? 0 : 3900;
                return basketPaise >= 50000 ? 0 : 2900;
            case "CORPORATE":
                if (city.equals("Pune")) return 1500;
                return 1500;
            default:
                throw new IllegalArgumentException(customerType);
        }
    }

    // ---- after ----
    sealed interface FeeRule permits Free, FreeAbove, Flat {
        long feePaise(long basketPaise);
    }
    record Free() implements FeeRule { public long feePaise(long b) { return 0; } }
    record FreeAbove(long thresholdPaise, long feePaise) implements FeeRule {
        public long feePaise(long b) { return b >= thresholdPaise ? 0 : feePaise; }
    }
    record Flat(long feePaise) implements FeeRule { public long feePaise(long b) { return feePaise; } }

    enum CustomerType { MEMBER, REGULAR, CORPORATE }

    static FeeRule ruleFor(CustomerType type, String city) {
        return switch (type) {
            case MEMBER -> new Free();
            case REGULAR -> city.equals("Pune") ? new FreeAbove(60000, 3900) : new FreeAbove(50000, 2900);
            case CORPORATE -> new Flat(1500);
        };
    }

    public static void main(String[] args) {
        int checked = 0, mismatches = 0;
        for (CustomerType type : CustomerType.values()) {
            for (String city : List.of("Bengaluru", "Pune")) {
                for (long basket : new long[] {0, 49999, 50000, 59999, 60000, 150000}) {
                    long before = legacyFee(type.name(), city, basket);
                    long after = ruleFor(type, city).feePaise(basket);
                    checked++;
                    if (before != after) { mismatches++; System.out.println("MISMATCH " + type + " " + city + " " + basket); }
                }
            }
        }
        System.out.println("characterisation: " + checked + " cases, " + mismatches + " mismatches");
        System.out.println("REGULAR in Pune, Rs 550 basket: rule " + ruleFor(CustomerType.REGULAR, "Pune") + ", fee " + ruleFor(CustomerType.REGULAR, "Pune").feePaise(55000) + " paise");
        System.out.println("CORPORATE anywhere: " + ruleFor(CustomerType.CORPORATE, "Bengaluru"));
    }
}
