import java.lang.management.ManagementFactory;

/**
 * Small, short-lived value objects are often free: escape analysis lets the JIT replace them with plain locals
 * (scalar replacement). Objects that are stored, or carried around a loop, are usually still allocated.
 * Bytes per iteration (machine- and JVM-dependent) go to stderr.
 */
public class EscapeAnalysisDemo {

    record Money(long paise) {                                                           // (1)!
        Money plus(Money o) { return new Money(paise + o.paise); }
        Money times(int q) { return new Money(paise * q); }
    }

    static long temporaries(int n) {
        long total = 0;
        for (int i = 0; i < n; i++) total += new Money(i % 100).times(3).paise();      // (2)!
        return total;
    }

    static long loopCarried(int n) {
        Money total = new Money(0);
        for (int i = 0; i < n; i++) total = total.plus(new Money(i % 100).times(3));   // (3)!
        return total.paise();
    }

    static Money[] stored(int n) {
        Money[] kept = new Money[n];
        for (int i = 0; i < n; i++) kept[i] = new Money(i % 100).times(3);
        return kept;
    }

    static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    public static void main(String[] args) {
        int n = 5_000_000;
        for (int i = 0; i < 10; i++) { temporaries(n); loopCarried(n); }
        long b0 = allocatedBytes();
        long a = temporaries(n);
        long b1 = allocatedBytes();
        long b = loopCarried(n);
        long b2 = allocatedBytes();
        Money[] kept = stored(n);
        long b3 = allocatedBytes();

        System.err.printf("temporaries:  %6.2f bytes/iteration%n", (b1 - b0) / (double) n);
        System.err.printf("loop-carried: %6.2f bytes/iteration%n", (b2 - b1) / (double) n);
        System.err.printf("stored:       %6.2f bytes/iteration%n", (b3 - b2) / (double) n);
        System.out.println("same totals: " + (a == b) + ", stored " + kept.length + " objects");
        System.out.println("temporaries allocate least: " + ((b1 - b0) < (b2 - b1) && (b1 - b0) < (b3 - b2)));
    }
}
