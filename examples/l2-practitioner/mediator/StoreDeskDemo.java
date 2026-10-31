import java.util.ArrayList;
import java.util.List;

/** Minimal Mediator: packers and riders don't call each other; the store desk routes messages by role. */
public class StoreDeskDemo {

    record Staff(String name, String role) { }

    static final class StoreDesk {                                                                  // (1)!
        private final List<Staff> staff = new ArrayList<>();
        void join(Staff s) { staff.add(s); }
        void send(Staff from, String toRole, String text) {
            staff.stream().filter(s -> s.role().equals(toRole))
                    .forEach(s -> System.out.println("  " + from.name() + " -> " + s.name() + ": " + text));   // (2)!
        }
    }

    public static void main(String[] args) {
        StoreDesk desk = new StoreDesk();
        Staff priya = new Staff("Priya", "packer"), arjun = new Staff("Arjun", "rider"), meena = new Staff("Meena", "rider");
        desk.join(priya); desk.join(arjun); desk.join(meena);
        desk.send(priya, "rider", "FC-9501 is packed at bay 2");
        desk.send(arjun, "packer", "picking up FC-9501");
    }
}
