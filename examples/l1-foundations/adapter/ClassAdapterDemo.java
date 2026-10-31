import java.math.BigDecimal;
import java.math.RoundingMode;

/** Class adapter: the adapter EXTENDS the adaptee and IMPLEMENTS the target. Java allows this only for one class. */
public class ClassAdapterDemo {

    interface Scale { BigDecimal weightKg(); }                                             // target

    /** Old store hardware SDK: grams as int, plus a tare step. */
    static class LegacyWeighingMachine {
        protected int tareGrams = 0;
        void tare(int grams) { tareGrams = grams; }
        int readGrams() { return 1_250; }                                                 // simulated sensor
    }

    static final class LegacyScaleAdapter extends LegacyWeighingMachine implements Scale {   // (1)!
        public BigDecimal weightKg() {
            int net = readGrams() - tareGrams;                                            // (2)!
            return BigDecimal.valueOf(net).divide(BigDecimal.valueOf(1_000), 3, RoundingMode.HALF_UP);
        }
    }

    public static void main(String[] args) {
        LegacyScaleAdapter scale = new LegacyScaleAdapter();
        scale.tare(50);                                                                   // (3)!
        Scale target = scale;
        System.out.println("net weight: " + target.weightKg() + " kg");
        System.out.println("price at 120/kg: " + target.weightKg().multiply(BigDecimal.valueOf(120)).setScale(2, RoundingMode.HALF_UP));
    }
}
