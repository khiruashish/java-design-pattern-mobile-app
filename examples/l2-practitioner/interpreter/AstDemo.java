import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** From text to an abstract syntax tree: a tokenizer and a recursive-descent parser for delivery-fee formulas. */
public class AstDemo {

    sealed interface Node permits Num, Var, Bin { }
    record Num(long value) implements Node { public String toString() { return String.valueOf(value); } }
    record Var(String name) implements Node { public String toString() { return name; } }
    record Bin(char op, Node left, Node right) implements Node { public String toString() { return "(" + op + " " + left + " " + right + ")"; } }

    static List<String> tokenize(String s) {                                                       // (1)!
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
        Node expr() {                                                                               // (2)!
            Node n = term();
            while (peek().equals("+") || peek().equals("-")) { char op = t.get(pos++).charAt(0); n = new Bin(op, n, term()); }
            return n;
        }
        Node term() {                                                                               // (3)!
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

    static long eval(Node n, Map<String, Long> vars) {                                             // (4)!
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
