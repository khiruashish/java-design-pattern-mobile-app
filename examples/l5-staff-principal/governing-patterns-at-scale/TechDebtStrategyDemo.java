import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A technical debt strategy treats debt like a loan: principal (effort to fix) and interest (time lost every month
 * while it stays). Items are ranked by payback period and funded from a fixed share of team capacity, so debt work is
 * planned and visible instead of heroic. Unfunded items stay on the register with their running cost.
 */
public class TechDebtStrategyDemo {

    record Debt(String item, int principalDays, double interestDaysPerMonth) {                     // (1)!
        double paybackMonths() { return principalDays / interestDaysPerMonth; }
    }

    public static void main(String[] args) {
        List<Debt> register = List.of(
                new Debt("God class CheckoutService (merge conflicts, slow reviews)", 15, 4.0),
                new Debt("Hand-rolled retry loops in 9 clients", 6, 3.0),
                new Debt("Service locator in ordering", 4, 0.5),
                new Debt("Flaky end-to-end suite", 8, 5.0),
                new Debt("Legacy XML config for pricing", 20, 1.0));

        int teamDaysPerQuarter = 3 * 60;
        int budget = teamDaysPerQuarter * 20 / 100;                                                 // (2)!
        System.out.println("quarter capacity " + teamDaysPerQuarter + " days, debt budget " + budget + " days (20%)");

        List<Debt> ranked = new ArrayList<>(register);
        ranked.sort(Comparator.comparingDouble(Debt::paybackMonths));                               // (3)!
        int spent = 0;
        double monthlyInterestLeft = 0;
        for (Debt d : ranked) {
            boolean funded = spent + d.principalDays() <= budget;
            if (funded) spent += d.principalDays(); else monthlyInterestLeft += d.interestDaysPerMonth();
            System.out.printf("  %-58s payback %4.1f months  %s%n", d.item(), d.paybackMonths(), funded ? "FUNDED" : "register");
        }
        System.out.printf("spent %d of %d days; still paying %.1f days per month on unfunded debt%n", spent, budget, monthlyInterestLeft);
    }
}
