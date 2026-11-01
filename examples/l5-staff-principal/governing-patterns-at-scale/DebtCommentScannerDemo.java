import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Making technical debt visible where it lives: a structured comment format with owner and expiry date replaces
 * anonymous TODOs. A scanner (run in CI) builds a per-team register and flags expired items and untracked TODOs.
 */
public class DebtCommentScannerDemo {

    static final Pattern DEBT = Pattern.compile("//\\s*DEBT\\(owner=([\\w-]+), expires=(\\d{4}-\\d{2}-\\d{2})\\):\\s*(.+)"); // (1)!
    static final Pattern TODO = Pattern.compile("//\\s*(TODO|FIXME)\\b");

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 1);                                               // (2)!
        Map<String, String> sources = new TreeMap<>();
        sources.put("checkout/CheckoutService.java", """
                // DEBT(owner=checkout-team, expires=2026-09-30): split pricing out of CheckoutService
                // DEBT(owner=checkout-team, expires=2027-03-31): replace manual retries with resilience4j
                """);
        sources.put("loyalty/PointsClient.java", """
                // TODO make this faster
                // DEBT(owner=growth-team, expires=2027-01-15): remove ServiceLocator lookup
                """);

        Map<String, int[]> perTeam = new TreeMap<>();                                              // owner -> {open, expired}
        sources.forEach((file, code) -> code.lines().forEach(line -> {
            Matcher d = DEBT.matcher(line);
            if (d.find()) {
                boolean expired = LocalDate.parse(d.group(2)).isBefore(today);
                int[] counts = perTeam.computeIfAbsent(d.group(1), k -> new int[2]);
                counts[0]++;
                if (expired) {
                    counts[1]++;
                    System.out.println("EXPIRED " + file + " (" + d.group(1) + ", " + d.group(2) + "): " + d.group(3));
                }
            } else if (TODO.matcher(line).find()) {
                System.out.println("UNTRACKED " + file + ": " + line.strip() + " -> add owner and expiry");
            }
        }));
        perTeam.forEach((team, c) -> System.out.println(team + ": open debt " + c[0] + ", expired " + c[1])); // (3)!
    }
}
