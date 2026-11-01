import java.util.regex.Pattern;

public class PhoneNumberDemo {

    // ---- Before: the same rule copied into two places, already drifting apart ----
    static boolean signupAcceptsPhone(String phone) {
        return phone != null && phone.replace(" ", "").matches("\\+?[0-9]{10,13}");
    }

    static boolean riderAppAcceptsPhone(String phone) {
        return phone != null && phone.matches("\\+?[0-9]{10,12}");          // (1)!
    }

    // ---- After: one authoritative rule, in a type ----
    record PhoneNumber(String value) {                                       // (2)!
        private static final Pattern FORMAT = Pattern.compile("\\+?[0-9]{10,13}");

        PhoneNumber {
            if (value == null) throw new IllegalArgumentException("phone is required");
            value = value.replace(" ", "");                                  // (3)!
            if (!FORMAT.matcher(value).matches()) throw new IllegalArgumentException("invalid phone: " + value);
        }

        static boolean isValid(String raw) {
            try { new PhoneNumber(raw); return true; } catch (IllegalArgumentException e) { return false; }
        }
    }

    public static void main(String[] args) {
        String phone = "+91 98765 43210";
        System.out.println("signup accepts:    " + signupAcceptsPhone(phone));
        System.out.println("rider app accepts: " + riderAppAcceptsPhone(phone));
        System.out.println("PhoneNumber valid: " + PhoneNumber.isValid(phone));
        System.out.println("normalised:        " + new PhoneNumber(phone).value());
    }
}
