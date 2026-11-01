import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Source compatibility is not binary compatibility. A partner app is compiled once against pricing-lib 1.0, then the
 * library jar is upgraded underneath it without recompiling the app (which is what happens with transitive
 * dependencies). Some changes keep working; widening a parameter from int to long compiles fine for callers but
 * breaks the already-compiled app with NoSuchMethodError.
 */
public class BinaryCompatibilityDemo {

    static final String V1_0 = "package lib; public class Pricing { public static long total(String sku, int qty) { return 6800L * qty; } }";
    static final String V1_1 = "package lib; public class Pricing { public static long total(String sku, int qty) { return 6800L * qty; } "
            + "public static long total(String sku, int qty, int discountPercent) { return 6800L * qty * (100 - discountPercent) / 100; } }"; // (1)!
    static final String V2_WIDENED = "package lib; public class Pricing { public static long total(String sku, long qty) { return 6800L * qty; } }"; // (2)!
    static final String APP = "package app; public class PartnerApp { public static void main(String[] a) { System.out.println(\"total \" + lib.Pricing.total(\"MILK-1L\", 3)); } }";

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("binary");
        Path libDir = root.resolve("lib-classes"), appDir = root.resolve("app-classes");
        compile(root, libDir, "lib/Pricing.java", V1_0, null);
        compile(root, appDir, "app/PartnerApp.java", APP, libDir);
        System.out.println("app compiled once against lib 1.0");

        for (String[] release : new String[][] {{"1.0", V1_0}, {"1.1 (adds an overload)", V1_1}, {"2.0 (int qty -> long qty)", V2_WIDENED}}) {
            compile(root, libDir, "lib/Pricing.java", release[1], null);                          // swap the jar, keep the app binary
            System.out.println("run with lib " + release[0] + ": " + run(libDir, appDir));
        }
        compile(root, appDir, "app/PartnerApp.java", APP, libDir);                                // (3)!
        System.out.println("after recompiling the app against 2.0: " + run(libDir, appDir));
    }

    static String run(Path libDir, Path appDir) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {appDir.toUri().toURL(), libDir.toUri().toURL()}, null)) {
            PrintStream original = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            System.setOut(new PrintStream(captured, true));
            try {
                loader.loadClass("app.PartnerApp").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
                return captured.toString().strip();
            } catch (InvocationTargetException e) {
                return e.getCause().getClass().getSimpleName() + " " + e.getCause().getMessage();
            } finally {
                System.setOut(original);
            }
        }
    }

    static void compile(Path root, Path out, String file, String source, Path classpath) throws IOException {
        Path src = root.resolve("src").resolve(file);
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> options = classpath == null ? List.of("-d", out.toString()) : List.of("-d", out.toString(), "-cp", classpath.toString());
        boolean ok = javac.getTask(null, null, null, options, null, javac.getStandardFileManager(null, null, null).getJavaFileObjects(src)).call();
        if (!ok) throw new IllegalStateException("compile failed: " + file);
    }
}
