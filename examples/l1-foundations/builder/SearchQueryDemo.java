import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Realistic builder: the app's product search request, with filters, sort, paging and a stable URL. */
public class SearchQueryDemo {

    enum Sort { RELEVANCE, PRICE_LOW_HIGH, PRICE_HIGH_LOW, NEWEST }

    record SearchQuery(String text, List<String> categories, Long maxPriceCents, boolean inStockOnly, Sort sort, int page, int pageSize) {

        String toQueryString() {                                                          // (1)!
            StringJoiner q = new StringJoiner("&");
            q.add("q=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
            categories.forEach(c -> q.add("cat=" + c));
            if (maxPriceCents != null) q.add("maxPrice=" + maxPriceCents);
            if (inStockOnly) q.add("inStock=true");
            if (sort != Sort.RELEVANCE) q.add("sort=" + sort.name().toLowerCase());
            q.add("page=" + page).add("size=" + pageSize);
            return q.toString();
        }

        static Builder forText(String text) { return new Builder(text); }

        static final class Builder {
            private final String text; private final List<String> categories = new ArrayList<>();
            private Long maxPriceCents; private boolean inStockOnly; private Sort sort = Sort.RELEVANCE; private int page = 1, pageSize = 20;
            private Builder(String text) { this.text = text == null ? "" : text.strip(); }
            Builder category(String c) { categories.add(c.toLowerCase()); return this; }
            Builder maxPrice(long cents) { maxPriceCents = cents; return this; }
            Builder inStockOnly() { inStockOnly = true; return this; }
            Builder sort(Sort s) { sort = s; return this; }
            Builder page(int page, int size) { this.page = page; this.pageSize = size; return this; }

            SearchQuery build() {
                if (text.isEmpty() && categories.isEmpty()) throw new IllegalStateException("search needs text or a category");   // (2)!
                if (page < 1) throw new IllegalStateException("page starts at 1");
                int size = Math.min(pageSize, 50);                                        // (3)!
                return new SearchQuery(text, List.copyOf(categories.stream().distinct().sorted().toList()), maxPriceCents, inStockOnly, sort, page, size);
            }
        }
    }

    public static void main(String[] args) {
        SearchQuery q = SearchQuery.forText("  organic milk ").category("Dairy").category("organic").category("dairy")
                .maxPrice(20_000).inStockOnly().sort(Sort.PRICE_LOW_HIGH).build();
        System.out.println(q.toQueryString());
        System.out.println(SearchQuery.forText("rice").page(2, 500).build().toQueryString());
        try { SearchQuery.forText(" ").build(); } catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
