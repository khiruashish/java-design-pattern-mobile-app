import java.util.ArrayList;
import java.util.List;

/** The Notification pattern collects every validation problem and returns them together, instead of throwing on the first. */
public class NotificationPatternDemo {

    record AddressForm(String name, String phone, String pin, String line1) {}

    record FieldError(String field, String message) {}

    static final class Notification {                                       // (1)!
        private final List<FieldError> errors = new ArrayList<>();
        void add(String field, String message) { errors.add(new FieldError(field, message)); }
        boolean hasErrors() { return !errors.isEmpty(); }
        List<FieldError> errors() { return List.copyOf(errors); }
    }

    // ---- Before: throws on the first problem; the user fixes one field per round trip. ----
    static void validateThrowing(AddressForm f) {
        if (f.name().isBlank()) throw new IllegalArgumentException("name is required");
        if (!f.phone().matches("\\d{10}")) throw new IllegalArgumentException("phone must have 10 digits");
        if (!f.pin().matches("[1-9]\\d{5}")) throw new IllegalArgumentException("PIN must have 6 digits");
    }

    // ---- After: all problems at once. ----
    static Notification validate(AddressForm f) {
        Notification n = new Notification();
        if (f.name().isBlank()) n.add("name", "is required");                  // (2)!
        if (!f.phone().matches("\\d{10}")) n.add("phone", "must have 10 digits");
        if (!f.pin().matches("[1-9]\\d{5}")) n.add("pin", "must have 6 digits");
        if (f.line1().length() < 5) n.add("line1", "is too short");
        return n;
    }

    static String save(AddressForm f) {
        Notification n = validate(f);
        if (n.hasErrors()) return "400 " + n.errors();                          // (3)!
        return "201 saved";
    }

    public static void main(String[] args) {
        AddressForm bad = new AddressForm("", "98765", "56003", "12");
        try {
            validateThrowing(bad);
        } catch (IllegalArgumentException e) {
            System.out.println("throwing: " + e.getMessage());
        }
        System.out.println("notification: " + save(bad));
        System.out.println("valid: " + save(new AddressForm("Asha", "9876543210", "560034", "12 MG Road")));
    }
}
