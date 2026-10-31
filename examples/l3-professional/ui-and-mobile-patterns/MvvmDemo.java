import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** MVVM: the ViewModel exposes observable state; views bind to it and never get called by name. */
public class MvvmDemo {

    /** A minimal LiveData/StateFlow: holds a value and notifies observers on change. */
    static final class Observable<T> {                                   // (1)!
        private T value; private final List<Consumer<T>> observers = new ArrayList<>();
        Observable(T initial) { value = initial; }
        T get() { return value; }
        void set(T v) { if (!v.equals(value)) { value = v; observers.forEach(o -> o.accept(v)); } }
        void observe(Consumer<T> o) { observers.add(o); o.accept(value); }
    }

    record Slot(String time, int left) {}

    static final class SlotPickerViewModel {                             // (2)!
        final Observable<List<String>> options = new Observable<>(List.of());
        final Observable<String> selected = new Observable<>("none");
        final Observable<Boolean> canContinue = new Observable<>(false);

        void load(List<Slot> slots) {
            options.set(slots.stream().map(s -> s.time() + (s.left() == 0 ? " (full)" : " (" + s.left() + " left)")).toList());
        }
        void select(Slot slot) {
            if (slot.left() == 0) return;
            selected.set(slot.time());
            canContinue.set(true);
        }
    }

    public static void main(String[] args) {
        SlotPickerViewModel vm = new SlotPickerViewModel();
        vm.options.observe(o -> System.out.println("  list shows " + o));     // (3)!
        vm.selected.observe(s -> System.out.println("  header shows selected: " + s));
        vm.canContinue.observe(c -> System.out.println("  continue button enabled: " + c));

        List<Slot> slots = List.of(new Slot("07-09", 0), new Slot("09-11", 4), new Slot("18-20", 12));
        System.out.println("> load");
        vm.load(slots);
        System.out.println("> tap 07-09");
        vm.select(slots.get(0));
        System.out.println("> tap 09-11");
        vm.select(slots.get(1));
    }
}
