import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Competing consumers: several consumers share one queue to scale throughput. Plain round-robin breaks per-order
 * ordering when consumers run at different speeds; partitioning by key keeps each order on one consumer.
 */
public class CompetingConsumersDemo {

    record Msg(String orderId, int seq, String status) {}

    /** Returns the order in which statuses are applied, given an assignment rule and consumer speeds. */
    static Map<String, List<String>> simulate(List<Msg> msgs, int consumers, boolean byKey, int[] msPerMessage) {
        record Done(long at, Msg m) {}
        long[] freeAt = new long[consumers];
        List<Done> done = new ArrayList<>();
        for (int i = 0; i < msgs.size(); i++) {
            Msg m = msgs.get(i);
            int c = byKey ? Math.floorMod(m.orderId().hashCode(), consumers) : i % consumers;      // (1)!
            freeAt[c] = Math.max(freeAt[c], i) + msPerMessage[c];                                 // (2)!
            done.add(new Done(freeAt[c], m));
        }
        done.sort((a, b) -> Long.compare(a.at(), b.at()));
        Map<String, List<String>> applied = new TreeMap<>();
        done.forEach(d -> applied.computeIfAbsent(d.m().orderId(), k -> new ArrayList<>()).add(d.m().status()));
        return applied;
    }

    public static void main(String[] args) {
        List<Msg> msgs = List.of(
                new Msg("FC-1", 1, "PLACED"), new Msg("FC-1", 2, "PACKED"), new Msg("FC-2", 1, "PLACED"),
                new Msg("FC-1", 3, "DELIVERED"), new Msg("FC-2", 2, "PACKED"), new Msg("FC-2", 3, "DELIVERED"));
        int[] speeds = {50, 5};                                                                   // consumer 0 is slow (GC pause, cold cache)
        System.out.println("round-robin: " + simulate(msgs, 2, false, speeds));
        System.out.println("by order key: " + simulate(msgs, 2, true, speeds));                   // (3)!
    }
}
