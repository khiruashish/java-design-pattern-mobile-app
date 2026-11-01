import java.util.ArrayList;
import java.util.List;

/** Records make good mementos, but only if they are DEEPLY immutable: copy mutable collections in the constructor. */
public class RecordSnapshotDemo {

    record ShallowSnapshot(String title, List<String> items) { }                                // (1)!

    record Snapshot(String title, List<String> items) {
        Snapshot { items = List.copyOf(items); }                                                    // (2)!
    }

    public static void main(String[] args) {
        List<String> banner = new ArrayList<>(List.of("Mango 20% off"));
        ShallowSnapshot shallow = new ShallowSnapshot("Summer sale", banner);
        Snapshot deep = new Snapshot("Summer sale", banner);

        banner.add("Free delivery");                                                                // the editor keeps changing its list
        System.out.println("shallow snapshot changed by later edits: " + shallow.items());
        System.out.println("deep snapshot stayed as saved:          " + deep.items());
        try { deep.items().add("hack"); } catch (UnsupportedOperationException e) { System.out.println("and nobody can modify it: UnsupportedOperationException"); }   // (3)!
    }
}
