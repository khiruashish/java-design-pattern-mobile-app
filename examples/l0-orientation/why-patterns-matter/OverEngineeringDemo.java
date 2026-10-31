public class OverEngineeringDemo {

    // ---- Pattern fever: four types to format one greeting that never varies ----
    interface GreetingStrategy { String greet(String name); }                 // (1)!
    static final class DefaultGreetingStrategy implements GreetingStrategy {
        public String greet(String name) { return "Hello, " + name + "!"; }
    }
    interface GreetingStrategyFactory { GreetingStrategy create(); }
    static final class DefaultGreetingStrategyFactory implements GreetingStrategyFactory {
        public GreetingStrategy create() { return new DefaultGreetingStrategy(); }
    }

    // ---- What the requirement actually needed ----
    static String greet(String name) { return "Hello, " + name + "!"; }        // (2)!

    public static void main(String[] args) {
        GreetingStrategyFactory factory = new DefaultGreetingStrategyFactory();
        System.out.println("over-engineered: " + factory.create().greet("Asha"));
        System.out.println("simple:          " + greet("Asha"));
    }
}
