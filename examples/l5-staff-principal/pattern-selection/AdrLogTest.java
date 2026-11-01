import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class AdrLogTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    static final String BODY = "## Context\n.\n## Decision\n.\n## Consequences\n.\n";

    static List<String> check(Map<String, String> adrs, Map<String, String> sources) {
        return AdrLogDemo.check(new TreeMap<>(adrs), new TreeMap<>(sources), TODAY, new TreeMap<>());
    }

    @Test
    void wellFormedLogHasNoProblems() {
        var adrs = Map.of("1.md", "# ADR-0001: Use Java 21\nStatus: Accepted\nReview-by: 2027-01-01\n" + BODY);
        assertEquals(List.of(), check(adrs, Map.of("A.java", "// see ADR-0001")));
    }

    @Test
    void missingRequiredSectionIsReported() {
        var problems = check(Map.of("1.md", "# ADR-0001: X\nStatus: Accepted\n## Context\n.\n## Decision\n.\n"), Map.of());
        assertEquals(List.of("1.md: missing section 'Consequences'"), problems);
    }

    @Test
    void unknownStatusIsRejected() {
        var problems = check(Map.of("1.md", "# ADR-0001: X\nStatus: Maybe\n" + BODY), Map.of());
        assertTrue(problems.get(0).contains("missing or unknown Status"));
    }

    @Test
    void danglingSupersededLinkIsReported() {
        var problems = check(Map.of("1.md", "# ADR-0001: X\nStatus: Superseded by ADR-0009\n" + BODY), Map.of());
        assertEquals(List.of("ADR-0001: superseded by missing ADR-0009"), problems);
    }

    @Test
    void overdueReviewOnlyForAcceptedDecisions() {
        var problems = check(Map.of(
                "1.md", "# ADR-0001: X\nStatus: Accepted\nReview-by: 2026-01-01\n" + BODY,
                "2.md", "# ADR-0002: Y\nStatus: Deprecated\nReview-by: 2026-01-01\n" + BODY), Map.of());
        assertEquals(List.of("ADR-0001: review was due 2026-01-01"), problems);
    }

    @Test
    void codeCitingSupersededAdrPointsToReplacement() {
        var problems = check(Map.of(
                "1.md", "# ADR-0001: Old\nStatus: Superseded by ADR-0002\n" + BODY,
                "2.md", "# ADR-0002: New\nStatus: Accepted\n" + BODY), Map.of("Fee.java", "// ADR-0001"));
        assertEquals(List.of("Fee.java: cites superseded ADR-0001; see ADR-0002"), problems);
    }
}
