import java.util.EnumMap;
import java.util.Map;

/** Minimal Command: a packer's handheld scanner has three programmable buttons; each store maps them to different commands. */
public class ScannerButtonsDemo {

    enum Button { F1, F2, F3 }
    interface Command { void execute(String scannedSku); }                                   // (1)!

    static final class Scanner {
        private final Map<Button, Command> buttons = new EnumMap<>(Button.class);
        void assign(Button b, Command c) { buttons.put(b, c); }
        void press(Button b, String sku) { buttons.getOrDefault(b, s -> System.out.println("  " + b + " not assigned")).execute(sku); }   // (2)!
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner();
        scanner.assign(Button.F1, sku -> System.out.println("  added " + sku + " to the bag"));
        scanner.assign(Button.F2, sku -> System.out.println("  reported " + sku + " as damaged"));
        scanner.press(Button.F1, "MILK-1L");
        scanner.press(Button.F2, "EGGS-6");
        scanner.press(Button.F3, "BREAD");
    }
}
