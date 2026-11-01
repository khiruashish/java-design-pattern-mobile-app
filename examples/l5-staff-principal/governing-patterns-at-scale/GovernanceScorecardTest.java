import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GovernanceScorecardTest {

    private static GovernanceScorecardDemo.TeamSignals team(int violations, int drift, double interest, int coverage, double minutes, int last) {
        return new GovernanceScorecardDemo.TeamSignals("t", violations, drift, interest, coverage, minutes, last);
    }

    @Test
    void weightsAddUpToOneHundred() {
        assertEquals(100, GovernanceScorecardDemo.WEIGHTS.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void perfectTeamScoresOneHundred() {
        var r = GovernanceScorecardDemo.evaluate(team(0, 0, 0, 100, 20, 100));
        assertEquals(100, r.score());
        assertEquals(0, r.trend());
        assertTrue(r.flags().isEmpty());
    }

    @Test
    void normalisedSignalsAreClampedToZero() {
        var n = GovernanceScorecardDemo.normalise(team(9, 100, 50, 0, 20, 0), new ArrayList<>());
        assertEquals(List.of(0, 0, 0, 0), List.copyOf(n.values()));
    }

    @Test
    void rubberStampReviewsAreCappedAndFlagged() {
        List<String> flags = new ArrayList<>();
        var n = GovernanceScorecardDemo.normalise(team(0, 0, 0, 100, 1, 0), flags);
        assertEquals(50, n.get("review"));
        assertEquals(1, flags.size());
    }

    @Test
    void lowScoresGetHelpAndSharpDropsGetACheckIn() {
        assertTrue(GovernanceScorecardDemo.evaluate(team(3, 80, 6, 50, 20, 40)).action().startsWith("offer guild pairing"));
        assertTrue(GovernanceScorecardDemo.evaluate(team(1, 40, 2, 90, 20, 95)).action().startsWith("tech lead check-in"));
    }
}
