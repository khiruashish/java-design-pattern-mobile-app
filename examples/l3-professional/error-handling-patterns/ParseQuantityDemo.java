import java.util.List;

/** QuickDrop: parse a quantity typed by a user, failing fast with a clear message instead of a stack trace. */
public class ParseQuantityDemo {

    static int parseQuantity(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("quantity is required");
        try {
            int qty = Integer.parseInt(input.strip());
            if (qty < 1 || qty > 20) throw new IllegalArgumentException("quantity must be 1 to 20, got " + qty);   // (1)!
            return qty;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + input + "' is not a number", e);    // (2)!
        }
    }

    public static void main(String[] args) {
        for (String input : List.of("3", " 12 ", "0", "two", "")) {
            try {
                System.out.println("'" + input + "' -> " + parseQuantity(input));
            } catch (IllegalArgumentException e) {
                System.out.println("'" + input + "' -> error: " + e.getMessage());
            }
        }
    }
}
