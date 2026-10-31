import java.util.Locale;

/** Parameterised factory method: one method, a parameter decides the product. */
public class ParameterisedFactoryDemo {

    sealed interface DeliveryOption permits Standard, Express, Scheduled {
        long feeInCents();
        String describe();
    }
    record Standard() implements DeliveryOption {
        public long feeInCents() { return 499; }
        public String describe() { return "standard (same day)"; }
    }
    record Express(int minutes) implements DeliveryOption {
        public long feeInCents() { return 799; }
        public String describe() { return "express in " + minutes + " min"; }
    }
    record Scheduled(String slot) implements DeliveryOption {
        public long feeInCents() { return 299; }
        public String describe() { return "scheduled " + slot; }
    }

    enum Kind { STANDARD, EXPRESS, SCHEDULED }

    /** The factory method. Callers never write `new Express(...)` themselves. */
    static DeliveryOption create(Kind kind, String slot) {                             // (1)!
        return switch (kind) {                                                          // (2)!
            case STANDARD -> new Standard();
            case EXPRESS -> new Express(30);
            case SCHEDULED -> {
                if (slot == null || !slot.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) throw new IllegalArgumentException("scheduled delivery needs a slot like 18:00-19:00");
                yield new Scheduled(slot);
            }
        };
    }

    /** Parsing user input is the factory's job too, with a helpful error. */
    static DeliveryOption fromRequest(String kind, String slot) {                       // (3)!
        try {
            return create(Kind.valueOf(kind.trim().toUpperCase(Locale.ROOT)), slot);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("No enum constant")) throw new IllegalArgumentException("unknown delivery kind '" + kind + "', use one of " + java.util.Arrays.toString(Kind.values()));
            throw e;
        }
    }

    public static void main(String[] args) {
        for (String[] req : new String[][] {{"standard", null}, {"Express", null}, {"scheduled", "18:00-19:00"}, {"drone", null}, {"scheduled", "evening"}}) {
            try {
                DeliveryOption o = fromRequest(req[0], req[1]);
                System.out.println(req[0] + " -> " + o.describe() + ", fee " + o.feeInCents());
            } catch (IllegalArgumentException e) {
                System.out.println(req[0] + " -> rejected: " + e.getMessage());
            }
        }
    }
}
