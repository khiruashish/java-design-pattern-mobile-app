import java.util.ArrayList;
import java.util.List;

/**
 * Architecture Decision Records: short, numbered, immutable documents (context, decision, consequences, status).
 * Decisions aren't edited when they change; a new ADR supersedes the old one, so the history stays readable.
 */
public class AdrDemo {

    record Adr(int number, String title, String status, String context, String decision, String consequences, Integer supersedes) {
        String toMarkdown() {                                                                        // (1)!
            return "# ADR-" + String.format("%04d", number) + ": " + title + "\n\n"
                    + "Status: " + status + (supersedes == null ? "" : " (supersedes ADR-" + String.format("%04d", supersedes) + ")") + "\n\n"
                    + "## Context\n" + context + "\n\n## Decision\n" + decision + "\n\n## Consequences\n" + consequences + "\n";
        }
    }

    static List<Adr> supersede(List<Adr> log, int old, Adr replacement) {                          // (2)!
        List<Adr> next = new ArrayList<>();
        for (Adr a : log)
            next.add(a.number() == old ? new Adr(a.number(), a.title(), "Superseded by ADR-" + String.format("%04d", replacement.number()),
                    a.context(), a.decision(), a.consequences(), a.supersedes()) : a);
        next.add(replacement);
        return next;
    }

    public static void main(String[] args) {
        List<Adr> log = List.of(new Adr(7, "Delivery fees as an enum switch", "Accepted",
                "Three fee rules, changed twice a year.", "Use an enum with a switch in DeliveryFeeCalculator.",
                "Simple; adding a rule needs a deploy.", null));
        Adr adr12 = new Adr(12, "Delivery fees as Strategy beans", "Accepted",
                "Eleven fee rules, city teams change them weekly.", "One Strategy bean per rule, selected by city and slot.",
                "Rules testable in isolation; one more indirection; enum removed.", 7);
        log = supersede(log, 7, adr12);
        System.out.print(adr12.toMarkdown());
        System.out.println();
        log.forEach(a -> System.out.println("ADR-" + String.format("%04d", a.number()) + " " + a.title() + " [" + a.status() + "]"));   // (3)!
    }
}
