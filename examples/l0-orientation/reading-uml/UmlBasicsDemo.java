public class UmlBasicsDemo {

    interface Payable {                                   // (1)!
        long amountDueInCents();
    }

    abstract static class Product {                       // (2)!
        private final String sku;                         // (3)!
        protected Product(String sku) { this.sku = sku; }
        public String sku() { return sku; }               // (4)!
        public abstract long priceInCents();
    }

    static final class Fruit extends Product {            // (5)!
        private final int grams;
        Fruit(String sku, int grams) { super(sku); this.grams = grams; }
        public long priceInCents() { return grams / 10; } // 1 cent per 10 g
    }

    static final class Invoice implements Payable {       // (6)!
        private final Product product;
        Invoice(Product product) { this.product = product; }
        public long amountDueInCents() { return product.priceInCents(); }
    }

    public static void main(String[] args) {
        Payable invoice = new Invoice(new Fruit("APPLE-GALA", 650));
        System.out.println("Amount due: " + invoice.amountDueInCents() + " cents");
    }
}
