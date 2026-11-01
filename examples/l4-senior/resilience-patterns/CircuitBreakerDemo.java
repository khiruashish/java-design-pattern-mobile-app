import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Circuit breaker: after too many recent failures, stop calling the dependency (OPEN) and fail fast. After a wait,
 * let a few trial calls through (HALF_OPEN); close again if they succeed. Uses a virtual clock for stable output.
 */
public class CircuitBreakerDemo {

    enum State { CLOSED, OPEN, HALF_OPEN }

    static final class CircuitBreaker {
        private final int windowSize; private final double failureRateThreshold; private final long openMs; private final int trialCalls;
        private final Deque<Boolean> window = new ArrayDeque<>();
        private State state = State.CLOSED; private long openedAt; private int trialsLeft, trialFailures;
        CircuitBreaker(int windowSize, double failureRateThreshold, long openMs, int trialCalls) {
            this.windowSize = windowSize; this.failureRateThreshold = failureRateThreshold; this.openMs = openMs; this.trialCalls = trialCalls;
        }

        String call(Supplier<String> remote, long now) {
            if (state == State.OPEN) {
                if (now - openedAt < openMs) return "rejected fast (OPEN)";                       // (1)!
                state = State.HALF_OPEN; trialsLeft = trialCalls; trialFailures = 0;
            }
            boolean ok;
            String result;
            try { result = remote.get(); ok = true; } catch (RuntimeException e) { result = "failed: " + e.getMessage(); ok = false; }
            if (state == State.HALF_OPEN) {                                                       // (2)!
                if (!ok) trialFailures++;
                if (--trialsLeft == 0 || trialFailures > 0) {
                    if (trialFailures > 0) open(now); else { state = State.CLOSED; window.clear(); }
                }
                return result + " [" + state + "]";
            }
            window.addLast(ok);
            if (window.size() > windowSize) window.removeFirst();
            long failures = window.stream().filter(b -> !b).count();
            if (window.size() == windowSize && (double) failures / windowSize >= failureRateThreshold) open(now);   // (3)!
            return result + " [" + state + "]";
        }
        private void open(long now) { state = State.OPEN; openedAt = now; }
    }

    public static void main(String[] args) {
        CircuitBreaker breaker = new CircuitBreaker(4, 0.5, 30_000, 2);
        boolean[] paylinkUp = {true};
        int[] remoteCalls = {0};
        Supplier<String> paylink = () -> {
            remoteCalls[0]++;
            if (!paylinkUp[0]) throw new IllegalStateException("PayLink 503");
            return "paid";
        };
        long t = 0;
        System.out.println("t=" + t + " " + breaker.call(paylink, t));
        paylinkUp[0] = false;
        for (int i = 0; i < 3; i++) { t += 1000; System.out.println("t=" + t + " " + breaker.call(paylink, t)); }
        for (int i = 0; i < 3; i++) { t += 1000; System.out.println("t=" + t + " " + breaker.call(paylink, t)); }
        t = 40_000; System.out.println("t=" + t + " " + breaker.call(paylink, t));
        paylinkUp[0] = true;
        t = 75_000; System.out.println("t=" + t + " " + breaker.call(paylink, t));
        t = 76_000; System.out.println("t=" + t + " " + breaker.call(paylink, t));
        System.out.println("remote calls made: " + remoteCalls[0] + " of 10 requests");
    }
}
