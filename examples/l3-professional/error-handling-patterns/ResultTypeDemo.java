import java.util.List;
import java.util.function.Function;

/** A Result type makes expected failures part of the method signature and forces callers to handle them. */
public class ResultTypeDemo {

    sealed interface Result<T> permits Ok, Err {                            // (1)!
        <R> Result<R> map(Function<T, R> f);
        <R> Result<R> flatMap(Function<T, Result<R>> f);
    }
    record Ok<T>(T value) implements Result<T> {
        public <R> Result<R> map(Function<T, R> f) { return new Ok<>(f.apply(value)); }
        public <R> Result<R> flatMap(Function<T, Result<R>> f) { return f.apply(value); }
    }
    record Err<T>(PinCodeError error) implements Result<T> {
        public <R> Result<R> map(Function<T, R> f) { return new Err<>(error); }
        public <R> Result<R> flatMap(Function<T, Result<R>> f) { return new Err<>(error); }
    }

    sealed interface PinCodeError {}                                        // (2)!
    record Malformed(String input) implements PinCodeError {}
    record NotServiceable(int pin) implements PinCodeError {}

    static Result<Integer> parse(String input) {
        return input.matches("[1-9]\\d{5}") ? new Ok<>(Integer.parseInt(input)) : new Err<>(new Malformed(input));
    }
    static Result<String> zoneFor(int pin) {
        return pin / 1000 == 560 ? new Ok<>("BLR-" + pin % 1000) : new Err<>(new NotServiceable(pin));
    }

    static String message(Result<String> r) {
        return switch (r) {                                                 // (3)!
            case Ok<String> ok -> "Delivering to zone " + ok.value();
            case Err<String>(Malformed m) -> "'" + m.input() + "' is not a valid PIN code";
            case Err<String>(NotServiceable n) -> "We don't deliver to " + n.pin() + " yet";
        };
    }

    public static void main(String[] args) {
        for (String input : List.of("560034", "56003A", "110001")) {
            Result<String> zone = parse(input).flatMap(ResultTypeDemo::zoneFor);
            System.out.println(input + " -> " + message(zone));
        }
    }
}
