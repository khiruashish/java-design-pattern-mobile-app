import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Optional: "there may be no value, and YOU decide what that means" (return types of lookups).
 * Null Object: "absence has one obvious behaviour: do nothing" (collaborators such as notifiers or metrics).
 */
public class NullObjectVsOptionalDemo {

    record Customer(String phone, String name) { }

    static final Map<String, Customer> CUSTOMERS = new LinkedHashMap<>();
    static { CUSTOMERS.put("98450", new Customer("98450", "Asha")); }

    static Optional<Customer> findByPhone(String phone) {                                            // (1)!
        return Optional.ofNullable(CUSTOMERS.get(phone));
    }

    interface Notifier { void send(String phone, String text); }
    record SmsNotifier() implements Notifier { public void send(String p, String t) { System.out.println("  SMS to " + p + ": " + t); } }
    enum NoNotifier implements Notifier { INSTANCE; public void send(String p, String t) { } }       // (2)!

    static void placeOrder(String phone, Notifier notifier) {
        String greeting = findByPhone(phone).map(c -> "Hi " + c.name()).orElse("Welcome, new customer");   // (3)!
        System.out.println(phone + ": " + greeting);
        notifier.send(phone, "order confirmed");
    }

    public static void main(String[] args) {
        placeOrder("98450", new SmsNotifier());
        placeOrder("70000", NoNotifier.INSTANCE);
        System.out.println("existing customer at checkout? " + findByPhone("70000").isPresent() + " -> ask them to sign up");
    }
}
