import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A context map as data: who depends on whom, and how. It answers "who is affected if this context changes?" */
public class ContextMappingDemo {

    enum Relationship {                                                                 // (1)!
        PARTNERSHIP, SHARED_KERNEL, CUSTOMER_SUPPLIER, CONFORMIST, ANTICORRUPTION_LAYER, OPEN_HOST_SERVICE
    }

    record Link(String upstream, String downstream, Relationship how) {}

    static final List<Link> FRESHCART = List.of(
            new Link("Catalogue", "Search", Relationship.OPEN_HOST_SERVICE),
            new Link("Catalogue", "Pricing", Relationship.CUSTOMER_SUPPLIER),
            new Link("Pricing", "Checkout", Relationship.CUSTOMER_SUPPLIER),
            new Link("Checkout", "Delivery", Relationship.CUSTOMER_SUPPLIER),
            new Link("ParcelGo API", "Delivery", Relationship.ANTICORRUPTION_LAYER),       // (2)!
            new Link("PayLink API", "Checkout", Relationship.CONFORMIST),
            new Link("Shared Money/Sku", "Checkout", Relationship.SHARED_KERNEL),
            new Link("Shared Money/Sku", "Pricing", Relationship.SHARED_KERNEL));

    static Set<String> impactOf(String changed) {                                       // (3)!
        Set<String> affected = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>(List.of(changed));
        while (!todo.isEmpty()) {
            String up = todo.pop();
            for (Link l : FRESHCART) {
                if (!l.upstream().equals(up) || !affected.add(l.downstream())) continue;
                if (l.how() != Relationship.ANTICORRUPTION_LAYER) todo.push(l.downstream());   // an ACL absorbs change
            }
        }
        return affected;
    }

    public static void main(String[] args) {
        FRESHCART.forEach(l -> System.out.printf("%-17s -> %-9s %s%n", l.upstream(), l.downstream(), l.how()));
        System.out.println("change in Catalogue affects: " + impactOf("Catalogue"));
        System.out.println("change in ParcelGo API affects: " + impactOf("ParcelGo API") + " (stops at the ACL)");
        System.out.println("change in PayLink API affects: " + impactOf("PayLink API") + " (conformist: change flows through)");
        List<String> conformists = new ArrayList<>();
        FRESHCART.stream().filter(l -> l.how() == Relationship.CONFORMIST).forEach(l -> conformists.add(l.downstream() + " conforms to " + l.upstream()));
        System.out.println("risks to review: " + conformists);
    }
}
