import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Realistic: FreshCart sends login OTPs through TextBlast. TextBlast returns 429 with Retry-After when we exceed
 * our quota and 503 when it's struggling. The sender honours Retry-After, backs off with jitter on 503, gives up
 * after an overall deadline (an OTP that arrives in 2 minutes is useless) and fails over to a backup provider.
 */
public class OtpSenderDemo {

    record Reply(int status, long retryAfterMs) {}
    interface SmsProvider { String name(); Reply send(String phone, String otp, long nowMs); }

    static final class OtpSender {
        private final List<SmsProvider> providers; private final long deadlineMs; private final Random random;
        final List<String> log = new ArrayList<>();
        OtpSender(List<SmsProvider> providers, long deadlineMs, Random random) { this.providers = providers; this.deadlineMs = deadlineMs; this.random = random; }

        boolean send(String phone, String otp) {
            long now = 0;
            for (SmsProvider p : providers) {
                for (int attempt = 1; attempt <= 4 && now < deadlineMs; attempt++) {
                    Reply r = p.send(phone, otp, now);
                    log.add("t=" + now + " " + p.name() + " -> " + r.status());
                    if (r.status() == 200) return true;
                    if (r.status() == 400) return false;                                         // bad number: no retry, no failover
                    long wait = r.status() == 429 ? r.retryAfterMs()                              // (1)!
                            : (long) (random.nextDouble() * 200 * (1L << (attempt - 1)));          // (2)!
                    now += wait;
                }
                log.add("failing over from " + p.name());                                         // (3)!
            }
            return false;
        }
    }

    public static void main(String[] args) {
        SmsProvider textBlast = new SmsProvider() {
            public String name() { return "TextBlast"; }
            public Reply send(String phone, String otp, long now) {
                if (now < 1000) return new Reply(429, 1000);
                return new Reply(503, 0);
            }
        };
        SmsProvider backup = new SmsProvider() {
            public String name() { return "BackupSms"; }
            public Reply send(String phone, String otp, long now) { return new Reply(200, 0); }
        };
        OtpSender sender = new OtpSender(List.of(textBlast, backup), 5000, new Random(7));
        System.out.println("delivered: " + sender.send("+91-98xxxx1234", "482913"));
        sender.log.forEach(l -> System.out.println("  " + l));
    }
}
