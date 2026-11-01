import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FreshCart's internal checkout framework: product teams contribute steps by annotating a class; the framework
 * discovers, orders and runs them (inversion of control). A failing step is reported with its owner so the right
 * team is paged, and optional steps can fail without blocking the order.
 */
public class CheckoutStepsFrameworkDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @interface Step {                                                                              // (1)!
        int order();
        String owner();
        boolean optional() default false;
    }

    interface CheckoutStep { void run(Order order); }

    static final class Order {
        long totalPaise = 40000;
        final List<String> notes = new ArrayList<>();
    }

    @Step(order = 10, owner = "pricing-team")
    static final class ApplyCoupon implements CheckoutStep {
        public void run(Order o) { o.totalPaise -= 5000; o.notes.add("coupon FRESH50"); }
    }

    @Step(order = 30, owner = "growth-team", optional = true)
    static final class AwardLoyaltyPoints implements CheckoutStep {
        public void run(Order o) { throw new IllegalStateException("loyalty service timeout"); }
    }

    @Step(order = 20, owner = "logistics-team")
    static final class AddDeliveryFee implements CheckoutStep {
        public void run(Order o) { o.totalPaise += 2900; o.notes.add("QuickDrop fee"); }
    }

    static final class Framework {
        private final List<CheckoutStep> steps;

        Framework(Class<?> scanRoot) throws ReflectiveOperationException {
            List<CheckoutStep> found = new ArrayList<>();
            for (Class<?> c : scanRoot.getDeclaredClasses()) {
                if (c.isAnnotationPresent(Step.class) && CheckoutStep.class.isAssignableFrom(c)) {
                    found.add((CheckoutStep) c.getDeclaredConstructor().newInstance());
                }
            }
            found.sort(Comparator.comparingInt(s -> s.getClass().getAnnotation(Step.class).order())); // (2)!
            steps = List.copyOf(found);
        }

        void checkout(Order order) {
            for (CheckoutStep step : steps) {
                Step meta = step.getClass().getAnnotation(Step.class);
                try {
                    step.run(order);
                    System.out.println("  " + meta.order() + " " + step.getClass().getSimpleName() + " ok");
                } catch (RuntimeException e) {
                    System.out.println("  " + meta.order() + " " + step.getClass().getSimpleName() + " FAILED (" + e.getMessage() + "), owner " + meta.owner()); // (3)!
                    if (!meta.optional()) throw e;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Framework framework = new Framework(CheckoutStepsFrameworkDemo.class);
        Order order = new Order();
        System.out.println("checkout:");
        framework.checkout(order);
        System.out.println("total Rs " + order.totalPaise / 100.0 + ", notes " + order.notes);
    }
}
