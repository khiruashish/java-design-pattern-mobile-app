import java.time.LocalTime;
import java.util.Map;

/** Realistic Strategy: delivery-slot pricing differs per city; each city's pricing team owns its strategy. */
public class SlotPricingDemo {

    record SlotContext(LocalTime start, double demandRatio, boolean rain) { }             // demandRatio = booked / capacity

    interface SlotPricing { long priceCents(SlotContext ctx); String name(); }

    /** Pune: flat price, small evening premium. */
    record FlatWithPeak(long base, long peakExtra) implements SlotPricing {
        public long priceCents(SlotContext c) { return base + (isEvening(c.start()) ? peakExtra : 0); }
        public String name() { return "flat+peak"; }
    }

    /** Mumbai: demand-based surge with a hard cap, plus a rain surcharge. */
    record DemandSurge(long base, double maxMultiplier, long rainExtra) implements SlotPricing {
        public long priceCents(SlotContext c) {
            double multiplier = c.demandRatio() <= 0.7 ? 1.0 : Math.min(maxMultiplier, 1.0 + (c.demandRatio() - 0.7) * 2);   // (1)!
            return Math.round(base * multiplier) + (c.rain() ? rainExtra : 0);
        }
        public String name() { return "demand-surge"; }
    }

    /** New cities: free delivery to grow, but never in the last free slot. */
    record LaunchOffer(SlotPricing afterFull) implements SlotPricing {                  // (2)!
        public long priceCents(SlotContext c) { return c.demandRatio() >= 0.9 ? afterFull.priceCents(c) : 0; }
        public String name() { return "launch-offer"; }
    }

    static boolean isEvening(LocalTime t) { return !t.isBefore(LocalTime.of(17, 0)) && t.isBefore(LocalTime.of(21, 0)); }

    static final Map<String, SlotPricing> BY_CITY = Map.of(                               // (3)!
            "pune", new FlatWithPeak(2_900, 1_000),
            "mumbai", new DemandSurge(3_900, 1.5, 1_500),
            "nashik", new LaunchOffer(new FlatWithPeak(1_900, 0)));

    public static void main(String[] args) {
        SlotContext quietMorning = new SlotContext(LocalTime.of(9, 0), 0.4, false);
        SlotContext busyRainyEvening = new SlotContext(LocalTime.of(19, 0), 0.95, true);
        for (String city : new String[]{"pune", "mumbai", "nashik"}) {
            SlotPricing p = BY_CITY.get(city);
            System.out.printf("%-7s %-13s morning %5d  rainy evening %5d%n", city, p.name(), p.priceCents(quietMorning), p.priceCents(busyRainyEvening));
        }
    }
}
