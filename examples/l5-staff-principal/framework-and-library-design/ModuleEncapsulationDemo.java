import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Hiding internals with JPMS: a library module exports only its API package, exports an internal package to one
 * named friend module (a test kit), and provides an SPI implementation from a package nobody can import. The demo
 * writes the modules to a temp folder and compiles each consumer with the real javac.
 */
public class ModuleEncapsulationDemo {

    static final Map<String, String> LIBRARY = new TreeMap<>(Map.of(
            "module-info.java", """
                    module freshcart.pricing {
                        exports com.freshcart.pricing.api;
                        exports com.freshcart.pricing.internal to freshcart.testkit;
                        provides com.freshcart.pricing.api.DiscountRule with com.freshcart.pricing.internal.FestiveRule;
                    }""",                                                                         // (1)!
            "com/freshcart/pricing/api/DiscountRule.java", "package com.freshcart.pricing.api; public interface DiscountRule { long apply(long paise); }",
            "com/freshcart/pricing/internal/FestiveRule.java", "package com.freshcart.pricing.internal; public class FestiveRule implements com.freshcart.pricing.api.DiscountRule { public long apply(long p) { return p * 90 / 100; } }"));

    public static void main(String[] args) throws IOException {
        String usesApi = "import com.freshcart.pricing.api.DiscountRule; class Cart { long total(DiscountRule r) { return r.apply(10000); } }";
        String usesInternal = "import com.freshcart.pricing.internal.FestiveRule; class Cart { long total() { return new FestiveRule().apply(10000); } }";

        attempt("freshcart.ordering", "uses the exported API", usesApi);
        attempt("freshcart.ordering", "imports the internal package", usesInternal);                // (2)!
        attempt("freshcart.testkit", "imports the internal package (qualified export)", usesInternal); // (3)!
    }

    static void attempt(String module, String what, String body) throws IOException {
        Path root = Files.createTempDirectory("modules");
        write(root.resolve("freshcart.pricing"), LIBRARY);
        String pkg = module.replace("freshcart.", "com.freshcart.");
        write(root.resolve(module), Map.of(
                "module-info.java", "module " + module + " { requires freshcart.pricing; }",
                pkg.replace('.', '/') + "/Cart.java", "package " + pkg + "; " + body));
        StringWriter diagnostics = new StringWriter();
        boolean ok = compile(root, diagnostics);
        System.out.println(module + " " + what + ": " + (ok ? "compiles" : "compile error"));
        diagnostics.toString().lines().filter(l -> l.contains("error:") || l.contains("warning:"))
                .map(l -> l.substring(l.indexOf(l.contains("error:") ? "error:" : "warning:"))).distinct()
                .forEach(l -> System.out.println("  " + l));
    }

    static void write(Path dir, Map<String, String> files) throws IOException {
        for (var e : files.entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    static boolean compile(Path root, StringWriter out) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> sources;
        try (var walk = Files.walk(root)) { sources = walk.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList(); }
        List<String> options = new ArrayList<>(List.of("--module-source-path", root.toString(), "-d", root.resolve("out").toString()));
        return javac.getTask(out, null, null, options, null, javac.getStandardFileManager(null, null, null).getJavaFileObjectsFromStrings(sources)).call();
    }
}
