import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FreshCart's quarterly engineering health scorecard. It combines signals from the governance tools (catalogue lint,
 * reference drift, debt register, code review data) into one score per team, compares it with last quarter, guards
 * against gamed metrics, and turns results into offers of help rather than rankings.
 */
public class GovernanceScorecardDemo {

    record TeamSignals(String team, int holdViolations, int driftPercent, double debtInterestDaysPerMonth,
                       int reviewCoveragePercent, double medianReviewMinutes, int lastQuarterScore) {}

    record Result(String team, int score, int trend, List<String> flags, String action) {}

    static final Map<String, Integer> WEIGHTS = new LinkedHashMap<>();                             // (1)!
    static {
        WEIGHTS.put("catalogue", 30);
        WEIGHTS.put("reference", 25);
        WEIGHTS.put("debt", 25);
        WEIGHTS.put("review", 20);
    }

    static Map<String, Integer> normalise(TeamSignals s, List<String> flags) {
        Map<String, Integer> n = new LinkedHashMap<>();
        n.put("catalogue", Math.max(0, 100 - 20 * s.holdViolations()));                             // (2)!
        n.put("reference", 100 - s.driftPercent());
        n.put("debt", (int) Math.max(0, Math.round(100 - 10 * s.debtInterestDaysPerMonth())));
        int review = s.reviewCoveragePercent();
        if (review >= 90 && s.medianReviewMinutes() < 3) {                                         // (3)!
            flags.add("rubber-stamp reviews suspected (median " + s.medianReviewMinutes() + " min)");
            review = Math.min(review, 50);
        }
        n.put("review", review);
        return n;
    }

    static Result evaluate(TeamSignals s) {
        List<String> flags = new ArrayList<>();
        Map<String, Integer> n = normalise(s, flags);
        int total = 0;
        for (var w : WEIGHTS.entrySet()) total += w.getValue() * n.get(w.getKey());
        int score = Math.round(total / 100f);
        int trend = score - s.lastQuarterScore();                                                  // (4)!
        String action = score < 60 ? "offer guild pairing and a reference migration sprint"         // (5)!
                : trend <= -10 ? "tech lead check-in: what changed this quarter?"
                : !flags.isEmpty() ? "review practices workshop"
                : "share what works at the guild demo";
        return new Result(s.team(), score, trend, flags, action);
    }

    public static void main(String[] args) {
        List<TeamSignals> teams = List.of(
                new TeamSignals("checkout", 0, 0, 1.0, 95, 24, 92),
                new TeamSignals("catalogue", 1, 50, 2.5, 88, 18, 85),
                new TeamSignals("loyalty", 2, 83, 4.0, 100, 1.5, 49));
        System.out.println("weights " + WEIGHTS);
        for (TeamSignals t : teams) {
            Result r = evaluate(t);
            System.out.printf("%-9s score %3d (%+d vs last quarter) -> %s%n", r.team(), r.score(), r.trend(), r.action());
            r.flags().forEach(f -> System.out.println("          flag: " + f));
        }
    }
}
