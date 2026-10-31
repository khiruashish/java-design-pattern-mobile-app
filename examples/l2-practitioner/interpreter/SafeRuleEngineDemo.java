import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade interpreter for rules written by non-developers (promo eligibility, fraud flags):
 * - full grammar with precedence: or < and < not < comparison; parentheses; numbers, strings, booleans
 * - LIMITS checked while parsing: source length, nesting depth, node count (untrusted input can't exhaust the server)
 * - TYPE CHECKING against a schema before any evaluation: unknown fields, comparing text with numbers, non-boolean rules
 * - compiled ASTs are cached by source text; evaluation can EXPLAIN why a rule matched
 *
 *   or  := and ("or" and)*      and := not ("and" not)*      not := "not" not | cmp
 *   cmp := primary (op primary)?   primary := NUMBER | STRING | true | false | IDENT | "(" or ")"
 */
public class SafeRuleEngineDemo {

    sealed interface Node permits Lit, Field, Cmp, And, Or, Not { }
    record Lit(Object value) implements Node { }
    record Field(String name) implements Node { }
    record Cmp(String op, Node l, Node r) implements Node { }
    record And(Node l, Node r) implements Node { }
    record Or(Node l, Node r) implements Node { }
    record Not(Node e) implements Node { }

    static final int MAX_LENGTH = 500, MAX_DEPTH = 12, MAX_NODES = 60;

    static final class Parser {
        private final List<String> t = new ArrayList<>(); private final List<Integer> at = new ArrayList<>();
        private int p, depth, nodes;

        Parser(String src) {
            if (src.length() > MAX_LENGTH) throw new IllegalArgumentException("rule longer than " + MAX_LENGTH + " chars");   // (1)!
            for (int i = 0; i < src.length(); ) {
                char c = src.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                int s = i;
                if (c == '"') { int e = src.indexOf('"', i + 1); if (e < 0) throw new IllegalArgumentException("unclosed quote at " + i); i = e + 1; }
                else if (Character.isLetterOrDigit(c) || c == '_') { while (i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++; }
                else if ("=!<>".indexOf(c) >= 0) { i += (i + 1 < src.length() && src.charAt(i + 1) == '=') ? 2 : 1; }
                else if (c == '(' || c == ')') { i++; }
                else throw new IllegalArgumentException("unexpected '" + c + "' at " + i);
                t.add(src.substring(s, i)); at.add(s);
            }
        }

        Node parse() { Node n = or(); if (p < t.size()) throw err("unexpected '" + t.get(p) + "'"); return n; }
        private Node node(Node n) { if (++nodes > MAX_NODES) throw err("rule has more than " + MAX_NODES + " parts"); return n; }
        private Node or() { Node n = and(); while (is("or")) { p++; n = node(new Or(n, and())); } return n; }
        private Node and() { Node n = not(); while (is("and")) { p++; n = node(new And(n, not())); } return n; }
        private Node not() { if (is("not")) { p++; return node(new Not(not())); } return cmp(); }
        private Node cmp() {
            Node l = primary();
            if (p < t.size() && List.of("==", "!=", ">=", "<=", ">", "<").contains(t.get(p))) { String op = t.get(p++); return node(new Cmp(op, l, primary())); }
            return l;
        }
        private Node primary() {
            if (p >= t.size()) throw err("rule ends too early");
            String tok = t.get(p++);
            if (tok.equals("(")) {
                if (++depth > MAX_DEPTH) throw err("parentheses nested deeper than " + MAX_DEPTH);
                Node n = or(); depth--;
                if (!is(")")) throw err("missing ')'"); p++;
                return n;
            }
            if (tok.startsWith("\"")) return node(new Lit(tok.substring(1, tok.length() - 1)));
            if (tok.chars().allMatch(Character::isDigit)) return node(new Lit(Long.parseLong(tok)));
            if (tok.equals("true") || tok.equals("false")) return node(new Lit(Boolean.parseBoolean(tok)));
            if (Character.isLetter(tok.charAt(0))) return node(new Field(tok));
            throw err("unexpected '" + tok + "'", p - 1);
        }
        private boolean is(String w) { return p < t.size() && t.get(p).equals(w); }
        private IllegalArgumentException err(String m) { return err(m, p); }
        private IllegalArgumentException err(String m, int i) { return new IllegalArgumentException(m + (i < at.size() ? " (at char " + at.get(i) + ")" : "")); }
    }

    static Class<?> typeOf(Node n, Map<String, Class<?>> schema) {                                    // (2)!
        return switch (n) {
            case Lit l -> l.value().getClass();
            case Field f -> { Class<?> c = schema.get(f.name()); if (c == null) throw new IllegalArgumentException("unknown field '" + f.name() + "', known: " + new java.util.TreeSet<>(schema.keySet())); yield c; }
            case Cmp c -> {
                Class<?> a = typeOf(c.l(), schema), b = typeOf(c.r(), schema);
                if (a != b) throw new IllegalArgumentException("can't compare " + a.getSimpleName() + " with " + b.getSimpleName() + " in '" + c.op() + "'");
                if (a != Long.class && !List.of("==", "!=").contains(c.op())) throw new IllegalArgumentException("'" + c.op() + "' needs numbers");
                yield Boolean.class;
            }
            case And a -> { requireBool(a.l(), schema); requireBool(a.r(), schema); yield Boolean.class; }
            case Or o -> { requireBool(o.l(), schema); requireBool(o.r(), schema); yield Boolean.class; }
            case Not x -> { requireBool(x.e(), schema); yield Boolean.class; }
        };
    }
    static void requireBool(Node n, Map<String, Class<?>> s) { if (typeOf(n, s) != Boolean.class) throw new IllegalArgumentException("expected a yes/no condition, got " + show(n)); }

    static Object eval(Node n, Map<String, Object> facts, List<String> trace) {                      // (3)!
        return switch (n) {
            case Lit l -> l.value();
            case Field f -> facts.get(f.name());
            case Cmp c -> {
                Object a = eval(c.l(), facts, trace), b = eval(c.r(), facts, trace);
                boolean r = switch (c.op()) {
                    case "==" -> a.equals(b); case "!=" -> !a.equals(b);
                    case ">=" -> (Long) a >= (Long) b; case "<=" -> (Long) a <= (Long) b;
                    case ">" -> (Long) a > (Long) b; default -> (Long) a < (Long) b;
                };
                trace.add(show(c) + " -> " + r + " (" + a + ")");
                yield r;
            }
            case And a -> (Boolean) eval(a.l(), facts, trace) && (Boolean) eval(a.r(), facts, trace);
            case Or o -> (Boolean) eval(o.l(), facts, trace) || (Boolean) eval(o.r(), facts, trace);
            case Not x -> !(Boolean) eval(x.e(), facts, trace);
        };
    }

    static String show(Node n) {
        return switch (n) {
            case Lit l -> l.value() instanceof String s ? "\"" + s + "\"" : String.valueOf(l.value());
            case Field f -> f.name();
            case Cmp c -> show(c.l()) + " " + c.op() + " " + show(c.r());
            case And a -> "(" + show(a.l()) + " and " + show(a.r()) + ")";
            case Or o -> "(" + show(o.l()) + " or " + show(o.r()) + ")";
            case Not x -> "not " + show(x.e());
        };
    }

    static final class RuleEngine {
        private final Map<String, Class<?>> schema; private final Map<String, Node> compiled = new ConcurrentHashMap<>();
        int compilations;
        RuleEngine(Map<String, Class<?>> schema) { this.schema = schema; }

        Node compile(String src) {                                                                     // (4)!
            return compiled.computeIfAbsent(src, s -> {
                compilations++;
                Node n = new Parser(s).parse();
                if (typeOf(n, schema) != Boolean.class) throw new IllegalArgumentException("rule must be a yes/no condition");
                return n;
            });
        }
        boolean matches(String src, Map<String, Object> facts, List<String> trace) {
            Map<String, Object> missing = new java.util.TreeMap<>(); schema.keySet().forEach(k -> { if (!facts.containsKey(k)) missing.put(k, null); });
            if (!missing.isEmpty()) throw new IllegalArgumentException("facts missing " + missing.keySet());   // (5)!
            return (Boolean) eval(compile(src), facts, trace);
        }
    }

    public static void main(String[] args) {
        RuleEngine engine = new RuleEngine(Map.of("cart_total", Long.class, "category", String.class, "tier", String.class, "first_order", Boolean.class));
        String rule = "(category == \"fruit\" and cart_total >= 499) or (tier == \"gold\" and not first_order == true)";
        Map<String, Object> cart = Map.of("cart_total", 320L, "category", "dairy", "tier", "gold", "first_order", false);
        List<String> trace = new ArrayList<>();
        System.out.println("eligible: " + engine.matches(rule, cart, trace));
        trace.forEach(s -> System.out.println("  " + s));
        for (int i = 0; i < 1000; i++) engine.matches(rule, cart, new ArrayList<>());
        System.out.println("1,001 evaluations, compilations: " + engine.compilations);

        for (String bad : List.of(
                "cart_total >= \"499\"",
                "cartTotal > 100",
                "tier > \"gold\"",
                "cart_total",
                "(".repeat(13) + "tier == \"gold\"" + ")".repeat(13),
                "tier == \"gold\" or ".repeat(20) + "first_order == true",
                "tier == \"" + "a".repeat(600) + "\"")) {
            try { engine.compile(bad); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
