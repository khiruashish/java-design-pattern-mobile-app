import java.util.Objects;

/** The problem Builder solves: telescoping constructors and the JavaBeans "setter soup". */
public class TelescopingDemo {

    // ===================== BEFORE 1: telescoping constructors =====================
    static final class Product {
        final String sku; final String name; final long priceInCents; final boolean organic; final int shelfLifeDays; final String brand;

        Product(String sku, String name, long price) { this(sku, name, price, false); }
        Product(String sku, String name, long price, boolean organic) { this(sku, name, price, organic, 7); }
        Product(String sku, String name, long price, boolean organic, int shelfLifeDays) { this(sku, name, price, organic, shelfLifeDays, "FreshCart"); }
        Product(String sku, String name, long price, boolean organic, int shelfLifeDays, String brand) {   // (1)!
            this.sku = sku; this.name = name; this.priceInCents = price; this.organic = organic; this.shelfLifeDays = shelfLifeDays; this.brand = brand;
        }
        public String toString() { return sku + " " + name + " " + priceInCents + "c organic=" + organic + " shelf=" + shelfLifeDays + "d brand=" + brand; }
    }

    // ===================== BEFORE 2: JavaBean with setters =====================
    static final class ProductBean {
        private String sku; private String name; private long priceInCents;
        void setSku(String sku) { this.sku = sku; }
        void setName(String name) { this.name = name; }
        void setPriceInCents(long p) { this.priceInCents = p; }
        boolean isValid() { return sku != null && name != null && priceInCents > 0; }    // (2)!
    }

    // ===================== AFTER: a builder =====================
    static final class ProductV2 {
        final String sku; final String name; final long priceInCents; final boolean organic; final int shelfLifeDays; final String brand;
        private ProductV2(Builder b) {
            sku = b.sku; name = b.name; priceInCents = b.priceInCents; organic = b.organic; shelfLifeDays = b.shelfLifeDays; brand = b.brand;
        }
        static Builder builder(String sku, String name, long priceInCents) { return new Builder(sku, name, priceInCents); }
        public String toString() { return sku + " " + name + " " + priceInCents + "c organic=" + organic + " shelf=" + shelfLifeDays + "d brand=" + brand; }

        static final class Builder {
            private final String sku; private final String name; private final long priceInCents;
            private boolean organic; private int shelfLifeDays = 7; private String brand = "FreshCart";
            private Builder(String sku, String name, long price) { this.sku = Objects.requireNonNull(sku); this.name = Objects.requireNonNull(name); this.priceInCents = price; }
            Builder organic(boolean v) { organic = v; return this; }
            Builder shelfLifeDays(int v) { shelfLifeDays = v; return this; }
            Builder brand(String v) { brand = v; return this; }
            ProductV2 build() {
                if (priceInCents <= 0) throw new IllegalArgumentException("price must be positive");
                return new ProductV2(this);
            }
        }
    }

    public static void main(String[] args) {
        // Which boolean is which? Is 3 the shelf life or something else? Readers can't tell.
        System.out.println("telescoping: " + new Product("MLK-1", "Milk", 129, true, 3, "DairyDay"));

        ProductBean bean = new ProductBean();
        bean.setSku("BRD-1");                                                             // (3)!
        System.out.println("bean half-built, valid? " + bean.isValid());

        ProductV2 milk = ProductV2.builder("MLK-1", "Milk", 129).organic(true).shelfLifeDays(3).build();
        System.out.println("builder: " + milk);
        try { ProductV2.builder("X", "Broken", 0).build(); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
