import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Realistic Prototype: a national campaign is designed once, then deep-copied per city and tweaked locally. */
public class CampaignCloneDemo {

    static final class DiscountRule {
        String category; int percent; long minBasketCents;
        DiscountRule(String category, int percent, long minBasketCents) { this.category = category; this.percent = percent; this.minBasketCents = minBasketCents; }
        DiscountRule copy() { return new DiscountRule(category, percent, minBasketCents); }
        public String toString() { return category + " " + percent + "% over " + minBasketCents; }
    }

    static final class Campaign {
        String name; String city; LocalDate start, end; final List<DiscountRule> rules; final List<String> bannerSkus;

        Campaign(String name, String city, LocalDate start, LocalDate end, List<DiscountRule> rules, List<String> bannerSkus) {
            this.name = name; this.city = city; this.start = start; this.end = end;
            this.rules = new ArrayList<>(); rules.forEach(r -> this.rules.add(r.copy()));   // (1)!
            this.bannerSkus = new ArrayList<>(bannerSkus);
        }

        Campaign forCity(String city) { return new Campaign(name, city, start, end, rules, bannerSkus); }   // (2)!

        public String toString() { return "[" + city + "] " + name + " " + start + ".." + end + " " + rules + " " + bannerSkus; }
    }

    public static void main(String[] args) {
        Campaign national = new Campaign("Diwali Sale", "ALL", LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12),
                List.of(new DiscountRule("sweets", 15, 50_000), new DiscountRule("dry-fruits", 10, 80_000)), List.of("KAJU-KATLI", "ALMONDS"));

        Campaign mumbai = national.forCity("mumbai");
        mumbai.rules.get(0).percent = 20;                                                  // (3)!
        mumbai.end = LocalDate.of(2026, 11, 14);

        Campaign kolkata = national.forCity("kolkata");
        kolkata.name = "Kali Puja Sale";
        kolkata.bannerSkus.set(0, "RASGULLA");

        System.out.println(national);
        System.out.println(mumbai);
        System.out.println(kolkata);
    }
}
