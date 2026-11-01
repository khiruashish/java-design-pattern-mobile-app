import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** QuickDrop: summing order amounts in a List<Long> vs a long[]. Boxing costs memory and indirection. */
public class BoxingCostDemo {

    static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    public static void main(String[] args) {
        int n = 1_000_000;
        long b0 = allocated();
        List<Long> boxed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) boxed.add(1_000L + i);                   // (1)!
        long b1 = allocated();
        long[] primitive = new long[n];
        for (int i = 0; i < n; i++) primitive[i] = 1_000L + i;               // (2)!
        long b2 = allocated();

        long sumBoxed = 0;
        for (Long v : boxed) sumBoxed += v;
        long sumPrimitive = 0;
        for (long v : primitive) sumPrimitive += v;

        System.err.printf("List<Long>: %.1f bytes per amount, long[]: %.1f bytes per amount%n", (b1 - b0) / (double) n, (b2 - b1) / (double) n);
        System.out.println("same sum: " + (sumBoxed == sumPrimitive) + " (" + sumPrimitive + ")");
        System.out.println("boxed list used at least twice the memory: " + ((b1 - b0) >= 2 * (b2 - b1)));
    }
}
