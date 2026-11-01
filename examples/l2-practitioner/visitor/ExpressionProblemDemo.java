import java.util.Arrays;

/**
 * The expression problem: with classes-with-methods, adding a TYPE is easy and adding an OPERATION touches every class;
 * with visitors (or sealed switches), adding an OPERATION is easy and adding a TYPE touches every visitor.
 */
public class ExpressionProblemDemo {

    // ===================== OO style: operations are methods on each type =====================
    interface ItemOO { long gst(); int grams(); String label(); }                                  // (1)!
    record LooseOO(int grams) implements ItemOO { public long gst() { return 0; } public int grams() { return grams; } public String label() { return "loose"; } }
    record PackagedOO(long paise) implements ItemOO { public long gst() { return paise / 20; } public int grams() { return 500; } public String label() { return "packaged"; } }

    // ===================== Visitor style: operations are visitors over a fixed set of types =====================
    interface ItemVisitor<R> { R loose(int grams); R packaged(long paise); }                       // (2)!

    static int methodsIn(Class<?> c) { return (int) Arrays.stream(c.getDeclaredMethods()).filter(m -> !m.isSynthetic()).count(); }

    public static void main(String[] args) {
        int types = 2, operations = methodsIn(ItemOO.class);
        System.out.println("OO style:      add an operation -> edit " + types + " classes; add a type -> write 1 class");
        System.out.println("Visitor style: add an operation -> write 1 visitor; add a type -> edit every visitor (" + methodsIn(ItemVisitor.class) + " methods each) and the interface");
        System.out.println("today: " + types + " types x " + operations + " operations; pick the axis you expect to grow");   // (3)!
    }
}
