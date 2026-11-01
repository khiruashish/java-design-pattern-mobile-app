/** Minimal: every error from FreshCart's API has the same shape (RFC 9457 Problem Details), so clients handle all errors one way. */
public class ProblemDetailsDemo {

    record Problem(String type, String title, int status, String detail, String instance) {      // (1)!
        String toJson() {
            return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"status\":" + status
                    + ",\"detail\":\"" + detail + "\",\"instance\":\"" + instance + "\"}";
        }
    }

    static Problem slotFull(String slot, String path) {
        return new Problem("https://api.freshcart.example/problems/slot-full", "Delivery slot is full", 409,   // (2)!
                "Slot " + slot + " has no capacity left; choose another slot", path);
    }

    public static void main(String[] args) {
        System.out.println("409 Content-Type: application/problem+json");
        System.out.println(slotFull("07:00-09:00", "/v2/orders").toJson());
    }
}
