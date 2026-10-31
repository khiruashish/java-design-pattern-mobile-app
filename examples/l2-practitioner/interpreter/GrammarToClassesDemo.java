import java.util.Set;

/**
 * Classic GoF Interpreter: each grammar rule becomes a class with interpret(context).
 *   rule     := condition | rule "AND" rule | rule "OR" rule
 *   condition:= "cart_total >" number | "category =" word
 */
public class GrammarToClassesDemo {

    record Cart(long totalRupees, Set<String> categories) { }                                     // the context

    interface Rule { boolean interpret(Cart c); }                                                  // (1)!

    record TotalAbove(long rupees) implements Rule {                                               // terminal expressions
        public boolean interpret(Cart c) { return c.totalRupees() > rupees; }
    }
    record HasCategory(String category) implements Rule {
        public boolean interpret(Cart c) { return c.categories().contains(category); }
    }
    record And(Rule left, Rule right) implements Rule {                                             // (2)!
        public boolean interpret(Cart c) { return left.interpret(c) && right.interpret(c); }
    }
    record Or(Rule left, Rule right) implements Rule {
        public boolean interpret(Cart c) { return left.interpret(c) || right.interpret(c); }
    }

    public static void main(String[] args) {
        // "cart_total > 499 AND category = fruit OR category = dairy", built by hand (no parser yet)
        Rule freeDelivery = new Or(new And(new TotalAbove(499), new HasCategory("fruit")), new HasCategory("dairy"));   // (3)!
        System.out.println("Rs 600 of fruit: " + freeDelivery.interpret(new Cart(600, Set.of("fruit"))));
        System.out.println("Rs 300 of fruit: " + freeDelivery.interpret(new Cart(300, Set.of("fruit"))));
        System.out.println("Rs 100 of dairy: " + freeDelivery.interpret(new Cart(100, Set.of("dairy"))));
    }
}
