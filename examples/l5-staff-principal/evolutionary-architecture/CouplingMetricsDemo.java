import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Measuring coupling (Robert C. Martin's package metrics): afferent coupling Ca (who depends on me), efferent
 * coupling Ce (whom I depend on), instability I = Ce / (Ca + Ce), abstractness A, and distance from the main
 * sequence D = |A + I - 1|. Stable modules should be abstract; unstable ones concrete.
 */
public class CouplingMetricsDemo {

    record Module(String name, int abstractTypes, int totalTypes, Set<String> dependsOn) {}

    public static void main(String[] args) {
        List<Module> modules = List.of(
                new Module("shared-kernel", 6, 8, Set.of()),
                new Module("pricing", 2, 20, Set.of("shared-kernel")),
                new Module("ordering", 3, 30, Set.of("shared-kernel", "pricing", "inventory")),
                new Module("inventory", 1, 25, Set.of("shared-kernel")),
                new Module("util", 0, 40, Set.of()),
                new Module("checkout-api", 0, 12, Set.of("ordering", "pricing", "util")));
        Map<String, Integer> ca = new TreeMap<>();
        modules.forEach(m -> ca.putIfAbsent(m.name(), 0));
        modules.forEach(m -> m.dependsOn().forEach(d -> ca.merge(d, 1, Integer::sum)));                 // (1)!

        System.out.println("module         Ca Ce    I     A     D");
        Set<String> concerns = new TreeSet<>();
        for (Module m : modules) {
            int a = ca.get(m.name()), e = m.dependsOn().size();
            double instability = a + e == 0 ? 0 : (double) e / (a + e);                                  // (2)!
            double abstractness = (double) m.abstractTypes() / m.totalTypes();
            double distance = Math.abs(abstractness + instability - 1);                                 // (3)!
            System.out.printf("%-14s %2d %2d  %.2f  %.2f  %.2f%n", m.name(), a, e, instability, abstractness, distance);
            if (distance > 0.7) concerns.add(m.name() + (instability < 0.5 ? " (stable and concrete: painful to change)" : " (unstable and abstract: useless abstractions)"));
        }
        System.out.println("far from the main sequence: " + concerns);
    }
}
