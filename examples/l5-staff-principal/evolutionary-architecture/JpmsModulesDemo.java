import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Modularity with JPMS: module-info.java declares what a module exports and requires. Packages that aren't exported
 * are inaccessible to other modules at compile time and run time, so internals stay internal. This demo writes two
 * small modules to a temp folder and compiles them with the real javac.
 */
public class JpmsModulesDemo {

    static final Map<String, String> PRICING = new TreeMap<>(Map.of(
            "module-info.java", "module freshcart.pricing { exports com.freshcart.pricing.api; }",                // (1)!
            "com/freshcart/pricing/api/PriceQuote.java", "package com.freshcart.pricing.api; public record PriceQuote(String sku, long paise) {}",
            "com/freshcart/pricing/internal/DiscountTable.java", "package com.freshcart.pricing.internal; public class DiscountTable { public static int percent(String sku) { return 10; } }"));

    static String ordering(String body) {
        return "package com.freshcart.ordering; " + body;
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("jpms");
        write(root.resolve("freshcart.pricing"), PRICING);
        String good = ordering("import com.freshcart.pricing.api.PriceQuote; public class Cart { PriceQuote q = new PriceQuote(\"MILK-1L\", 6800); }");
        String bad = ordering("import com.freshcart.pricing.internal.DiscountTable; public class Cart { int p = DiscountTable.percent(\"MILK-1L\"); }");

        for (String[] attempt : new String[][] {{"uses the exported API", good}, {"reaches into internals", bad}}) {
            Path orderingDir = root.resolve("freshcart.ordering");
            write(orderingDir, Map.of("module-info.java", "module freshcart.ordering { requires freshcart.pricing; }",   // (2)!
                    "com/freshcart/ordering/Cart.java", attempt[1]));
            String errors = compile(root);
            System.out.println("ordering " + attempt[0] + ": " + (errors.isEmpty() ? "compiles" : "compile error"));
            errors.lines().filter(l -> l.contains("error:")).map(l -> l.substring(l.indexOf("error:"))).distinct()
                    .forEach(l -> System.out.println("  " + l));                                         // (3)!
        }
    }

    static void write(Path dir, Map<String, String> files) throws IOException {
        for (var e : files.entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    static String compile(Path root) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> sources;
        try (var walk = Files.walk(root)) { sources = walk.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList(); }
        StringWriter out = new StringWriter();
        List<String> options = new java.util.ArrayList<>(List.of("--module-source-path", root.toString(), "-d", root.resolve("out").toString()));
        javac.getTask(out, null, null, options, null, javac.getStandardFileManager(null, null, null).getJavaFileObjectsFromStrings(sources)).call();
        return out.toString();
    }
}
