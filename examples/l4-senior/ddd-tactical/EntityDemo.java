import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** An Entity is defined by a stable identity, not by its attributes; its attributes can change over its life. */
public class EntityDemo {

    record CustomerId(String value) {                                                   // (1)!
        CustomerId { if (!value.matches("C-\\d+")) throw new IllegalArgumentException("bad id " + value); }
    }

    static final class Customer {
        private final CustomerId id;
        private String name; private String phone;
        Customer(CustomerId id, String name, String phone) { this.id = id; this.name = name; this.phone = phone; }
        void changePhone(String newPhone) {
            if (!newPhone.matches("\\d{10}")) throw new IllegalArgumentException("phone must have 10 digits");
            phone = newPhone;
        }
        @Override public boolean equals(Object o) { return o instanceof Customer c && c.id.equals(id); }   // (2)!
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return id.value() + "(" + name + ", " + phone + ")"; }
    }

    /** Before: equality by all fields, as Lombok @Data or a record would generate. */
    record CustomerByValue(String id, String name, String phone) {}                   // (3)!

    public static void main(String[] args) {
        Customer asha = new Customer(new CustomerId("C-17"), "Asha", "9876500001");
        Set<Customer> vip = new HashSet<>(Set.of(asha));
        asha.changePhone("9876500002");
        System.out.println("entity still in VIP set after phone change: " + vip.contains(asha));
        System.out.println("same customer loaded twice is equal: " + asha.equals(new Customer(new CustomerId("C-17"), "Asha R", "0000000000")));

        CustomerByValue before = new CustomerByValue("C-17", "Asha", "9876500001");
        CustomerByValue after = new CustomerByValue("C-17", "Asha", "9876500002");
        System.out.println("value-equality 'customer' equal after phone change: " + before.equals(after));
        System.out.println(Objects.toString(asha));
    }
}
