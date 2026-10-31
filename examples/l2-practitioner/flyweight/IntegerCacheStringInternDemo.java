/** The JDK's own flyweights: the Integer cache, Boolean constants and the String pool. And the == trap they create. */
public class IntegerCacheStringInternDemo {

    public static void main(String[] args) {
        Integer a = 127, b = 127;                                                               // (1)!
        Integer c = 128, d = 128;
        System.out.println("Integer 127 == 127: " + (a == b) + "   (cached, -128..127)");
        System.out.println("Integer 128 == 128: " + (c == d) + "  (new objects) -> always use equals: " + c.equals(d));

        System.out.println("Boolean.valueOf(true) == Boolean.TRUE: " + (Boolean.valueOf(true) == Boolean.TRUE));   // (2)!

        String s1 = "FreshCart";                                                                // (3)!
        String s2 = "FreshCart";
        String s3 = new String("FreshCart");
        System.out.println("literal == literal: " + (s1 == s2) + ", literal == new String: " + (s1 == s3) + ", after intern(): " + (s1 == s3.intern()));

        String fromFeed = new StringBuilder("Dai").append("ry").toString();                     // (4)!
        System.out.println("built at runtime == literal: " + (fromFeed == "Dairy") + ", equals: " + fromFeed.equals("Dairy"));
    }
}
