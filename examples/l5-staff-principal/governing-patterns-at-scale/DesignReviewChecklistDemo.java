import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A risk-based design review checklist: each question has a weight, and some answers trigger a mandatory
 * architecture review. Unanswered questions count as risk. The total decides how much review a design needs, so
 * small changes move fast and risky ones get senior eyes.
 */
public class DesignReviewChecklistDemo {

    record Question(String id, String text, int weight, boolean escalatesWhenYes) {}               // (1)!

    static final List<Question> QUESTIONS = List.of(
            new Question("data", "Does it change who owns a database or data contract?", 3, true),
            new Question("money", "Does it touch payments, refunds or pricing?", 3, false),
            new Question("partner", "Does it change an API used by partners (QuickDrop, ParcelGo, PayLink)?", 3, true),
            new Question("new-pattern", "Does it introduce a pattern marked TRIAL or HOLD?", 2, false),
            new Question("rollback", "Is rollback harder than a redeploy?", 2, false),
            new Question("traffic", "Will it handle more than 1000 requests per second?", 1, false));

    enum Level { SELF_SERVE, PEER_REVIEW, ARCHITECTURE_REVIEW }

    static void review(String design, Map<String, Boolean> answers) {
        int risk = 0;
        boolean escalate = false;
        List<String> open = new ArrayList<>();
        for (Question q : QUESTIONS) {
            Boolean yes = answers.get(q.id());
            if (yes == null) {                                                                     // (2)!
                risk += q.weight();
                open.add(q.id());
            } else if (yes) {
                risk += q.weight();
                escalate |= q.escalatesWhenYes();
            }
        }
        Level level = escalate || risk >= 6 ? Level.ARCHITECTURE_REVIEW : risk >= 3 ? Level.PEER_REVIEW : Level.SELF_SERVE; // (3)!
        System.out.println(design + ": risk " + risk + " -> " + level + (open.isEmpty() ? "" : ", unanswered " + open));
    }

    public static void main(String[] args) {
        review("Add banner text to home screen", Map.of("data", false, "money", false, "partner", false,
                "new-pattern", false, "rollback", false, "traffic", true));
        review("Cache delivery slots with a TRIAL pattern", Map.of("data", false, "money", false, "partner", false,
                "new-pattern", true, "rollback", false, "traffic", true));
        review("Split loyalty points into its own service", Map.of("data", true, "money", true, "partner", false,
                "new-pattern", false, "rollback", true));
    }
}
