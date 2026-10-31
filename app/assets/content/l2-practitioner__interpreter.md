# Interpreter

L2 🔵 Practitioner · ⏱ 40 min read · Prerequisites: [Composite](composite.md), [Visitor](visitor.md)

> 📌 **TL;DR**  
> Interpreter turns a small language (promo rules, fee formulas, filters) into a tree of objects and evaluates that tree. Each grammar rule becomes a node type; evaluation is a recursive walk. In Java 21 the nodes are sealed records and evaluation is an exhaustive `switch`. Parse once, evaluate many times, and when the input comes from people (not code), add type checking, size limits and clear error messages. When the grammar grows beyond a page, use a parser generator such as ANTLR or an existing expression language instead of hand-writing one.

## 1. What It Is

**Plain English.** FreshCart's merchandisers want to write rules like `category == "fruit" and cart_total >= 499 or tier == "gold"` without asking a developer to ship a release. Interpreter gives that text a structure: a parser builds a tree (`Or(And(Cmp, Cmp), Cmp)`), and an evaluator walks the tree for each cart and answers yes or no.

**Definition (GoF).** "Given a language, define a representation for its grammar along with an interpreter that uses the representation to interpret sentences in the language." Parts:

- **Abstract expression:** the node type (`Rule`, `Expr`) with an `interpret(context)` operation or a `switch` over it.
- **Terminal expressions:** leaves such as numbers, field names, `TotalAbove(499)`.
- **Non-terminal expressions:** nodes made of other nodes, such as `And`, `Or`, `Add`.
- **Context:** the facts the sentence is evaluated against (the cart, the variables).
- **Client / parser:** builds the tree from text. GoF leaves parsing out of the pattern, but in practice you always need one.

## 2. Why It Exists

Some behaviour changes faster than you can deploy: promo eligibility, surge pricing, alert conditions, search filters. Hard-coding each variant means a release per business change; storing them as free text and using `String.split` breaks on the first pair of parentheses. Interpreter gives these rules a real grammar, a typed in-memory form you can validate, print, optimise and evaluate, and a single place to enforce safety.

## 3. Mental Model / Analogy

**Analogy.** A recipe is a sentence in a small language: "boil 2 cups of water, then add rice". A cook (the interpreter) reads each step and acts on the kitchen (the context). Rewriting the recipe doesn't require hiring a new cook.

**Technical model.** Text → tokens → abstract syntax tree (AST) → evaluation. The AST is a [Composite](composite.md): leaves and nodes share one type, and evaluation recurses. Extra operations on the same AST (printing, type checking, simplifying) are [Visitor](visitor.md)s, which in Java 21 are just more `switch` functions.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Grammar to class hierarchy

**Description.** Write the grammar first, then map each rule to a type. Terminals (`cart_total > 499`, `category = fruit`) become leaf classes; combinations (`rule AND rule`) become classes holding child rules. Each class implements `interpret(context)` by doing its own small piece and delegating to its children. This is the GoF form.

**Advantages / features.** The grammar is visible in the code; adding a construct is adding a class; each class is tiny and easy to test.

**GrammarToClassesDemo.java**

```java
import java.util.Set;

/**
 * Classic GoF Interpreter: each grammar rule becomes a class with interpret(context).
 *   rule     := condition | rule "AND" rule | rule "OR" rule
 *   condition:= "cart_total >" number | "category =" word
 */
public class GrammarToClassesDemo {

    record Cart(long totalRupees, Set<String> categories) { }                                     // the context

    interface Rule { boolean interpret(Cart c); }

    record TotalAbove(long rupees) implements Rule {                                               // terminal expressions
        public boolean interpret(Cart c) { return c.totalRupees() > rupees; }
    }
    record HasCategory(String category) implements Rule {
        public boolean interpret(Cart c) { return c.categories().contains(category); }
    }
    record And(Rule left, Rule right) implements Rule {
        public boolean interpret(Cart c) { return left.interpret(c) && right.interpret(c); }
    }
    record Or(Rule left, Rule right) implements Rule {
        public boolean interpret(Cart c) { return left.interpret(c) || right.interpret(c); }
    }

    public static void main(String[] args) {
        // "cart_total > 499 AND category = fruit OR category = dairy", built by hand (no parser yet)
        Rule freeDelivery = new Or(new And(new TotalAbove(499), new HasCategory("fruit")), new HasCategory("dairy"));
        System.out.println("Rs 600 of fruit: " + freeDelivery.interpret(new Cart(600, Set.of("fruit"))));
        System.out.println("Rs 300 of fruit: " + freeDelivery.interpret(new Cart(300, Set.of("fruit"))));
        System.out.println("Rs 100 of dairy: " + freeDelivery.interpret(new Cart(100, Set.of("dairy"))));
    }
}
```

1. The abstract expression: every node knows how to interpret itself against the context (the cart).
2. A non-terminal expression: it delegates to its children and combines the results.
3. The "sentence" is built by hand here. Real systems get it from a parser, shown in the next subtopic.

**Run it:** `java GrammarToClassesDemo.java`

```text
Rs 600 of fruit: true
Rs 300 of fruit: false
Rs 100 of dairy: true
```

| Grammar element | Becomes | Example |
|-----------------|---------|---------|
| Terminal | Leaf record | `TotalAbove(499)` |
| Sequence / combination | Record with child fields | `And(left, right)` |
| Alternative (`a \| b`) | Separate implementations of the interface | `Or`, `And`, `HasCategory` |
| Repetition (`a*`) | `List<Child>` field | `Sum(List<Expr>)` |

**Use** this mapping for any small, stable language. **Avoid** it when the language has dozens of constructs; the class count explodes.
**Real-world Java:** `java.util.regex.Pattern` compiles a regex into a tree of internal `Node` objects that match themselves; Spring's `SpelNode` subclasses (`OpAnd`, `OpOr`, `PropertyOrFieldReference`).
**Best practice:** write the grammar as a comment at the top of the file, and keep it in sync. **Gotcha:** `interpret` methods spread one operation across many classes, so adding a second operation (printing) touches every class. That's why the modern form below keeps operations in functions.

### Abstract syntax trees

**Description.** The AST is the tree form of a sentence after parsing: it drops punctuation and whitespace and keeps only structure. Getting there takes two steps. A **tokenizer** (lexer) turns characters into tokens (`25`, `+`, `km`). A **parser** turns tokens into a tree, applying precedence (`*` before `+`) and parentheses. The simplest hand-written parser is **recursive descent**: one method per grammar level, each calling the next tighter level.

**Advantages / features.** Precedence is encoded by which method calls which; errors can report the exact token; the resulting tree is independent of the text, so you can print it, store it or evaluate it many times.

**AstDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** From text to an abstract syntax tree: a tokenizer and a recursive-descent parser for delivery-fee formulas. */
public class AstDemo {

    sealed interface Node permits Num, Var, Bin { }
    record Num(long value) implements Node { public String toString() { return String.valueOf(value); } }
    record Var(String name) implements Node { public String toString() { return name; } }
    record Bin(char op, Node left, Node right) implements Node { public String toString() { return "(" + op + " " + left + " " + right + ")"; } }

    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; }
            else if (Character.isDigit(c)) { int j = i; while (j < s.length() && Character.isDigit(s.charAt(j))) j++; out.add(s.substring(i, j)); i = j; }
            else if (Character.isLetter(c)) { int j = i; while (j < s.length() && Character.isLetterOrDigit(s.charAt(j))) j++; out.add(s.substring(i, j)); i = j; }
            else if ("+-*/()".indexOf(c) >= 0) { out.add(String.valueOf(c)); i++; }
            else throw new IllegalArgumentException("unexpected '" + c + "' at " + i);
        }
        return out;
    }

    static final class Parser {
        private final List<String> t; private int pos;
        Parser(List<String> tokens) { t = tokens; }
        String peek() { return pos < t.size() ? t.get(pos) : ""; }
        Node expr() {
            Node n = term();
            while (peek().equals("+") || peek().equals("-")) { char op = t.get(pos++).charAt(0); n = new Bin(op, n, term()); }
            return n;
        }
        Node term() {
            Node n = factor();
            while (peek().equals("*") || peek().equals("/")) { char op = t.get(pos++).charAt(0); n = new Bin(op, n, factor()); }
            return n;
        }
        Node factor() {
            String tok = t.get(pos++);
            if (tok.equals("(")) { Node n = expr(); if (!t.get(pos++).equals(")")) throw new IllegalArgumentException("expected )"); return n; }
            return Character.isDigit(tok.charAt(0)) ? new Num(Long.parseLong(tok)) : new Var(tok);
        }
    }

    static long eval(Node n, Map<String, Long> vars) {
        return switch (n) {
            case Num x -> x.value();
            case Var v -> vars.get(v.name());
            case Bin b -> switch (b.op()) {
                case '+' -> eval(b.left(), vars) + eval(b.right(), vars);
                case '-' -> eval(b.left(), vars) - eval(b.right(), vars);
                case '*' -> eval(b.left(), vars) * eval(b.right(), vars);
                default -> eval(b.left(), vars) / eval(b.right(), vars);
            };
        };
    }

    public static void main(String[] args) {
        String formula = "25 + 8 * (km - 2)";
        System.out.println("tokens: " + tokenize(formula));
        Node ast = new Parser(tokenize(formula)).expr();
        System.out.println("ast:    " + ast + "   (* binds tighter than +)");
        System.out.println("fee for 5 km: Rs " + eval(ast, Map.of("km", 5L)));
    }
}
```

1. The tokenizer: digits become numbers, letters become names, and anything unexpected is rejected with its position.
2. `expr` handles the loosest operators (`+`, `-`) and calls `term` for each side, so the tighter level is built first.
3. `term` handles `*` and `/`. Because `expr` calls `term`, multiplication ends up deeper in the tree and is evaluated first.
4. Evaluation is a recursive `switch` over node types.

**Run it:** `java AstDemo.java`

```text
tokens: [25, +, 8, *, (, km, -, 2, )]
ast:    (+ 25 (* 8 (- km 2)))   (* binds tighter than +)
fee for 5 km: Rs 49
```

| Stage | Input | Output | Typical errors |
|-------|-------|--------|----------------|
| Lexing | Characters | Tokens with positions | Unknown character, unclosed quote |
| Parsing | Tokens | AST | Missing `)`, unexpected token |
| Checking | AST | Typed AST | Unknown field, wrong type |
| Evaluation | AST + context | Value | Missing fact, division by zero |

**Use** recursive descent for grammars of up to roughly ten rules. **Avoid** left-recursive rules (`expr := expr '+' term`) in recursive descent; they recurse forever. Use loops instead, as `expr` does.
**Real-world Java:** `javac` (a hand-written recursive-descent parser, `JavacParser`), Spring's `InternalSpelExpressionParser`, Jackson's JSON parser.
**Best practice:** keep token positions so every error says where. **Gotcha:** `8 - 2 - 1` must be `(8 - 2) - 1`; building the tree with a loop (left fold) gives left associativity, while naive recursion (`term '-' expr`) gives the wrong answer.

### Interpreter with records and switch

**Description.** Java 21 lets you define the AST as a `sealed interface` with `record` nodes and write each operation (evaluate, print, simplify, type-check) as a function with an exhaustive `switch` and record patterns. The nodes become plain data; operations live in one place each.

**Advantages / features.** Adding an operation is one new function; the compiler rejects a `switch` that misses a node type; records give `equals`, `hashCode` and `toString` for free, which makes tests and caching easy.

**Before (interpret methods inside each class) and after (data + functions):** compare `GrammarToClassesDemo` above with this:

**RecordsSwitchInterpreterDemo.java**

```java
import java.util.Map;

/** Modern interpreter: the AST is sealed records; evaluate, print and simplify are separate switch functions. */
public class RecordsSwitchInterpreterDemo {

    sealed interface Expr permits Num, Var, Add, Mul { }
    record Num(long v) implements Expr { }
    record Var(String name) implements Expr { }
    record Add(Expr l, Expr r) implements Expr { }
    record Mul(Expr l, Expr r) implements Expr { }

    static long eval(Expr e, Map<String, Long> env) {
        return switch (e) {
            case Num n -> n.v();
            case Var v -> { Long x = env.get(v.name()); if (x == null) throw new IllegalArgumentException("unknown variable " + v.name()); yield x; }
            case Add(Expr l, Expr r) -> eval(l, env) + eval(r, env);
            case Mul(Expr l, Expr r) -> eval(l, env) * eval(r, env);
        };
    }

    static String show(Expr e) {
        return switch (e) {
            case Num n -> String.valueOf(n.v());
            case Var v -> v.name();
            case Add(Expr l, Expr r) -> "(" + show(l) + " + " + show(r) + ")";
            case Mul(Expr l, Expr r) -> show(l) + " * " + show(r);
        };
    }

    static Expr simplify(Expr e) {
        return switch (e) {
            case Add(Expr l, Expr r) -> {
                Expr a = simplify(l), b = simplify(r);
                if (a instanceof Num(long x) && b instanceof Num(long y)) yield new Num(x + y);
                if (b instanceof Num(long y) && y == 0) yield a;
                yield new Add(a, b);
            }
            case Mul(Expr l, Expr r) -> {
                Expr a = simplify(l), b = simplify(r);
                if (a instanceof Num(long x) && b instanceof Num(long y)) yield new Num(x * y);
                if (b instanceof Num(long y) && y == 1) yield a;
                yield new Mul(a, b);
            }
            case Num n -> n;
            case Var v -> v;
        };
    }

    public static void main(String[] args) {
        Expr fee = new Add(new Mul(new Var("km"), new Mul(new Num(8), new Num(1))), new Add(new Num(20), new Num(5)));
        System.out.println("original:   " + show(fee));
        System.out.println("simplified: " + show(simplify(fee)));
        System.out.println("for 4 km:   Rs " + eval(simplify(fee), Map.of("km", 4L)));
    }
}
```

1. The whole grammar fits in five lines; the compiler knows every node type.
2. A second operation, constant folding, added without touching the node types.
3. Record patterns deconstruct and test in one line: "the right side is the number 1".

**Run it:** `java RecordsSwitchInterpreterDemo.java`

```text
original:   (km * 8 * 1 + (20 + 5))
simplified: (km * 8 + 25)
for 4 km:   Rs 57
```

| | GoF `interpret` methods | Sealed records + `switch` |
|-|-------------------------|---------------------------|
| Add a node type | One class; existing code untouched | Compiler flags every `switch` |
| Add an operation | Edit every class | One new function |
| Completeness check | Abstract method | Exhaustive `switch` |
| Equality, printing | Hand-written | Record defaults |
| Java version | Any | 21+ |

**Use** records and `switch` for new interpreters in Java 21. **Avoid** a `default` branch in these switches; it silently accepts new node types.
**Real-world Java:** `java.lang.classfile` (JDK 24) models instructions as sealed types; the Java compiler's own pattern-matching design was motivated by AST work like this.
**Best practice:** make AST nodes immutable so a compiled rule can be shared by all threads. **Gotcha:** very deep trees overflow the stack during recursive evaluation; limit depth at parse time.

### When to use a parser generator instead

**Description.** Splitting strings and regular expressions can't handle nesting. Hand-written recursive descent is fine for small grammars. When the language needs functions, dates, lists, string escapes, good error recovery or will grow over years, generate the parser from a grammar file with **ANTLR** (or JavaCC), or use an existing expression language (Spring SpEL, MVEL, CEL, JEXL) rather than inventing one.

**Advantages / features.** Parser generators handle precedence and ambiguity declaratively, produce syntax-error recovery, and keep the grammar as documentation. Existing languages bring tooling and years of bug fixes.

**ParserGeneratorDemo.java**

```java
import java.util.Arrays;
import java.util.List;

/** Why ad-hoc string tricks fail for real languages: splitting on AND/OR ignores parentheses and precedence. */
public class ParserGeneratorDemo {

    static boolean evalNaive(String rule, List<String> tags) {
        String[] ors = rule.split(" OR ");
        return Arrays.stream(ors).anyMatch(part -> Arrays.stream(part.split(" AND "))
                .allMatch(atom -> tags.contains(atom.replace("(", "").replace(")", "").trim())));
    }

    /** Tiny recursive-descent evaluator that respects parentheses. */
    static final class Rd {
        private final String[] t; private int p; private final List<String> tags;
        Rd(String s, List<String> tags) { t = s.replace("(", " ( ").replace(")", " ) ").trim().split("\\s+"); this.tags = tags; }
        boolean or() { boolean v = and(); while (p < t.length && t[p].equals("OR")) { p++; v = and() | v; } return v; }
        boolean and() { boolean v = atom(); while (p < t.length && t[p].equals("AND")) { p++; v = atom() & v; } return v; }
        boolean atom() { String tok = t[p++]; if (tok.equals("(")) { boolean v = or(); p++; return v; } return tags.contains(tok); }
    }

    public static void main(String[] args) {
        String rule = "gold AND (fruit OR dairy)";
        List<String> silverBuyingDairy = List.of("silver", "dairy");
        System.out.println("rule: " + rule + ", customer: " + silverBuyingDairy);
        System.out.println("naive split:      " + evalNaive(rule, silverBuyingDairy) + "   <- wrong: parentheses ignored");
        System.out.println("recursive descent: " + new Rd(rule, silverBuyingDairy).or());
        System.out.println("grammar growing (functions, dates, lists, error recovery)? use a parser generator such as ANTLR");
    }
}
```

1. The tempting shortcut: split on `OR`, then on `AND`. It ignores parentheses entirely.
2. A real parser follows the grammar, so `(fruit OR dairy)` is evaluated as a unit.
3. Past a certain size, stop hand-writing.

**Run it:** `java ParserGeneratorDemo.java`

```text
rule: gold AND (fruit OR dairy), customer: [silver, dairy]
naive split:      true   <- wrong: parentheses ignored
recursive descent: false
grammar growing (functions, dates, lists, error recovery)? use a parser generator such as ANTLR
```

| Option | Good for | Cost |
|--------|----------|------|
| String split / regex | Flat lists (`a,b,c`) | Breaks on nesting |
| Hand-written recursive descent | ≤ ~10 grammar rules, best error messages | You maintain it |
| ANTLR / JavaCC | Large or evolving grammars | Build step, generated code, learning curve |
| Existing language (SpEL, CEL, JEXL) | Standard expressions | Security review; may be too powerful |
| No language: a form/JSON rule builder | Non-technical users | Less flexible |

**Use** a parser generator when the grammar exceeds a page or needs error recovery. **Avoid** exposing general-purpose expression languages (SpEL, OGNL) to untrusted input; they can call arbitrary methods.
**Real-world Java:** Hibernate 6 parses HQL with ANTLR; Elasticsearch's Painless is ANTLR-based; Google's CEL is designed for safe, non-Turing-complete policy rules.
**Best practice:** before writing a language, ask whether a structured JSON rule (field, operator, value) edited in a UI is enough. **Gotcha:** a "simple" DSL grows; budget for tooling (syntax highlighting, validation in the editor) from the start.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop's delivery fee is Rs 20 plus Rs 6 per km, or Rs 10 per km when it's raining. Operations want to change this formula without a release.

**SurgeFeeDemo.java**

```java
import java.util.Map;

/** Minimal Interpreter: a surge-fee rule as a tiny expression tree, evaluated against the current conditions. */
public class SurgeFeeDemo {

    sealed interface Fee permits Fixed, PerKm, Plus, IfRaining { }
    record Fixed(long rupees) implements Fee { }
    record PerKm(long rupees) implements Fee { }
    record Plus(Fee a, Fee b) implements Fee { }
    record IfRaining(Fee then, Fee otherwise) implements Fee { }

    static long fee(Fee f, double km, boolean raining) {
        return switch (f) {
            case Fixed x -> x.rupees();
            case PerKm p -> Math.round(p.rupees() * km);
            case Plus(Fee a, Fee b) -> fee(a, km, raining) + fee(b, km, raining);
            case IfRaining(Fee t, Fee o) -> raining ? fee(t, km, raining) : fee(o, km, raining);
        };
    }

    public static void main(String[] args) {
        Fee rule = new Plus(new Fixed(20), new IfRaining(new PerKm(10), new PerKm(6)));
        for (Map.Entry<Double, Boolean> c : new java.util.TreeMap<>(Map.of(3.0, false, 5.0, true)).entrySet())
            System.out.println(c.getKey() + " km, raining=" + c.getValue() + " -> Rs " + fee(rule, c.getKey(), c.getValue()));
    }
}
```

1. The fee language: four node types, sealed so the evaluator must handle each.
2. The interpreter: one recursive `switch`, with the conditions (`km`, `raining`) as the context.

**Run it:** `java SurgeFeeDemo.java`

**Output:**

```text
3.0 km, raining=false -> Rs 38
5.0 km, raining=true -> Rs 70
```

**What to notice:**

- The rule is data; changing it doesn't change `fee`.
- `IfRaining` is a non-terminal that chooses which child to evaluate.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart merchandisers type promo eligibility rules into an admin screen. The rule is parsed once into an AST, then evaluated for every cart. Typos must be rejected with a position, not saved.

**PromoRuleDslDemo.java**

```java
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

    static final class Parser {
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
        IllegalArgumentException err(String m, int idx) { return new IllegalArgumentException(m + (idx < t.size() ? " at position " + t.get(idx).pos() : "")); }
    }

    static boolean eval(Rule r, Map<String, Object> cart) {
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
```

1. The recursive-descent parser: `or` calls `and`, so `and` binds tighter, as merchandisers expect.
2. Every error carries the token's position, so the admin screen can underline it.
3. Evaluation walks the AST against the cart's facts.

**Run it:** `java PromoRuleDslDemo.java`

**Output:**

```text
parsed as: ((category == "fruit" and cart_total >= 499) or tier == "gold")
{cart_total=650, category=fruit, tier=silver} -> true
{cart_total=300, category=fruit, tier=silver} -> false
{cart_total=120, category=dairy, tier=gold} -> true
rejected 'cart_total >= ': expected value at end of rule
rejected 'tier = "gold"': unknown operator '=' at position 5
rejected 'cart_total >= 499 and': expected field at end of rule
```

**What to notice:**

- The pretty-printed form shows the grouping the parser chose; showing it back to the author catches precedence surprises.
- `tier = "gold"` (one `=`) is rejected at position 5 instead of being saved and never matching.

### Example 3: Production-grade

**Scenario:** the rule engine is shared by promotions, fraud flags and free-delivery offers. Rules come from people, so they are untrusted: a rule must not exhaust the server, compare text with numbers, or reference fields that don't exist. Support asks "why did this customer get the offer?" so evaluation must explain itself. Thousands of carts per second evaluate the same few rules.

**SafeRuleEngineDemo.java**

```java
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
            if (src.length() > MAX_LENGTH) throw new IllegalArgumentException("rule longer than " + MAX_LENGTH + " chars");
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

    static Class<?> typeOf(Node n, Map<String, Class<?>> schema) {
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

    static Object eval(Node n, Map<String, Object> facts, List<String> trace) {
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

        Node compile(String src) {
            return compiled.computeIfAbsent(src, s -> {
                compilations++;
                Node n = new Parser(s).parse();
                if (typeOf(n, schema) != Boolean.class) throw new IllegalArgumentException("rule must be a yes/no condition");
                return n;
            });
        }
        boolean matches(String src, Map<String, Object> facts, List<String> trace) {
            Map<String, Object> missing = new java.util.TreeMap<>(); schema.keySet().forEach(k -> { if (!facts.containsKey(k)) missing.put(k, null); });
            if (!missing.isEmpty()) throw new IllegalArgumentException("facts missing " + missing.keySet());
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
```

1. Limits are checked while parsing (length here, nesting depth and node count further down), so a hostile rule fails fast.
2. A type checker runs over the whole AST before any evaluation: unknown fields, mixed types, ordering operators on text and non-boolean rules are all rejected at save time.
3. Evaluation records each comparison with the actual value, giving support an explanation.
4. Rules are parsed and checked once and cached by their text; ASTs are immutable records, so threads share them safely.
5. Missing facts fail loudly instead of evaluating to `false` and quietly denying an offer.

**Run it:** `java SafeRuleEngineDemo.java`

**Output:**

```text
eligible: true
  category == "fruit" -> false (dairy)
  tier == "gold" -> true (gold)
  first_order == true -> false (false)
1,001 evaluations, compilations: 1
rejected: can't compare Long with String in '>='
rejected: unknown field 'cartTotal', known: [cart_total, category, first_order, tier]
rejected: '>' needs numbers
rejected: rule must be a yes/no condition
rejected: parentheses nested deeper than 12 (at char 13)
rejected: rule has more than 60 parts (at char 285)
rejected: rule longer than 500 chars
```

**What to notice:**

- The trace shows short-circuiting: `cart_total >= 499` never ran because `category == "fruit"` was already false.
- 1,001 evaluations compiled the rule once.
- Each rejection names the problem in words a merchandiser can act on.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Flexibility | Business rules change without a release | A new language to document, test and support |
| Structure | AST can be validated, printed, optimised, stored | Parser and checker are real code to maintain |
| Extensibility | New operations are new functions | New syntax touches lexer, parser, checker, evaluator |
| Performance | Parse once, evaluate fast | Tree walking is slower than compiled code |
| Safety | One place to enforce limits and types | Untrusted input is an attack surface |
| Debuggability | Traces explain decisions | Users may write rules nobody else understands |

## 8. When to Use / When NOT to Use

**Use it when:**

- a small, stable language expresses the problem well (filters, rules, formulas, queries);
- the rules change more often than you deploy, and non-developers write them;
- you need to analyse rules, not just run them (validate, explain, find conflicts).

**Don't use it when:**

- a configuration table or a JSON rule (field, operator, value) is enough;
- an existing language (SQL, SpEL, CEL, JSONPath) already fits;
- the grammar is large or evolving fast: use a parser generator;
- performance is critical and rules are fixed: write the code.

## 9. Best Practices

1. **Parse once, evaluate many times;** cache compiled ASTs. *(Consensus.)*
2. **Sealed records for nodes, one `switch` function per operation, no `default`.** *(Consensus for Java 21.)*
3. **Validate at save time** (syntax, types, fields), not at evaluation time. *(Consensus.)*
4. **Limit length, depth and node count** for any user-written rule. *(Consensus.)*
5. **Report errors with positions** and echo the parsed form back to the author. *(Consensus.)*
6. **Keep the language non-Turing-complete:** no loops, no method calls, no I/O. *(Consensus for user rules.)*
7. **Prefer an existing language or a parser generator** once the grammar exceeds about a page. *(Opinion, widely shared.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Missing facts evaluate to false (production failure mode)**  
> **Symptom:** after a checkout refactor, no customer got the "gold tier free delivery" offer for three days; nothing errored.
> **Root cause:** the refactor renamed the fact `tier` to `loyalty_tier`. The interpreter returned `null` for unknown fields, and `null == "gold"` evaluated to `false`.
> **Fix:** type-check rules against a declared schema at save time, fail evaluation when a schema field is missing from the facts, and alert on rules that never match.

> 🚨 **Expression injection**  
> **Symptom:** a "rule" field accepted `T(java.lang.Runtime).getRuntime().exec(...)`.
> **Root cause:** user text was passed straight to a general-purpose expression language (SpEL with `StandardEvaluationContext`).
> **Fix:** use your own small grammar, or `SimpleEvaluationContext` / CEL, which can't call arbitrary methods.

> 🚨 **Precedence surprises**  
> **Symptom:** `a or b and c` was read by authors as `(a or b) and c`.
> **Root cause:** standard precedence (and before or) differs from how people read left to right.
> **Fix:** show the parenthesised form back to the author before saving, and consider requiring parentheses when `and` and `or` are mixed.

> 🚨 **String hacks instead of a parser**  
> **Symptom:** rules with parentheses or quoted values containing `and` behave randomly.
> **Root cause:** `split(" AND ")` or regexes can't parse nested structures.
> **Fix:** a real tokenizer and parser, however small.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Max rule length | 500 chars (example) | Rejects huge inputs before parsing | Rules legitimately longer | Too high allows resource abuse |
| Max nesting depth | 12 | Prevents stack overflow in parsing and evaluation | Deeply nested generated rules | Too high risks `StackOverflowError` |
| Max node count | 60 | Bounds evaluation cost per rule | Large but legitimate rules | Too high slows checkout |
| Compiled-rule cache size | Unbounded map (example) | Avoids re-parsing | Many distinct rules: use a bounded cache (Caffeine) | Unbounded growth if rule text is per-user |
| Evaluation timeout | none | Stops runaway rules | Languages with loops or functions | Partial results |

## 12. Security Considerations

- Treat rule text as **untrusted input**: limit size, depth and node count before evaluating.
- **Never** feed user text to SpEL, OGNL, MVEL, JavaScript engines or `ScriptEngine` with full access; that's remote code execution (several Struts and Spring CVEs).
- Keep the language **free of side effects**: no method calls, no I/O, no loops.
- **Whitelist fields** through a schema; don't resolve arbitrary property paths by reflection.
- Escape and quote properly when pretty-printing rules into logs or UIs.
- Store who changed each rule and when; rules are code that bypasses code review.

## 13. Performance & Scalability

A tree-walking interpreter costs a few nanoseconds per node, which is negligible for rules with tens of nodes. The expensive part is parsing, so cache compiled ASTs. For very hot paths, compile the AST into a tree of lambdas (`Predicate<Facts>`) once; the JIT then inlines them. Beyond that, generate bytecode (`java.lang.classfile`, ASM), as SpEL's compiler mode does. Evaluate cheap and selective comparisons first in `and` chains to benefit from short-circuiting. Immutable ASTs scale across threads with no locking.

## 14. Testing It

Test precedence, every type error, the limits and the cache:

**SafeRuleEngineTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeRuleEngineTest {

    private final SafeRuleEngineDemo.RuleEngine engine =
            new SafeRuleEngineDemo.RuleEngine(Map.of("total", Long.class, "tier", String.class));
    private final Map<String, Object> gold500 = Map.of("total", 500L, "tier", "gold");

    private boolean matches(String rule) { return engine.matches(rule, gold500, new ArrayList<>()); }

    @Test
    void andBindsTighterThanOr() {
        assertTrue(matches("tier == \"silver\" and total > 1000 or total == 500"));
        assertFalse(matches("tier == \"silver\" and (total > 1000 or total == 500)"));
    }

    @Test
    void notAndComparisons() {
        assertTrue(matches("not total < 100"));
        assertTrue(matches("total >= 500 and total <= 500"));
    }

    @Test
    void typeErrorsAreCaughtBeforeEvaluation() {
        assertThrows(IllegalArgumentException.class, () -> engine.compile("total == \"500\""));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("tier > \"a\""));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("unknown == 1"));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("total"));
    }

    @Test
    void limitsProtectAgainstHugeOrDeepRules() {
        assertThrows(IllegalArgumentException.class, () -> engine.compile("(".repeat(20) + "total == 1" + ")".repeat(20)));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("total == 1 or ".repeat(100) + "total == 1"));
    }

    @Test
    void compiledRulesAreCached() {
        matches("total > 1");
        matches("total > 1");
        assertEquals(1, engine.compilations);
    }
}
```

Also worth adding: a round-trip property test (parse → print → parse gives the same AST), and a fuzz test that random strings never throw anything but `IllegalArgumentException`.

## 15. Observability & Operations

Log rule saves with author, old and new text. Count evaluations, matches and errors per rule; alert on rules that never match after a release (the failure mode above) or suddenly match everything. Keep the evaluation trace for sampled decisions so support can answer "why did this customer get this offer?". Measure parse time and cache hit rate.

## 16. Version Notes

| Java | Change relevant to Interpreter |
|------|--------------------------------|
| 1.4 | `java.util.regex`: a compiled pattern is an interpreter tree |
| 6 | `javax.script` (`ScriptEngine`) for embedding languages |
| 8 | Lambdas: compile ASTs into `Predicate` / `Function` trees |
| 15 | Nashorn JavaScript engine removed |
| 16 | Records for AST nodes; pattern matching for `instanceof` |
| 17 | Sealed classes for closed node hierarchies |
| 21 | Pattern matching for `switch` and record patterns (final) |
| 24 | `java.lang.classfile` API for generating bytecode from ASTs |

## 17. Expert Deep Dive

- **Interpreter = Composite + operations.** The AST is a Composite; evaluation, printing, type checking and optimisation are [Visitor](visitor.md)s. In Java 21 all of them are `switch` functions over sealed records.
- **Closure compilation.** Turning `And(a, b)` into `facts -> ca.test(facts) && cb.test(facts)` once removes the per-node `switch` dispatch; Truffle/GraalVM takes this further with self-specialising AST interpreters that the JIT compiles to machine code.
- **Pratt parsing.** For expression languages with many precedence levels, a Pratt (top-down operator precedence) parser replaces one method per level with a table of binding powers.
- **Static analysis of rules.** Because rules are trees, you can detect contradictions (`total > 500 and total < 100`), overlap between promotions, or rules that can never match, before they go live.
- **Turing completeness is a liability.** Policy languages like CEL deliberately avoid loops so evaluation time is bounded by rule size.

> 📖 **War story: the rule that never matched**  
> DairyDay ran milk-subscription offers from rules stored as text and evaluated by a hand-written interpreter that returned `false` for any field it didn't recognise. A data team renamed `subscription_days` to `sub_days` in the checkout facts. Every rule mentioning the old name stopped matching, and the "30 days free delivery" offer silently disappeared for 40,000 subscribers over a long weekend. No error was logged because, as far as the interpreter knew, nothing had failed. **Fixes:** a declared schema of facts; type checking on save and on deploy (all stored rules are re-checked when the schema changes); evaluation fails if a declared fact is missing; and a dashboard of match rates per rule with an alert on a drop to zero. **Lesson:** an interpreter that "never throws" hides bugs; make unknowns loud.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What does Interpreter do?** Represents sentences of a **small language as a tree** and **evaluates** it.
2. **What are terminal and non-terminal expressions?** **Leaves** (numbers, fields) and **nodes made of other nodes** (and, or, plus).
3. **What is the context?** The **facts** the expression is evaluated against.
4. **Which pattern does the AST resemble?** **Composite.**
5. **Give an example from the JDK.** **`java.util.regex.Pattern`.**

**▸ 🟡 L3**

1. **What does a parser do that `split` can't?** Handles **nesting, precedence and associativity**.
2. **How does recursive descent encode precedence?** **Looser levels call tighter levels** (`expr` calls `term`).
3. **How do Java 21 features change the pattern?** **Sealed records** for nodes and **exhaustive `switch`** per operation.
4. **Why parse once and cache?** Parsing dominates cost; **ASTs are immutable** and shareable.
5. **When would you use ANTLR?** When the **grammar is large, evolving or needs error recovery**.

**▸ 🔴 L4–L5**

1. **Design a promo rule engine for non-developers.** **Grammar, schema type checking, limits, explain trace, cache, versioned rules, match-rate monitoring.**
2. **How do you make user expressions safe?** **Own small grammar, no method calls or loops**, size/depth limits, field whitelist; never raw SpEL/OGNL.
3. **How would you speed up a hot interpreter?** **Compile to lambdas**, then bytecode; order `and` chains by selectivity.
4. **How would you detect conflicting promotions?** **Analyse ASTs** (interval reasoning on numeric comparisons) or use an SMT solver.
5. **Interpreter vs Strategy vs rules engine (Drools)?** Strategy picks among **coded** behaviours; Interpreter runs **user-written** ones; Drools adds **forward chaining** over many rules.

**▸ 💻 Hands-on**

**Task:** Add `in` to `SafeRuleEngineDemo` so authors can write `category in ("fruit", "dairy")`.

> ✅ **Solution**  
> ```java
> record In(Node field, List<Lit> values) implements Node { }   // add to the permits list
>
> // parser, in cmp(): after primary(), if the token is "in", read "(" lit ("," lit)* ")"
> // type checker:
> case In i -> {
>     Class<?> f = typeOf(i.field(), schema);
>     for (Lit v : i.values()) if (v.value().getClass() != f) throw new IllegalArgumentException("mixed types in 'in'");
>     yield Boolean.class;
> }
> // evaluator:
> case In i -> { Object v = eval(i.field(), facts, trace); yield i.values().stream().anyMatch(l -> l.value().equals(v)); }
> // show():
> case In i -> show(i.field()) + " in " + i.values().stream().map(SafeRuleEngineDemo::show).toList();
> ```

## 19. Practice

- [ ] Add `/` to `AstDemo` with a clear error for division by zero.
    > 💡 **Hint**  
    > Check the right operand's value in the `/` branch and throw with the expression text.

- [ ] Write a round-trip test for `PromoRuleDslDemo`: `parse(show(parse(s)))` equals `parse(s)`.
    > 💡 **Hint**  
    > Records give you `equals` for free.

- [ ] Compile `SafeRuleEngineDemo`'s AST into a `Predicate<Map<String, Object>>` and compare speed.
    > 💡 **Hint**  
    > A `switch` that returns lambdas: `case And a -> { var l = compile(a.l()); var r = compile(a.r()); yield f -> l.test(f) && r.test(f); }`.

- [ ] Detect the contradiction `cart_total > 500 and cart_total < 100` at save time.
    > 💡 **Hint**  
    > Collect numeric bounds per field across an `and` chain and check the interval is non-empty.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: represent a small language as a tree and evaluate it.
> - Pipeline: text → tokens → AST → check → evaluate (cache the AST).
> - Java 21: sealed records for nodes, one exhaustive `switch` per operation.
> - Recursive descent: looser levels call tighter levels; loops for left associativity.
> - User rules: limits, schema type checks, positions in errors, no method calls.
> - Big grammar? ANTLR or an existing language.
>
> ```java
> static boolean eval(Rule r, Map<String, Object> f) {
>     return switch (r) {
>         case Cmp c -> compare(f.get(c.field()), c.op(), c.value());
>         case And(Rule a, Rule b) -> eval(a, f) && eval(b, f);
>         case Or(Rule a, Rule b) -> eval(a, f) || eval(b, f);
>     };
> }
> ```

## 21. Further Reading

- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994), "Interpreter".
- Robert Nystrom, *Crafting Interpreters* (2021), free online; part II builds a tree-walking interpreter in Java.
- Terence Parr, *The Definitive ANTLR 4 Reference* (2013).
- [JEP 440: Record Patterns](https://openjdk.org/jeps/440) and [JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441)
- [Common Expression Language (CEL)](https://github.com/google/cel-spec): a safe, non-Turing-complete expression language.

---

[← Previous: Visitor](visitor.md) · [Next: Null Object →](null-object.md)
