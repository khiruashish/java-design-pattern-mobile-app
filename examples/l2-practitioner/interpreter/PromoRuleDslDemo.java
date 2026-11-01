import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Realistic Interpreter: merchandisers write promo eligibility rules as text, e.g.
 *   category == "fruit" and cart_total >= 499 or tier == "gold"
 * The rule is parsed once into an AST, then evaluated for every cart. Errors point at the position.
 */
public class PromoRuleDslDemo {

    sealed interface Rule permits Cmp, And, Or { }
    record Cmp(String field, String op, Object value) implements Rule { }
    record And(Rule l, Rule r) implements Rule { }
    record Or(Rule l, Rule r) implements Rule { }

    record Tok(String text, int pos) { }

    static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ') { i++; continue; }
            int start = i;
            if (c == '"') { i = s.indexOf('"', i + 1) + 1; if (i == 0) throw new IllegalArgumentException("unclosed quote at " + start); }
            else if (Character.isLetterOrDigit(c) || c == '_') { while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++; }
            else if ("=<>!".indexOf(c) >= 0) { i += (i + 1 < s.length() && s.charAt(i + 1) == '=') ? 2 : 1; }
            else throw new IllegalArgumentException("unexpected '" + c + "' at " + i);
            out.add(new Tok(s.substring(start, i), start));
        }
        return out;
    }

    static final class Parser {                                                                     // (1)!
        private final List<Tok> t; private int p;
        Parser(String src) { t = lex(src); }
        Rule parse() { Rule r = or(); if (p < t.size()) throw err("unexpected '" + t.get(p).text() + "'"); return r; }
        Rule or() { Rule r = and(); while (is("or")) { p++; r = new Or(r, and()); } return r; }
        Rule and() { Rule r = cmp(); while (is("and")) { p++; r = new And(r, cmp()); } return r; }  // and binds tighter than or
        Rule cmp() {
            String field = next("field").text();
            String op = next("operator").text();
            if (!List.of("==", "!=", ">=", "<=", ">", "<").contains(op)) throw err("unknown operator '" + op + "'", p - 1);
            String raw = next("value").text();
            Object value = raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : Long.parseLong(raw);
            return new Cmp(field, op, value);
        }
        boolean is(String word) { return p < t.size() && t.get(p).text().equals(word); }
        Tok next(String what) { if (p >= t.size()) throw err("expected " + what + " at end of rule"); return t.get(p++); }
        IllegalArgumentException err(String m) { return err(m, p); }
        IllegalArgumentException err(String m, int idx) { return new IllegalArgumentException(m + (idx < t.size() ? " at position " + t.get(idx).pos() : "")); }   // (2)!
    }

    static boolean eval(Rule r, Map<String, Object> cart) {                                         // (3)!
        return switch (r) {
            case And(Rule l, Rule rr) -> eval(l, cart) && eval(rr, cart);
            case Or(Rule l, Rule rr) -> eval(l, cart) || eval(rr, cart);
            case Cmp(String f, String op, Object v) -> {
                Object actual = cart.get(f);
                if (v instanceof Long n && actual instanceof Long a) yield switch (op) {
                    case "==" -> a.equals(n); case "!=" -> !a.equals(n); case ">=" -> a >= n; case "<=" -> a <= n; case ">" -> a > n; default -> a < n; };
                yield switch (op) { case "==" -> v.equals(actual); case "!=" -> !v.equals(actual); default -> false; };
            }
        };
    }

    static String show(Rule r) {
        return switch (r) {
            case And(Rule l, Rule rr) -> "(" + show(l) + " and " + show(rr) + ")";
            case Or(Rule l, Rule rr) -> "(" + show(l) + " or " + show(rr) + ")";
            case Cmp(String f, String op, Object v) -> f + " " + op + " " + (v instanceof String s ? "\"" + s + "\"" : v);
        };
    }

    public static void main(String[] args) {
        Rule rule = new Parser("category == \"fruit\" and cart_total >= 499 or tier == \"gold\"").parse();
        System.out.println("parsed as: " + show(rule));
        List<Map<String, Object>> carts = List.of(
                Map.of("category", "fruit", "cart_total", 650L, "tier", "silver"),
                Map.of("category", "fruit", "cart_total", 300L, "tier", "silver"),
                Map.of("category", "dairy", "cart_total", 120L, "tier", "gold"));
        carts.forEach(c -> System.out.println(new java.util.TreeMap<>(c) + " -> " + eval(rule, c)));
        for (String bad : List.of("cart_total >= ", "tier = \"gold\"", "cart_total >= 499 and")) {
            try { new Parser(bad).parse(); } catch (IllegalArgumentException e) { System.out.println("rejected '" + bad + "': " + e.getMessage()); }
        }
    }
}
