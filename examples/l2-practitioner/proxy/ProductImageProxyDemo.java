import java.util.List;

/** Minimal Proxy (virtual): a product list shows 20 products, but full-size images load only when a product is opened. */
public class ProductImageProxyDemo {

    interface Image { String show(); }

    static final class HighResImage implements Image {
        private final String file;
        HighResImage(String file) { this.file = file; System.out.println("  downloading " + file + " (2 MB)"); }
        public String show() { return "showing " + file; }
    }

    static final class ImageProxy implements Image {                                       // (1)!
        private final String file; private Image real;
        ImageProxy(String file) { this.file = file; }
        public String show() {
            if (real == null) real = new HighResImage(file);                                 // (2)!
            return real.show();
        }
    }

    public static void main(String[] args) {
        List<Image> images = List.of(new ImageProxy("mango.jpg"), new ImageProxy("apple.jpg"), new ImageProxy("grapes.jpg"));
        System.out.println("list screen ready with " + images.size() + " products, nothing downloaded");
        System.out.println(images.get(0).show());
        System.out.println(images.get(0).show());
    }
}
