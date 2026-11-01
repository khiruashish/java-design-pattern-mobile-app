import java.util.ArrayList;
import java.util.List;

/** Rate limiter: a token bucket allows short bursts up to its capacity and a steady rate after that. */
public class RateLimiterDemo {

    static final class TokenBucket {
        private final double capacity, refillPerMs; private double tokens; private long last;
        TokenBucket(int capacity, int perSecond) { this.capacity = capacity; this.refillPerMs = perSecond / 1000.0; this.tokens = capacity; }  // (1)!
        boolean tryAcquire(long nowMs) {
            tokens = Math.min(capacity, tokens + (nowMs - last) * refillPerMs);                     // (2)!
            last = nowMs;
            if (tokens >= 1) { tokens -= 1; return true; }
            return false;                                                                         // (3)!
        }
    }

    public static void main(String[] args) {
        TokenBucket textBlast = new TokenBucket(5, 2);     // TextBlast SMS API allows 2/s with bursts of 5
        List<String> burst = new ArrayList<>();
        for (int i = 0; i < 8; i++) burst.add(textBlast.tryAcquire(0) ? "send" : "wait");
        System.out.println("8 OTPs at t=0:      " + burst);
        List<String> later = new ArrayList<>();
        for (long t = 500; t <= 2000; t += 250) later.add("t=" + t + ":" + (textBlast.tryAcquire(t) ? "send" : "wait"));
        System.out.println("one every 250 ms:   " + later);
        System.out.println("after 10 s idle:    burst of " + countBurst(textBlast, 12_000) + " allowed");
    }

    static int countBurst(TokenBucket b, long now) { int n = 0; while (b.tryAcquire(now)) n++; return n; }
}
