/** A static proxy: a hand-written class that implements the interface, adds behaviour, and delegates. */
public class StaticProxyDemo {

    interface SmsGateway { void send(String phone, String text); }

    // ===================== Before: cross-cutting code mixed into the real class =====================
    static final class TextBlastBefore implements SmsGateway {
        public void send(String phone, String text) {
            if (phone.equals("+919800000009")) { System.out.println("  blocked: opted out"); return; }   // compliance rule
            if (text.length() > 160) throw new IllegalArgumentException("too long");                  // validation
            System.out.println("  TextBlast API <- " + phone);                                         // the real work
        }   // switching to another SMS provider means copying the rules above
    }

    // ===================== After: the real class does only the real work =====================
    static final class TextBlastClient implements SmsGateway {                            // (1)!
        public void send(String phone, String text) { System.out.println("  TextBlast API <- " + phone + ": " + text); }
    }

    static final class SmsGuardProxy implements SmsGateway {                              // (2)!
        private final SmsGateway real; private final java.util.Set<String> optedOut; int blocked;
        SmsGuardProxy(SmsGateway real, java.util.Set<String> optedOut) { this.real = real; this.optedOut = optedOut; }
        public void send(String phone, String text) {
            if (optedOut.contains(phone)) { blocked++; System.out.println("  blocked: " + phone + " opted out"); return; }   // (3)!
            if (text.length() > 160) throw new IllegalArgumentException("SMS longer than 160 chars");
            real.send(phone, text);
        }
    }

    public static void main(String[] args) {
        System.out.println("before:");
        new TextBlastBefore().send("+919800000001", "Your order is on the way");
        System.out.println("after:");
        SmsGateway sms = new SmsGuardProxy(new TextBlastClient(), java.util.Set.of("+919800000009"));
        sms.send("+919800000001", "Your order is on the way");
        sms.send("+919800000009", "Big sale today!");
        System.out.println("caller only knows SmsGateway: " + (sms instanceof SmsGateway));
    }
}
