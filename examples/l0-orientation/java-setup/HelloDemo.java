public class HelloDemo {

    record Greeting(String to) { }                                          // (1)!

    public static void main(String[] args) {
        int feature = Runtime.version().feature();                          // (2)!
        if (feature < 21) {
            System.out.println("Java " + feature + " found; the playbook needs Java 21 or newer.");
            System.exit(1);
        }
        var greeting = new Greeting("design patterns");
        System.out.println("Hello, " + greeting.to() + "!");
        System.out.println("Running on Java " + feature);
    }
}
