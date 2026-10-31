import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Minimal Strategy: what to do when an item is out of stock, chosen by the customer. */
public class SubstitutionDemo {

    record Item(String sku, String brand, String category, int sizeMl, long priceCents) { }

    interface SubstitutionPolicy { Optional<Item> replace(Item missing, List<Item> shelf); }   // (1)!

    static final SubstitutionPolicy DONT_REPLACE = (missing, shelf) -> Optional.empty();
    static final SubstitutionPolicy SAME_BRAND = (missing, shelf) -> shelf.stream()
            .filter(i -> i.brand().equals(missing.brand()) && i.category().equals(missing.category())).findFirst();
    static final SubstitutionPolicy CHEAPEST_SIMILAR = (missing, shelf) -> shelf.stream()
            .filter(i -> i.category().equals(missing.category()) && i.sizeMl() == missing.sizeMl() && i.priceCents() <= missing.priceCents() * 11 / 10)   // (2)!
            .min(Comparator.comparingLong(Item::priceCents));

    public static void main(String[] args) {
        Item missing = new Item("MLK-DD-1L", "DairyDay", "milk", 1_000, 6_800);
        List<Item> shelf = List.of(new Item("MLK-DD-500", "DairyDay", "milk", 500, 3_500),
                new Item("MLK-GV-1L", "GreenValley", "milk", 1_000, 6_400), new Item("MLK-PR-1L", "Prime", "milk", 1_000, 9_900));
        for (var p : List.of(java.util.Map.entry("don't replace", DONT_REPLACE), java.util.Map.entry("same brand", SAME_BRAND),
                java.util.Map.entry("cheapest similar", CHEAPEST_SIMILAR))) {
            System.out.println(p.getKey() + " -> " + p.getValue().replace(missing, shelf).map(Item::sku).orElse("refund"));
        }
    }
}
