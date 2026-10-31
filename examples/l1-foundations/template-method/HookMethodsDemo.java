import java.util.ArrayList;
import java.util.List;

/** Hooks: optional steps with a default (often "do nothing") that subclasses MAY override. */
public class HookMethodsDemo {

    abstract static class Packing {
        final List<String> pack(List<String> items) {
            List<String> steps = new ArrayList<>();
            beforePacking(steps);                                                         // (1)!
            steps.add("pack " + items);
            if (needsColdChain(items)) steps.add("add ice packs");                        // (2)!
            steps.add("seal bag");
            afterPacking(steps);
            return steps;
        }
        protected void beforePacking(List<String> steps) { }                              // hook: default no-op
        protected boolean needsColdChain(List<String> items) { return false; }           // hook: default answer
        protected void afterPacking(List<String> steps) { }
    }

    static final class StandardPacking extends Packing { }                                // (3)!

    static final class GroceryPacking extends Packing {
        @Override protected boolean needsColdChain(List<String> items) { return items.stream().anyMatch(i -> i.startsWith("FROZEN")); }
        @Override protected void afterPacking(List<String> steps) { steps.add("photo of sealed bag for proof"); }
    }

    static final class GiftPacking extends Packing {
        @Override protected void beforePacking(List<String> steps) { steps.add("remove price tags"); }
        @Override protected void afterPacking(List<String> steps) { steps.add("add gift note"); }   // (4)!
    }

    public static void main(String[] args) {
        List<String> items = List.of("FROZEN-PEAS", "BREAD");
        System.out.println("standard: " + new StandardPacking().pack(items));
        System.out.println("grocery:  " + new GroceryPacking().pack(items));
        System.out.println("gift:     " + new GiftPacking().pack(List.of("CHOCOLATES")));
    }
}
