import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A pattern catalogue as data, in the style of a technology radar: each entry has a status (ADOPT, TRIAL, HOLD), an
 * owner and the preferred alternative. A lint rule per HOLD entry scans code and points developers to the guidance,
 * so the catalogue is enforced by tooling instead of by memory.
 */
public class PatternCatalogueDemo {

    enum Status { ADOPT, TRIAL, HOLD }

    record Entry(String pattern, Status status, String owner, String guidance, Pattern detector) {}  // (1)!

    static final List<Entry> CATALOGUE = List.of(
            new Entry("Constructor injection", Status.ADOPT, "platform-guild", "default for all Spring beans", null),
            new Entry("Transactional outbox", Status.ADOPT, "data-guild", "use freshcart-outbox starter", null),
            new Entry("Event sourcing", Status.TRIAL, "data-guild", "only with an approved design review", null),
            new Entry("Service locator", Status.HOLD, "platform-guild", "use constructor injection instead",
                    Pattern.compile("\\.getBean\\(")),                                  // (2)!
            new Entry("Mutable static singleton", Status.HOLD, "platform-guild", "inject a bean, or use an immutable enum",
                    Pattern.compile("static\\s+(?!final)\\w+(<[^>]*>)?\\s+\\w+\\s*=\\s*new\\s")));

    public static void main(String[] args) {
        System.out.println("catalogue:");
        for (Entry e : CATALOGUE) System.out.printf("  %-6s %-26s owner %s%n", e.status(), e.pattern(), e.owner());

        Map<String, String> sources = new TreeMap<>();
        sources.put("ordering/OrderService.java", "class OrderService { Pricing p = ctx.getBean(Pricing.class); }");
        sources.put("pricing/PriceCache.java", "class PriceCache { static Map<String, Long> CACHE = new HashMap<>(); }");
        sources.put("pricing/PricingService.java", "class PricingService { PricingService(PriceRepository repo) {} }");

        System.out.println("lint:");
        sources.forEach((file, code) -> CATALOGUE.stream()
                .filter(e -> e.status() == Status.HOLD && e.detector().matcher(code).find())
                .forEach(e -> System.out.println("  " + file + ": " + e.pattern() + " is HOLD, " + e.guidance() + " (ask " + e.owner() + ")"))); // (3)!
    }
}
