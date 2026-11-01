import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: a build-time fitness function for FreshCart's dependencies. Banned libraries, snapshot versions in a
 * release and libraries too many major versions behind the approved baseline fail the build; minor lag only warns.
 */
public class DependencyPolicyDemo {

    record Dependency(String coordinates, String version) {
        int major() { return Integer.parseInt(version.split("[.-]")[0]); }
    }

    static final Map<String, String> BANNED = new TreeMap<>(Map.of(                                 // (1)!
            "log4j:log4j", "Log4j 1.x is end-of-life; use Log4j 2 or Logback",
            "com.netflix.hystrix:hystrix-core", "Hystrix is in maintenance mode; use Resilience4j"));
    static final Map<String, Integer> APPROVED_MAJOR = Map.of(
            "org.springframework.boot:spring-boot", 3, "com.fasterxml.jackson.core:jackson-databind", 2, "org.flywaydb:flyway-core", 10);

    static List<String> check(List<Dependency> deps, boolean release) {
        List<String> findings = new ArrayList<>();
        for (Dependency d : deps) {
            if (BANNED.containsKey(d.coordinates())) findings.add("FAIL " + d.coordinates() + ": " + BANNED.get(d.coordinates()));
            if (release && d.version().endsWith("-SNAPSHOT")) findings.add("FAIL " + d.coordinates() + ":" + d.version() + " is a snapshot in a release build");   // (2)!
            Integer approved = APPROVED_MAJOR.get(d.coordinates());
            if (approved != null && d.major() < approved - 1) findings.add("FAIL " + d.coordinates() + " " + d.version() + " is 2+ majors behind " + approved);
            else if (approved != null && d.major() < approved) findings.add("WARN " + d.coordinates() + " " + d.version() + " is behind approved major " + approved);   // (3)!
        }
        return findings;
    }

    public static void main(String[] args) {
        List<Dependency> checkout = List.of(
                new Dependency("org.springframework.boot:spring-boot", "1.5.22.RELEASE"),
                new Dependency("com.fasterxml.jackson.core:jackson-databind", "2.18.2"),
                new Dependency("com.netflix.hystrix:hystrix-core", "1.5.18"),
                new Dependency("com.freshcart:pricing-client", "4.2.0-SNAPSHOT"),
                new Dependency("org.flywaydb:flyway-core", "9.22.3"));
        List<String> findings = check(checkout, true);
        findings.forEach(System.out::println);
        System.out.println(findings.stream().anyMatch(f -> f.startsWith("FAIL")) ? "build FAILED" : "build passed with warnings");
    }
}
