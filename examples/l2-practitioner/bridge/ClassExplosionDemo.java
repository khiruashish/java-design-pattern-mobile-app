import java.util.ArrayList;
import java.util.List;

/** Why Bridge exists: two dimensions in one inheritance tree multiply classes; a bridge adds them instead. */
public class ClassExplosionDemo {

    static List<String> inheritanceClasses(List<String> kinds, List<String> channels) {      // (1)!
        List<String> classes = new ArrayList<>();
        for (String k : kinds) for (String c : channels) classes.add(c + k);
        return classes;
    }

    static List<String> bridgeClasses(List<String> kinds, List<String> channels) {           // (2)!
        List<String> classes = new ArrayList<>(kinds);
        classes.addAll(channels);
        return classes;
    }

    public static void main(String[] args) {
        List<String> kinds = new ArrayList<>(List.of("OrderUpdate", "Promo", "OtpCode"));
        List<String> channels = new ArrayList<>(List.of("Sms", "Push", "Email"));
        System.out.println("inheritance: " + inheritanceClasses(kinds, channels).size() + " classes, e.g. " + inheritanceClasses(kinds, channels).subList(0, 3));
        System.out.println("bridge:      " + bridgeClasses(kinds, channels).size() + " classes " + bridgeClasses(kinds, channels));

        channels.add("WhatsApp"); kinds.add("RefundNotice");                                   // (3)!
        System.out.println("after adding WhatsApp and RefundNotice:");
        System.out.println("inheritance: " + inheritanceClasses(kinds, channels).size() + " classes (m x n)");
        System.out.println("bridge:      " + bridgeClasses(kinds, channels).size() + " classes (m + n)");
    }
}
