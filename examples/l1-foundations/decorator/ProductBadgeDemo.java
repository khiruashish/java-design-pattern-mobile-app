/** Minimal Decorator: product labels in the app get badges, each added by a small wrapper. */
public class ProductBadgeDemo {

    interface Label { String text(); }

    record Plain(String name) implements Label { public String text() { return name; } }
    record Organic(Label inner) implements Label { public String text() { return "[Organic] " + inner.text(); } }   // (1)!
    record Bestseller(Label inner) implements Label { public String text() { return inner.text() + " *Bestseller*"; } }
    record LowStock(Label inner, int left) implements Label {
        public String text() { return left <= 5 ? inner.text() + " (only " + left + " left)" : inner.text(); }   // (2)!
    }

    public static void main(String[] args) {
        System.out.println(new Plain("Basmati 5kg").text());
        System.out.println(new Bestseller(new Organic(new Plain("Brown rice 1kg"))).text());
        System.out.println(new LowStock(new Organic(new Plain("Desi ghee 500ml")), 3).text());
        System.out.println(new LowStock(new Plain("Sugar 1kg"), 40).text());
    }
}
