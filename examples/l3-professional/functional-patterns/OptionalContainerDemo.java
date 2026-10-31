import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Optional as a container: map / flatMap / filter / or, instead of nested null checks. */
public class OptionalContainerDemo {

    record Address(String city, String pincode) { }
    record Customer(String id, Address defaultAddress) { }

    static final Map<String, Customer> CUSTOMERS = new LinkedHashMap<>();
    static {
        CUSTOMERS.put("u1", new Customer("u1", new Address("Pune", "411001")));
        CUSTOMERS.put("u2", new Customer("u2", null));
    }

    // ---------- before ----------
    static String zoneBefore(String customerId) {
        Customer c = CUSTOMERS.get(customerId);
        if (c != null) {
            Address a = c.defaultAddress();
            if (a != null && a.pincode() != null && a.pincode().startsWith("411")) return "PUNE-ZONE";
        }
        return "ASK-FOR-ADDRESS";
    }

    // ---------- after ----------
    static Optional<Customer> find(String id) { return Optional.ofNullable(CUSTOMERS.get(id)); }

    static String zone(String customerId) {
        return find(customerId)                                                                         // (1)!
                .map(Customer::defaultAddress)
                .map(Address::pincode)
                .filter(pin -> pin.startsWith("411"))
                .map(pin -> "PUNE-ZONE")
                .orElse("ASK-FOR-ADDRESS");
    }

    static Optional<Address> lastOrderAddress(String id) { return id.equals("u2") ? Optional.of(new Address("Pune", "411045")) : Optional.empty(); }

    static Address deliveryAddress(String id) {
        return find(id).map(Customer::defaultAddress)
                .or(() -> lastOrderAddress(id))                                                         // (2)!
                .orElseThrow(() -> new IllegalStateException("no address for " + id));
    }

    static String expensiveDefault() { System.out.println("  (expensive default computed)"); return "fallback"; }

    public static void main(String[] args) {
        for (String id : new String[] {"u1", "u2", "u9"}) System.out.println(id + ": before=" + zoneBefore(id) + " after=" + zone(id));
        System.out.println("u2 delivers to " + deliveryAddress("u2"));
        System.out.println("orElse:     " + Optional.of("value").orElse(expensiveDefault()));               // (3)!
        System.out.println("orElseGet:  " + Optional.of("value").orElseGet(OptionalContainerDemo::expensiveDefault));
    }
}
