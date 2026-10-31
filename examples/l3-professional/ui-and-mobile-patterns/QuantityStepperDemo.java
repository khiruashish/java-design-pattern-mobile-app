/** QuickDrop's quantity stepper: a tiny ViewModel whose state the view simply renders. */
public class QuantityStepperDemo {

    record StepperState(int qty, boolean canDecrease, boolean canIncrease) {}

    static final class StepperViewModel {
        private final int max; private int qty = 1;
        StepperViewModel(int max) { this.max = max; }
        StepperState state() { return new StepperState(qty, qty > 1, qty < max); }   // (1)!
        void plus() { if (qty < max) qty++; }
        void minus() { if (qty > 1) qty--; }
    }

    static String render(StepperState s) {                                         // (2)!
        return (s.canDecrease() ? "[-]" : "[ ]") + " " + s.qty() + " " + (s.canIncrease() ? "[+]" : "[ ]");
    }

    public static void main(String[] args) {
        StepperViewModel vm = new StepperViewModel(3);
        System.out.println(render(vm.state()));
        for (String tap : new String[] {"+", "+", "+", "-", "-", "-"}) {
            if (tap.equals("+")) vm.plus(); else vm.minus();
            System.out.println("tap " + tap + "  " + render(vm.state()));
        }
    }
}
