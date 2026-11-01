import java.util.List;

/** Replace a status switch duplicated across methods with types that carry their own behaviour. */
public class ReplaceConditionalWithPolymorphismDemo {

    // ---- Before ----
    static String messageBefore(String status, String rider, int eta) {           // (1)!
        return switch (status) {
            case "PLACED" -> "We've got your order";
            case "PACKED" -> "Packed and waiting for a rider";
            case "OUT" -> rider + " is on the way, " + eta + " min";
            case "DELIVERED" -> "Delivered. Enjoy!";
            default -> throw new IllegalArgumentException(status);
        };
    }
    static boolean canCancelBefore(String status) {
        return switch (status) { case "PLACED", "PACKED" -> true; default -> false; };
    }

    // ---- After ----
    sealed interface OrderStatus permits Placed, Packed, OutForDelivery, Delivered {   // (2)!
        String customerMessage();
        default boolean canCancel() { return false; }
    }
    record Placed() implements OrderStatus {
        public String customerMessage() { return "We've got your order"; }
        public boolean canCancel() { return true; }
    }
    record Packed() implements OrderStatus {
        public String customerMessage() { return "Packed and waiting for a rider"; }
        public boolean canCancel() { return true; }
    }
    record OutForDelivery(String rider, int etaMinutes) implements OrderStatus {      // (3)!
        public String customerMessage() { return rider + " is on the way, " + etaMinutes + " min"; }
    }
    record Delivered() implements OrderStatus {
        public String customerMessage() { return "Delivered. Enjoy!"; }
    }

    public static void main(String[] args) {
        List<OrderStatus> statuses = List.of(new Placed(), new Packed(), new OutForDelivery("Bina", 9), new Delivered());
        List<String> codes = List.of("PLACED", "PACKED", "OUT", "DELIVERED");
        for (int i = 0; i < statuses.size(); i++) {
            OrderStatus s = statuses.get(i);
            boolean same = s.customerMessage().equals(messageBefore(codes.get(i), "Bina", 9)) && s.canCancel() == canCancelBefore(codes.get(i));
            System.out.println(String.format("%-10s %-36s cancel=%-5s same=%s", codes.get(i), s.customerMessage(), s.canCancel(), same));
        }
    }
}
