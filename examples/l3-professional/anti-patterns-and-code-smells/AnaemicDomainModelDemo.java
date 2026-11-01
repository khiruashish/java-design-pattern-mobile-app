/** An anaemic model has data classes with getters/setters and puts every rule in services, where it gets duplicated. */
public class AnaemicDomainModelDemo {

    // ---- Before: a bag of setters; rules live wherever someone remembered them. ----
    static final class WalletBean {                                          // (1)!
        private long balance;
        long getBalance() { return balance; }
        void setBalance(long b) { balance = b; }
    }
    static final class RefundService { void refund(WalletBean w, long amt) { w.setBalance(w.getBalance() + amt); } }
    static final class PaymentService {
        void pay(WalletBean w, long amt) {
            if (w.getBalance() < amt) throw new IllegalStateException("insufficient balance");
            w.setBalance(w.getBalance() - amt);
        }
    }
    static final class CashbackService { void reverse(WalletBean w, long amt) { w.setBalance(w.getBalance() - amt); } }   // (2)!

    // ---- After: the wallet enforces its own rules; nobody can set a balance directly. ----
    static final class Wallet {                                              // (3)!
        private long balance;
        Wallet(long opening) { if (opening < 0) throw new IllegalArgumentException("negative opening balance"); balance = opening; }
        void credit(long amount) { requirePositive(amount); balance += amount; }
        void debit(long amount) {
            requirePositive(amount);
            if (balance < amount) throw new IllegalStateException("insufficient balance: " + balance + " < " + amount);
            balance -= amount;
        }
        long balance() { return balance; }
        private static void requirePositive(long a) { if (a <= 0) throw new IllegalArgumentException("amount must be positive"); }
    }

    public static void main(String[] args) {
        WalletBean bean = new WalletBean();
        bean.setBalance(5_000);
        new PaymentService().pay(bean, 4_000);
        new CashbackService().reverse(bean, 2_500);          // forgot the balance rule
        System.out.println("anaemic wallet balance: " + bean.getBalance());

        Wallet wallet = new Wallet(5_000);
        wallet.debit(4_000);
        try {
            wallet.debit(2_500);
        } catch (IllegalStateException e) {
            System.out.println("rich wallet refuses: " + e.getMessage() + ", balance stays " + wallet.balance());
        }
    }
}
