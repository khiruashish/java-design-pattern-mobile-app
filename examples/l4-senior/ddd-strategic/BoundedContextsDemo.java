import java.util.Map;

/**
 * Bounded contexts: one word, several models. "Product" means different things to the catalogue, the warehouse and
 * pricing; each context keeps its own model and they share only the identifier.
 */
public class BoundedContextsDemo {

    record Sku(String code) {}                                                        // (1)!

    // Catalogue context: what customers see.
    record CatalogProduct(Sku sku, String title, String description, String imageUrl) {}
    // Warehouse context: where it is and how to handle it.
    record StockItem(Sku sku, String binLocation, int grams, boolean chilled, int onHand) {}      // (2)!
    // Pricing context: what it costs right now.
    record PricedItem(Sku sku, long mrpPaise, long salePaise, int gstPercent) {}

    /** Before: one "Product" model trying to serve everyone. */
    record GodProduct(String sku, String title, String description, String imageUrl, String bin, int grams,
                      boolean chilled, int onHand, long mrp, long sale, int gst, String supplierCode, String hsn) {}   // (3)!

    public static void main(String[] args) {
        Sku paneer = new Sku("PANEER-200G");
        var catalog = Map.of(paneer, new CatalogProduct(paneer, "Fresh Paneer 200g", "Soft malai paneer", "/img/paneer.jpg"));
        var warehouse = Map.of(paneer, new StockItem(paneer, "C-04-2", 200, true, 37));
        var pricing = Map.of(paneer, new PricedItem(paneer, 9_500, 8_900, 5));

        System.out.println("app shows:    " + catalog.get(paneer).title() + " at Rs " + pricing.get(paneer).salePaise() / 100);
        System.out.println("picker sees:  bin " + warehouse.get(paneer).binLocation() + ", chilled=" + warehouse.get(paneer).chilled());
        System.out.println("fields per model: catalogue " + CatalogProduct.class.getRecordComponents().length
                + ", warehouse " + StockItem.class.getRecordComponents().length + ", pricing " + PricedItem.class.getRecordComponents().length
                + " vs one shared model " + GodProduct.class.getRecordComponents().length);
    }
}
