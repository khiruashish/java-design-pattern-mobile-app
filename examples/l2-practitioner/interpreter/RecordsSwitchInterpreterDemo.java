import java.util.Map;

/** Modern interpreter: the AST is sealed records; evaluate, print and simplify are separate switch functions. */
public class RecordsSwitchInterpreterDemo {

    sealed interface Expr permits Num, Var, Add, Mul { }                                           // (1)!
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

    static Expr simplify(Expr e) {                                                                // (2)!
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
                if (b instanceof Num(long y) && y == 1) yield a;                                    // (3)!
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
