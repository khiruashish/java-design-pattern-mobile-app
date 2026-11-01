import java.util.ArrayList;
import java.util.List;

/**
 * Realistic: a design review for FreshCart's delivery-fee rules. Instead of arguing taste, the reviewer records the
 * forces (variants, change rate, who changes them, need for runtime changes) and applies explicit thresholds, so
 * the recommendation and its reasons can go straight into an ADR.
 */
public class DeliveryFeeReviewDemo {

    record Forces(int variants, int changesPerMonth, int teamsEditing, boolean businessEditsWithoutDeploy) {}
    record Recommendation(String design, List<String> reasons) {}

    static Recommendation review(Forces f) {
        List<String> reasons = new ArrayList<>();
        if (f.businessEditsWithoutDeploy()) {                                                         // (1)!
            reasons.add("business must change rules without a deploy");
            if (f.variants() > 20) reasons.add(f.variants() + " rules: a table-driven or rules-engine approach scales better than classes");
            return new Recommendation(f.variants() > 20 ? "rules engine / decision table" : "rules as data in config", reasons);
        }
        if (f.variants() >= 3 && (f.changesPerMonth() >= 2 || f.teamsEditing() > 1)) {               // (2)!
            reasons.add(f.variants() + " variants changing " + f.changesPerMonth() + "x a month");
            if (f.teamsEditing() > 1) reasons.add(f.teamsEditing() + " teams edit rules: separate classes avoid merge conflicts");
            return new Recommendation("Strategy per rule, selected by a small registry", reasons);
        }
        reasons.add("few variants (" + f.variants() + "), rarely changed (" + f.changesPerMonth() + "x a month)");
        return new Recommendation("keep the enum + switch; revisit at the next variant", reasons);     // (3)!
    }

    public static void main(String[] args) {
        Forces[] snapshots = {
                new Forces(3, 0, 1, false),
                new Forces(11, 4, 3, false),
                new Forces(40, 30, 1, true)};
        String[] when = {"2025 Q1", "2026 Q2", "2026 Q4 proposal"};
        for (int i = 0; i < snapshots.length; i++) {
            Recommendation r = review(snapshots[i]);
            System.out.println(when[i] + ": " + r.design());
            r.reasons().forEach(reason -> System.out.println("    because " + reason));
        }
    }
}
