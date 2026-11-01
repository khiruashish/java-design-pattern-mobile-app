public class LiskovDemo {

    // ---- Violation: Square IS-A Rectangle mathematically, but not behaviourally ----
    static class Rectangle {
        protected int width;
        protected int height;
        void setWidth(int width) { this.width = width; }
        void setHeight(int height) { this.height = height; }
        int area() { return width * height; }
    }

    static class Square extends Rectangle {                              // (1)!
        @Override void setWidth(int side) { this.width = side; this.height = side; }
        @Override void setHeight(int side) { this.width = side; this.height = side; }
    }

    /** Written against Rectangle's contract: setting the height doesn't change the width. */
    static int resizeShelfLabel(Rectangle label) {                        // (2)!
        label.setWidth(5);
        label.setHeight(4);
        return label.area();
    }

    // ---- Fix: no mutable "is-a"; each shape is an immutable value with its own rules ----
    sealed interface Shape permits Rect, Sq { int area(); }               // (3)!
    record Rect(int width, int height) implements Shape { public int area() { return width * height; } }
    record Sq(int side) implements Shape { public int area() { return side * side; } }

    public static void main(String[] args) {
        System.out.println("Rectangle label area: " + resizeShelfLabel(new Rectangle()) + " (expected 20)");
        System.out.println("Square label area:    " + resizeShelfLabel(new Square()) + " (expected 20)");

        for (Shape s : new Shape[] {new Rect(5, 4), new Sq(4)}) {
            System.out.println(s + " area " + s.area());
        }
    }
}
