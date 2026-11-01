import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A modern chain: a List of handlers. Choose "stop at first problem" or "run all and collect". */
public class ListPipelineDemo {

    record Order(String pincode, long paise, int items, boolean hasAlcohol, int customerAge) { }

    interface Check { Optional<String> problem(Order o); }                                      // (1)!

    static final List<Check> CHECKS = List.of(
            o -> o.items() == 0 ? Optional.of("cart is empty") : Optional.empty(),
            o -> o.paise() < 9_900 ? Optional.of("minimum order is Rs 99") : Optional.empty(),
            o -> o.pincode().startsWith("560") ? Optional.empty() : Optional.of("we don't deliver to " + o.pincode()),
            o -> o.hasAlcohol() && o.customerAge() < 21 ? Optional.of("age check failed") : Optional.empty());

    static Optional<String> firstProblem(Order o) {                                             // (2)!
        for (Check c : CHECKS) { Optional<String> p = c.problem(o); if (p.isPresent()) return p; }
        return Optional.empty();
    }

    static List<String> allProblems(Order o) {                                                  // (3)!
        List<String> problems = new ArrayList<>();
        CHECKS.forEach(c -> c.problem(o).ifPresent(problems::add));
        return problems;
    }

    public static void main(String[] args) {
        Order bad = new Order("110001", 5_000, 2, true, 19);
        System.out.println("stop at first: " + firstProblem(bad).orElse("ok"));
        System.out.println("collect all:   " + allProblems(bad));
        System.out.println("good order:    " + allProblems(new Order("560034", 45_000, 6, false, 30)));
    }
}
