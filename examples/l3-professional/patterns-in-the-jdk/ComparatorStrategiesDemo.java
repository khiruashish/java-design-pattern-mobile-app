import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Comparator is the JDK's most used Strategy: sorting algorithms take the comparison as a parameter. */
public class ComparatorStrategiesDemo {

    record Product(String name, String brand, Long pricePaise) { }

    public static void main(String[] args) {
        List<Product> ps = Arrays.asList(new Product("Milk", "Amul", 6_400L), new Product("milk", "Gokul", 6_000L),
                new Product("apple juice", "Farm", null), new Product("Bread", "Amul", 4_500L));

        Comparator<Product> byPriceNullsLast = Comparator.comparing(Product::pricePaise, Comparator.nullsLast(Comparator.naturalOrder()));   // (1)!
        System.out.println("by price: " + ps.stream().sorted(byPriceNullsLast).map(Product::name).toList());

        System.out.println("String order: " + ps.stream().map(Product::name).sorted().toList());
        Collator collator = Collator.getInstance(Locale.ENGLISH);                                     // (2)!
        System.out.println("Collator:     " + ps.stream().map(Product::name).sorted(collator).toList());

        TreeSet<Product> byBrand = new TreeSet<>(Comparator.comparing(Product::brand));                 // (3)!
        byBrand.addAll(ps);
        System.out.println("TreeSet by brand keeps " + byBrand.size() + " of " + ps.size() + ": " + byBrand.stream().map(Product::name).toList());
    }
}
