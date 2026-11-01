/** TextBlast: Extract Method turns a dense block into named steps, the first move in almost every refactoring. */
public class ExtractMethodDemo {

    // ---- Before ----
    static String smsBefore(String name, String orderId, long paise, int eta) {
        String n = name == null || name.isBlank() ? "there" : name.strip().split(" ")[0];
        String amt = "Rs " + paise / 100 + (paise % 100 == 0 ? "" : "." + String.format("%02d", paise % 100));
        String msg = "Hi " + n + ", order " + orderId + " (" + amt + ") arrives in " + eta + " min.";
        return msg.length() > 160 ? msg.substring(0, 157) + "..." : msg;
    }

    // ---- After ----
    static String sms(String name, String orderId, long paise, int eta) {
        return fitToSms("Hi " + firstName(name) + ", order " + orderId + " (" + rupees(paise) + ") arrives in " + eta + " min.");   // (1)!
    }
    static String firstName(String name) { return name == null || name.isBlank() ? "there" : name.strip().split(" ")[0]; }   // (2)!
    static String rupees(long paise) { return "Rs " + paise / 100 + (paise % 100 == 0 ? "" : "." + String.format("%02d", paise % 100)); }
    static String fitToSms(String text) { return text.length() > 160 ? text.substring(0, 157) + "..." : text; }

    public static void main(String[] args) {
        System.out.println(sms("Asha Rao", "FC-12", 51_850, 18));
        System.out.println(sms(" ", "FC-13", 20_000, 25));
        System.out.println("same as before: " + (sms("Asha Rao", "FC-12", 51_850, 18).equals(smsBefore("Asha Rao", "FC-12", 51_850, 18))
                && sms(null, "X", 1, 1).equals(smsBefore(null, "X", 1, 1))));
    }
}
