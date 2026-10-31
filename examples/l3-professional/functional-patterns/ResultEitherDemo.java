import java.util.List;
import java.util.function.Function;

/** A Result (Either) type: success or failure as a value, chained with map / flatMap ("railway-oriented" code). */
public class ResultEitherDemo {

    sealed interface Result<T> permits Ok, Err {                                                        // (1)!
        default <R> Result<R> map(Function<T, R> f) {
            return switch (this) { case Ok<T> ok -> new Ok<>(f.apply(ok.value())); case Err<T> err -> new Err<>(err.error()); };
        }
        default <R> Result<R> flatMap(Function<T, Result<R>> f) {                                      // (2)!
            return switch (this) { case Ok<T> ok -> f.apply(ok.value()); case Err<T> err -> new Err<>(err.error()); };
        }
        default T orElse(T fallback) { return this instanceof Ok<T> ok ? ok.value() : fallback; }
    }
    record Ok<T>(T value) implements Result<T> { }
    record Err<T>(String error) implements Result<T> { }

    record Qty(int value) { }
    record Line(String sku, Qty qty) { }

    static Result<String> sku(String raw) { return raw.matches("[A-Z0-9-]{3,20}") ? new Ok<>(raw) : new Err<>("bad SKU '" + raw + "'"); }
    static Result<Qty> qty(String raw) {
        try { int q = Integer.parseInt(raw); return q >= 1 && q <= 20 ? new Ok<>(new Qty(q)) : new Err<>("qty must be 1-20, got " + q); }
        catch (NumberFormatException e) { return new Err<>("qty is not a number: '" + raw + "'"); }
    }

    static Result<Line> parseLine(String csv) {
        String[] parts = csv.split(",");
        if (parts.length != 2) return new Err<>("expected 'SKU,QTY', got '" + csv + "'");
        return sku(parts[0].trim()).flatMap(s -> qty(parts[1].trim()).map(q -> new Line(s, q)));        // (3)!
    }

    public static void main(String[] args) {
        for (String row : List.of("MILK-1L,2", "milk,2", "BREAD,0", "EGGS,six", "PANEER"))
            System.out.println(String.format("%-10s -> ", row) + parseLine(row));
    }
}
