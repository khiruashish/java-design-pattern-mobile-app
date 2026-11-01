import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Special Case (Fowler): a subclass for a particular situation that gives sensible, SPECIFIC answers,
 * not just "nothing". A Null Object is the special case for absence; here we also model a missing and a blocked customer.
 */
public class SpecialCaseDemo {

    sealed interface Customer permits Member, Guest, Blocked {
        String greeting();
        long loyaltyPoints();
        boolean canUseCashOnDelivery();
    }
    record Member(String name, long loyaltyPoints) implements Customer {
        public String greeting() { return "Welcome back, " + name; }
        public boolean canUseCashOnDelivery() { return true; }
    }
    enum Guest implements Customer {                                                                  // (1)!
        INSTANCE;
        public String greeting() { return "Hello! Sign in to earn points"; }
        public long loyaltyPoints() { return 0; }
        public boolean canUseCashOnDelivery() { return false; }
    }
    record Blocked(String name, String reason) implements Customer {                                  // (2)!
        public String greeting() { return "Hi " + name + ", please contact support"; }
        public long loyaltyPoints() { return 0; }
        public boolean canUseCashOnDelivery() { return false; }
    }

    static final Map<String, Customer> DB = new LinkedHashMap<>();
    static {
        DB.put("u1", new Member("Asha", 420));
        DB.put("u2", new Blocked("Ravi", "chargeback"));
    }

    static Customer lookup(String userId) {                                                           // (3)!
        if (userId == null) return Guest.INSTANCE;
        return DB.getOrDefault(userId, Guest.INSTANCE);
    }

    public static void main(String[] args) {
        for (String id : java.util.Arrays.asList("u1", "u2", null)) {
            Customer c = lookup(id);
            System.out.printf("%-5s %-38s points=%-4d cod=%s%n", id, c.greeting(), c.loyaltyPoints(), c.canUseCashOnDelivery());
        }
        List<Customer> all = List.of(lookup("u1"), lookup("u2"), lookup(null));
        System.out.println("blocked customers: " + all.stream().filter(c -> c instanceof Blocked).count());
    }
}
