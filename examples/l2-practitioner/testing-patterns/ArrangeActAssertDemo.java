import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Arrange-Act-Assert (Given-When-Then): one behaviour per test, three visible phases.
 * Both test styles run against a correct cart and against a cart with a bug, to show which failure message helps.
 */
public class ArrangeActAssertDemo {

    static final class Cart {
        private final boolean bug; private final Map<String, Integer> lines = new LinkedHashMap<>();
        Cart(boolean bug) { this.bug = bug; }
        void add(String sku, int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
            if (bug) lines.put(sku, qty); else lines.merge(sku, qty, Integer::sum);       // the bug: replaces instead of adding
        }
        int quantityOf(String sku) { return lines.getOrDefault(sku, 0); }
        int lineCount() { return lines.size(); }
    }

    static Supplier<Cart> newCart;

    // ---------- before: one long test checking many behaviours with bare booleans ----------
    static void cartWorks() {                                                                        // (1)!
        Cart c = newCart.get(); c.add("MILK", 1); check(c.quantityOf("MILK") == 1);
        c.add("MILK", 2); check(c.quantityOf("MILK") == 3); c.add("BREAD", 1); check(c.lineCount() == 2);
        try { c.add("EGG", 0); check(false); } catch (IllegalArgumentException e) { check(true); }
    }

    // ---------- after: one behaviour each, named as a sentence, with descriptive assertions ----------
    static void addingTheSameSkuTwiceAddsQuantities() {                                              // (2)!
        // arrange
        Cart cart = newCart.get();
        cart.add("MILK", 1);
        // act
        cart.add("MILK", 2);
        // assert
        assertEquals("MILK quantity", 3, cart.quantityOf("MILK"));
        assertEquals("line count", 1, cart.lineCount());
    }

    static void zeroQuantityIsRejected() {
        Cart cart = newCart.get();                                                                   // arrange
        IllegalArgumentException e = expectThrows(() -> cart.add("EGG", 0));                         // act
        assertEquals("message", "qty must be positive", e.getMessage());                             // assert
    }

    static void check(boolean condition) { if (!condition) throw new AssertionError("check failed"); }
    static void assertEquals(String what, Object expected, Object actual) {                         // (3)!
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected " + expected + " but was " + actual);
    }
    static IllegalArgumentException expectThrows(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException e) { return e; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    static void run(String name, Runnable test) {
        try { test.run(); System.out.println("  PASS " + name); }
        catch (AssertionError e) { System.out.println("  FAIL " + name + ": " + e.getMessage()); }
    }

    public static void main(String[] args) {
        for (boolean bug : new boolean[] {false, true}) {
            newCart = () -> new Cart(bug);
            System.out.println(bug ? "cart with a bug:" : "correct cart:");
            run("cartWorks", ArrangeActAssertDemo::cartWorks);
            run("adding the same SKU twice adds quantities", ArrangeActAssertDemo::addingTheSameSkuTwiceAddsQuantities);
            run("zero quantity is rejected", ArrangeActAssertDemo::zeroQuantityIsRejected);
        }
    }
}
