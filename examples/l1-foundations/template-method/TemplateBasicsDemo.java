import java.util.ArrayList;
import java.util.List;

/** Template Method: the fulfilment workflow is fixed in a final method; stores fill in the steps that differ. */
public class TemplateBasicsDemo {

    abstract static class Fulfilment {
        final List<String> fulfil(String orderId, List<String> skus) {                    // (1)!
            List<String> log = new ArrayList<>();
            if (skus.isEmpty()) throw new IllegalArgumentException("nothing to fulfil");
            log.add(pick(skus));                                                          // (2)!
            log.add(pack(skus.size()));
            log.add("label " + orderId + " via " + courier());
            return log;
        }
        protected abstract String pick(List<String> skus);                                // (3)!
        protected abstract String pack(int items);
        protected abstract String courier();
    }

    static final class DarkStore extends Fulfilment {
        protected String pick(List<String> skus) { return "picker scans " + skus.size() + " items by aisle"; }
        protected String pack(int n) { return "FreshCart bags x" + ((n + 9) / 10); }
        protected String courier() { return "in-house rider"; }
    }

    static final class PartnerStore extends Fulfilment {
        protected String pick(List<String> skus) { return "send pick list to partner tablet"; }
        protected String pack(int n) { return "partner packs in their own bags"; }
        protected String courier() { return "QuickDrop"; }
    }

    public static void main(String[] args) {
        List<String> skus = List.of("MILK", "EGGS", "BREAD");
        System.out.println("dark store:    " + new DarkStore().fulfil("FC-1501", skus));
        System.out.println("partner store: " + new PartnerStore().fulfil("FC-1502", skus));
        try { new DarkStore().fulfil("FC-1503", List.of()); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
