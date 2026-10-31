import java.util.ArrayList;
import java.util.List;

/**
 * Realistic: FreshCart's checkout form. A Result type stops at the first error (good for dependent steps);
 * a Validation type collects EVERY error (good for forms). Both are values, not exceptions.
 */
public class OrderFormValidationDemo {

    record Form(String phone, String pincode, String slot, int tipRupees) { }
    record ValidOrder(String phone, String pincode, String slot, int tipRupees) { }

    /** Validation: either a value or a non-empty list of errors; combining two validations keeps both error lists. */
    sealed interface Validation<T> permits Valid, Invalid {                                         // (1)!
        static <T> Validation<T> check(boolean ok, T value, String error) { return ok ? new Valid<>(value) : new Invalid<>(List.of(error)); }
        default List<String> errors() { return this instanceof Invalid<T> i ? i.errs() : List.of(); }
    }
    record Valid<T>(T value) implements Validation<T> { }
    record Invalid<T>(List<String> errs) implements Validation<T> { }

    static Validation<ValidOrder> validate(Form f) {
        List<Validation<?>> checks = List.of(                                                            // (2)!
                Validation.check(f.phone().matches("[6-9]\\d{9}"), f.phone(), "phone must be 10 digits starting 6-9"),
                Validation.check(f.pincode().matches("\\d{6}"), f.pincode(), "pincode must be 6 digits"),
                Validation.check(List.of("08-10", "18-20").contains(f.slot()), f.slot(), "slot " + f.slot() + " is not available"),
                Validation.check(f.tipRupees() >= 0 && f.tipRupees() <= 200, f.tipRupees(), "tip must be Rs 0-200"));
        List<String> errors = new ArrayList<>();
        checks.forEach(c -> errors.addAll(c.errors()));
        return errors.isEmpty() ? new Valid<>(new ValidOrder(f.phone(), f.pincode(), f.slot(), f.tipRupees())) : new Invalid<>(List.copyOf(errors));   // (3)!
    }

    static <T> String show(Validation<T> v) {
        return switch (v) { case Valid<T> ok -> "OK " + ok.value(); case Invalid<T> bad -> "rejected " + bad.errs(); };
    }

    public static void main(String[] args) {
        System.out.println(show(validate(new Form("9845012345", "411001", "18-20", 20))));
        System.out.println(show(validate(new Form("12345", "4110", "12-14", 500))));
        System.out.println(show(validate(new Form("9845012345", "411001", "12-14", 0))));
    }
}
