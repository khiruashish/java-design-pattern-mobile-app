import java.util.List;
import java.util.Map;

/** Realistic Abstract Factory: checkout behaviour per country; tax, address format and delivery promise must match the country. */
public class RegionalCheckoutDemo {

    record Address(String line1, String city, String postcode) { }
    record Item(String sku, String category, long cents) { }

    interface TaxPolicy { long taxCents(Item item); }
    interface AddressFormatter { String format(Address a); boolean validPostcode(String p); }
    interface DeliveryPromise { String promise(int minutesFromStore); }

    interface CountryKit { TaxPolicy tax(); AddressFormatter addresses(); DeliveryPromise delivery(); String code(); }   // (1)!

    static final class IndiaKit implements CountryKit {
        public String code() { return "IN"; }
        public TaxPolicy tax() {
            Map<String, Integer> rates = Map.of("fresh", 0, "packaged", 5, "personal-care", 18);   // (2)!
            return i -> i.cents() * rates.getOrDefault(i.category(), 18) / 100;
        }
        public AddressFormatter addresses() { return new AddressFormatter() {
            public String format(Address a) { return a.line1() + ", " + a.city() + " - " + a.postcode(); }
            public boolean validPostcode(String p) { return p.matches("[1-9]\\d{5}"); } }; }
        public DeliveryPromise delivery() { return m -> m <= 20 ? "in 20 minutes" : "today"; }
    }

    static final class UaeKit implements CountryKit {
        public String code() { return "AE"; }
        public TaxPolicy tax() { return i -> i.cents() * 5 / 100; }
        public AddressFormatter addresses() { return new AddressFormatter() {
            public String format(Address a) { return a.line1() + ", " + a.city() + ", UAE"; }
            public boolean validPostcode(String p) { return p.isEmpty(); } }; }           // (3)!
        public DeliveryPromise delivery() { return m -> m <= 30 ? "in 30 minutes" : "within 2 hours"; }
    }

    static final Map<String, CountryKit> KITS = Map.of("IN", new IndiaKit(), "AE", new UaeKit());

    static String checkout(String country, List<Item> items, Address address, int minutes) {
        CountryKit kit = KITS.get(country);                                               // (4)!
        if (!kit.addresses().validPostcode(address.postcode())) return country + ": invalid postcode '" + address.postcode() + "'";
        long net = items.stream().mapToLong(Item::cents).sum();
        long tax = items.stream().mapToLong(kit.tax()::taxCents).sum();
        return kit.code() + ": net " + net + " tax " + tax + " to " + kit.addresses().format(address) + ", delivery " + kit.delivery().promise(minutes);
    }

    public static void main(String[] args) {
        List<Item> items = List.of(new Item("TOMATO-1KG", "fresh", 4_000), new Item("BISCUITS", "packaged", 3_000), new Item("SHAMPOO", "personal-care", 20_000));
        System.out.println(checkout("IN", items, new Address("Flat 4B, Lake View", "Pune", "411001"), 15));
        System.out.println(checkout("AE", items, new Address("Villa 12, Al Barsha", "Dubai", ""), 45));
        System.out.println(checkout("IN", items, new Address("Flat 4B", "Pune", "41100"), 15));
    }
}
