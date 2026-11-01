import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Supplier<T> as a factory: pass "how to make one" into code that needs fresh objects. */
public class SupplierFactoryDemo {

    record PickList(String id, List<String> items) { }

    static final class Picker {
        private final Supplier<String> idFactory;                                        // (1)!
        Picker(Supplier<String> idFactory) { this.idFactory = idFactory; }

        List<PickList> split(List<String> items, int perList) {
            if (perList <= 0) throw new IllegalArgumentException("perList must be positive");
            List<PickList> lists = new ArrayList<>();
            for (int i = 0; i < items.size(); i += perList) {
                lists.add(new PickList(idFactory.get(), List.copyOf(items.subList(i, Math.min(i + perList, items.size())))));  // (2)!
            }
            return lists;
        }
    }

    public static void main(String[] args) {
        List<String> items = List.of("MILK", "BREAD", "EGGS", "RICE", "OIL");

        int[] seq = {0};
        Picker testPicker = new Picker(() -> "PL-" + (++seq[0]));                         // (3)!
        testPicker.split(items, 2).forEach(System.out::println);

        Picker prodPicker = new Picker(() -> "PL-" + UUID.randomUUID());                  // (4)!
        System.out.println("production ids look like PL-<uuid>: " + prodPicker.split(items, 5).get(0).id().matches("PL-[0-9a-f-]{36}"));

        Supplier<List<String>> freshList = ArrayList::new;                                // (5)!
        System.out.println("constructor reference gives a new object each time: " + (freshList.get() != freshList.get()));
    }
}
