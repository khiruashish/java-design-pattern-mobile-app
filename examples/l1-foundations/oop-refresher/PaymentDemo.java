import java.util.List;

public class PaymentDemo {

    interface PaymentMethod {                     // (1)!
        String pay(long amountInCents);
    }

    record CardPayment(String last4) implements PaymentMethod {
        public String pay(long amountInCents) {
            return "Charged %s to card ending %s".formatted(format(amountInCents), last4);
        }
    }

    static final class Wallet implements PaymentMethod {
        private long balanceInCents;              // (2)!

        Wallet(long openingBalanceInCents) {
            if (openingBalanceInCents < 0) {
                throw new IllegalArgumentException("Opening balance cannot be negative");
            }
            this.balanceInCents = openingBalanceInCents;
        }

        public String pay(long amountInCents) {
            if (amountInCents > balanceInCents) {
                return "Wallet declined " + format(amountInCents) + ": balance is " + format(balanceInCents);
            }
            balanceInCents -= amountInCents;
            return "Paid %s from wallet, %s left".formatted(format(amountInCents), format(balanceInCents));
        }
    }

    static String format(long cents) {
        return "$%d.%02d".formatted(cents / 100, cents % 100);
    }

    public static void main(String[] args) {
        List<PaymentMethod> methods = List.of(new CardPayment("4242"), new Wallet(1_500));
        for (PaymentMethod method : methods) {    // (3)!
            System.out.println(method.pay(1_299));
        }
        System.out.println(methods.get(1).pay(1_299));
    }
}
