import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production: ParcelGo's nightly rider settlement. "Functional core, imperative shell":
 * - the shell reads raw CSV lines and prints results; everything between is pure functions on immutable data
 * - each line parses to a Result, so one bad line doesn't abort the night's run and every error is reported
 * - money is BigDecimal with explicit rounding; per-zone rates are memoized with a thread-safe cache
 * - the pure pipeline runs in parallel safely and gives the same, sorted output every time
 */
public class RiderSettlementDemo {

    record Delivery(String riderId, String zone, double km, boolean late) { }
    record Payout(String riderId, int deliveries, BigDecimal amount) { }

    sealed interface Parsed permits Good, Bad { }
    record Good(Delivery d) implements Parsed { }
    record Bad(int lineNo, String line, String reason) implements Parsed { }

    static Parsed parse(int lineNo, String line) {                                                     // (1)!
        String[] f = line.split(",");
        if (f.length != 4) return new Bad(lineNo, line, "expected 4 fields");
        try {
            double km = Double.parseDouble(f[2]);
            if (km <= 0 || km > 50) return new Bad(lineNo, line, "km out of range");
            if (!f[3].equals("on_time") && !f[3].equals("late")) return new Bad(lineNo, line, "status must be on_time or late");
            return new Good(new Delivery(f[0], f[1], km, f[3].equals("late")));
        } catch (NumberFormatException e) { return new Bad(lineNo, line, "km is not a number"); }
    }

    record Rate(BigDecimal base, BigDecimal perKm) { }
    static final Map<String, Rate> RATE_CACHE = new ConcurrentHashMap<>();
    static int rateLookups;
    static Rate rate(String zone) {                                                                    // (2)!
        return RATE_CACHE.computeIfAbsent(zone, z -> {
            synchronized (RiderSettlementDemo.class) { rateLookups++; }
            return z.equals("CENTRAL") ? new Rate(new BigDecimal("25"), new BigDecimal("6.5")) : new Rate(new BigDecimal("30"), new BigDecimal("8"));
        });
    }

    static BigDecimal earning(Delivery d) {                                                           // (3)!
        Rate r = rate(d.zone());
        BigDecimal gross = r.base().add(r.perKm().multiply(BigDecimal.valueOf(d.km())));
        BigDecimal adjusted = d.late() ? gross.multiply(new BigDecimal("0.90")) : gross;
        return adjusted.setScale(2, RoundingMode.HALF_EVEN);
    }

    static Map<String, Payout> settle(List<Delivery> deliveries) {                                     // (4)!
        return deliveries.parallelStream().collect(Collectors.groupingBy(Delivery::riderId, TreeMap::new,
                Collectors.collectingAndThen(Collectors.toList(), ds -> new Payout(ds.get(0).riderId(), ds.size(),
                        ds.stream().map(RiderSettlementDemo::earning).reduce(BigDecimal.ZERO, BigDecimal::add)))));
    }

    public static void main(String[] args) {
        List<String> csv = List.of(                                                                    // the shell: input
                "R1,CENTRAL,3.2,on_time", "R2,OUTER,7.5,late", "R1,CENTRAL,1.8,on_time",
                "R3,OUTER,12.0,on_time", "R2,OUTER,abc,on_time", "R1,CENTRAL,2.5", "R3,CENTRAL,4.0,late", "R2,OUTER,80,on_time");

        List<Parsed> parsed = java.util.stream.IntStream.range(0, csv.size()).mapToObj(i -> parse(i + 1, csv.get(i))).toList();
        List<Delivery> good = parsed.stream().flatMap(p -> p instanceof Good g ? java.util.stream.Stream.of(g.d()) : java.util.stream.Stream.empty()).toList();
        List<Bad> bad = parsed.stream().flatMap(p -> p instanceof Bad b ? java.util.stream.Stream.of(b) : java.util.stream.Stream.empty()).toList();

        Map<String, Payout> run1 = settle(good), run2 = settle(good);
        run1.values().forEach(p -> System.out.println(p.riderId() + ": deliveries=" + p.deliveries() + ", payout Rs " + p.amount()));
        System.out.println("total Rs " + run1.values().stream().map(Payout::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        bad.forEach(b -> System.out.println("skipped line " + b.lineNo() + " (" + b.reason() + "): " + b.line()));
        System.out.println("rate lookups: " + rateLookups + ", two parallel runs identical: " + run1.equals(run2));   // (5)!
    }
}
