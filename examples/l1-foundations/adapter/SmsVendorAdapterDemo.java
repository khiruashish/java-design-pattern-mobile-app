/** Minimal Adapter: our code depends on SmsSender; a vendor SDK with a different shape is wrapped to fit. */
public class SmsVendorAdapterDemo {

    interface SmsSender { boolean send(String phone, String text); }                     // (1)!

    /** Vendor SDK (can't change): different method name, argument order, and int status codes. */
    static final class TextBlastApi {
        int sendMessage(String senderId, String body, String msisdn) {
            if (!msisdn.startsWith("91")) return 400;
            return body.length() > 160 ? 413 : 200;
        }
    }

    static final class TextBlastSmsSender implements SmsSender {
        private final TextBlastApi api = new TextBlastApi();
        public boolean send(String phone, String text) {
            String msisdn = phone.replace("+", "");                                       // (2)!
            return api.sendMessage("FRSHCT", text, msisdn) == 200;
        }
    }

    public static void main(String[] args) {
        SmsSender sms = new TextBlastSmsSender();
        System.out.println("+919876543210 -> " + sms.send("+919876543210", "Your order is packed"));
        System.out.println("+447700900123 -> " + sms.send("+447700900123", "Your order is packed"));
        System.out.println("long text     -> " + sms.send("+919876543210", "x".repeat(200)));
    }
}
