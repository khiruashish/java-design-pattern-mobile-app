import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Template Method without inheritance: the fixed algorithm takes the varying steps as functions. */
public class LambdaTemplateDemo {

    record Row(String sku, String price) { }

    /** The template: parse -> validate -> transform, with counts. Steps are parameters, not overrides. */
    static <T> String importRows(List<Row> rows, Function<Row, T> parse, Predicate<T> valid, Function<T, String> apply) {   // (1)!
        int ok = 0, rejected = 0;
        StringBuilder out = new StringBuilder();
        for (Row r : rows) {
            T value;
            try { value = parse.apply(r); } catch (RuntimeException e) { rejected++; continue; }
            if (!valid.test(value)) { rejected++; continue; }
            out.append(apply.apply(value)).append("; ");
            ok++;
        }
        return out + "ok=" + ok + " rejected=" + rejected;
    }

    record Price(String sku, long cents) { }

    public static void main(String[] args) {
        List<Row> rows = List.of(new Row("MILK", "68.00"), new Row("EGGS", "abc"), new Row("RICE", "-5"), new Row("OIL", "189.50"));

        String prices = importRows(rows,                                                  // (2)!
                r -> new Price(r.sku(), Math.round(Double.parseDouble(r.price()) * 100)),
                p -> p.cents() > 0,
                p -> p.sku() + "=" + p.cents());
        System.out.println("prices: " + prices);

        String skusOnly = importRows(rows, Row::sku, s -> s.length() <= 4, s -> s.toLowerCase());   // (3)!
        System.out.println("skus:   " + skusOnly);
    }
}
