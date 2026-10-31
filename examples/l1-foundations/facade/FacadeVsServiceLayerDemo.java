import java.util.List;
import java.util.Map;

/** Facade simplifies access (no new rules); a service layer owns a use case's business rules and transaction. */
public class FacadeVsServiceLayerDemo {

    record Product(String sku, String name, long priceCents, int stock) { }
    record Review(String sku, int stars) { }

    static final class CatalogDb { Product product(String sku) { return new Product(sku, "Basmati 5kg", 54_900, 7); } }
    static final class ReviewsApi { List<Review> reviews(String sku) { return List.of(new Review(sku, 5), new Review(sku, 4)); } }
    static final class RecommendationsApi { List<String> similar(String sku) { return List.of("RICE-1KG", "RICE-10KG"); } }

    // ===================== Facade: assembles a view, adds NO business rules =====================
    record ProductPage(Product product, double avgStars, List<String> similar) { }

    static final class ProductPageFacade {                                                // (1)!
        private final CatalogDb catalog = new CatalogDb(); private final ReviewsApi reviews = new ReviewsApi(); private final RecommendationsApi recs = new RecommendationsApi();
        ProductPage page(String sku) {
            double avg = reviews.reviews(sku).stream().mapToInt(Review::stars).average().orElse(0);
            return new ProductPage(catalog.product(sku), avg, recs.similar(sku));
        }
    }

    // ===================== Service layer: owns rules for a use case =====================
    static final class BasketService {                                                    // (2)!
        private final CatalogDb catalog = new CatalogDb();
        private final Map<String, Integer> maxPerCustomer = Map.of("RICE-5KG", 3);
        String add(String customerId, String sku, int qty) {
            Product p = catalog.product(sku);
            if (qty > p.stock()) return "only " + p.stock() + " in stock";                // business rule
            if (qty > maxPerCustomer.getOrDefault(sku, 20)) return "limit " + maxPerCustomer.get(sku) + " per customer";   // business rule
            return "added " + qty + " x " + p.name();
        }
    }

    public static void main(String[] args) {
        System.out.println("facade:  " + new ProductPageFacade().page("RICE-5KG"));
        BasketService basket = new BasketService();
        System.out.println("service: " + basket.add("asha", "RICE-5KG", 2));
        System.out.println("service: " + basket.add("asha", "RICE-5KG", 5));
        System.out.println("service: " + basket.add("asha", "RICE-5KG", 9));
    }
}
