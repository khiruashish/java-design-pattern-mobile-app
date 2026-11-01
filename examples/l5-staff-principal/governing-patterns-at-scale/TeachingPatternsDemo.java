import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Teaching patterns to teams with a skills matrix: each engineer rates themselves 0 to 4 per pattern area. Gaps
 * become pairing sessions with a mentor (level 3 or more, spread so nobody mentors too many people) and a kata
 * from the playbook. Areas with no mentor at all are the real organisational risk.
 */
public class TeachingPatternsDemo {

    static final List<String> AREAS = List.of("resilience", "messaging", "caching", "testing");

    public static void main(String[] args) {
        Map<String, int[]> matrix = new TreeMap<>();                                               // (1)!
        matrix.put("Asha",  new int[] {4, 1, 3, 2});
        matrix.put("Bilal", new int[] {1, 1, 2, 4});
        matrix.put("Chen",  new int[] {2, 1, 4, 1});
        matrix.put("Dev",   new int[] {0, 1, 1, 1});
        Map<String, String> kata = Map.of("resilience", "resilience-patterns practice 1", "messaging", "messaging-patterns practice 2",
                "caching", "caching-patterns practice 1", "testing", "testing-patterns practice 3");

        Map<String, Integer> mentorLoad = new HashMap<>();
        for (int a = 0; a < AREAS.size(); a++) {
            String area = AREAS.get(a);
            List<String> mentors = new ArrayList<>(), learners = new ArrayList<>();
            for (var e : matrix.entrySet()) {
                if (e.getValue()[a] >= 3) mentors.add(e.getKey()); else if (e.getValue()[a] <= 1) learners.add(e.getKey());
            }
            if (mentors.isEmpty()) {                                                               // (2)!
                System.out.println(area + ": NO MENTOR for " + learners + ", bring in a guild expert or an external course");
                continue;
            }
            for (String learner : learners) {
                String mentor = mentors.stream().min(Comparator.comparingInt((String m) -> mentorLoad.getOrDefault(m, 0))
                        .thenComparing(Comparator.naturalOrder())).orElseThrow();                  // (3)!
                mentorLoad.merge(mentor, 1, Integer::sum);
                System.out.println(area + ": " + learner + " pairs with " + mentor + ", kata: " + kata.get(area));
            }
        }
        System.out.println("mentor load: " + new TreeMap<>(mentorLoad));
    }
}
