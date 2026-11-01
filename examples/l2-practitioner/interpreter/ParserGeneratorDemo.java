import java.util.Arrays;
import java.util.List;

/** Why ad-hoc string tricks fail for real languages: splitting on AND/OR ignores parentheses and precedence. */
public class ParserGeneratorDemo {

    static boolean evalNaive(String rule, List<String> tags) {                                    // (1)!
        String[] ors = rule.split(" OR ");
        return Arrays.stream(ors).anyMatch(part -> Arrays.stream(part.split(" AND "))
                .allMatch(atom -> tags.contains(atom.replace("(", "").replace(")", "").trim())));
    }

    /** Tiny recursive-descent evaluator that respects parentheses. */
    static final class Rd {
        private final String[] t; private int p; private final List<String> tags;
        Rd(String s, List<String> tags) { t = s.replace("(", " ( ").replace(")", " ) ").trim().split("\\s+"); this.tags = tags; }
        boolean or() { boolean v = and(); while (p < t.length && t[p].equals("OR")) { p++; v = and() | v; } return v; }   // (2)!
        boolean and() { boolean v = atom(); while (p < t.length && t[p].equals("AND")) { p++; v = atom() & v; } return v; }
        boolean atom() { String tok = t[p++]; if (tok.equals("(")) { boolean v = or(); p++; return v; } return tags.contains(tok); }
    }

    public static void main(String[] args) {
        String rule = "gold AND (fruit OR dairy)";
        List<String> silverBuyingDairy = List.of("silver", "dairy");
        System.out.println("rule: " + rule + ", customer: " + silverBuyingDairy);
        System.out.println("naive split:      " + evalNaive(rule, silverBuyingDairy) + "   <- wrong: parentheses ignored");
        System.out.println("recursive descent: " + new Rd(rule, silverBuyingDairy).or());
        System.out.println("grammar growing (functions, dates, lists, error recovery)? use a parser generator such as ANTLR");   // (3)!
    }
}
