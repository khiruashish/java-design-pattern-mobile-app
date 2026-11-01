import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Saga: a business transaction across services as a sequence of local transactions. If a step fails, the steps
 * already done are undone by compensating actions, in reverse order. This is an orchestrated saga.
 */
public class SagaDemo {

    record Step(String name, Supplier<Boolean> action, Runnable compensation) {}              // (1)!

    static boolean run(List<Step> steps) {
        Deque<Step> done = new ArrayDeque<>();
        for (Step s : steps) {
            if (s.action().get()) { System.out.println("  ok   " + s.name()); done.push(s); continue; }
            System.out.println("  FAIL " + s.name() + ", compensating");
            while (!done.isEmpty()) done.pop().compensation().run();                            // (2)!
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        for (boolean riderAvailable : new boolean[] {true, false}) {
            System.out.println("order FC-1, rider available=" + riderAvailable);
            boolean placed = run(List.of(
                    new Step("reserve 2 x MILK-1L (Inventory)", () -> true, () -> System.out.println("  compensate: release 2 x MILK-1L")),
                    new Step("charge Rs 136 (Payments via PayLink)", () -> true, () -> System.out.println("  compensate: refund Rs 136")),   // (3)!
                    new Step("book rider (Fulfilment)", () -> riderAvailable, () -> {})));
            System.out.println("  order " + (placed ? "CONFIRMED" : "CANCELLED"));
        }
    }
}
