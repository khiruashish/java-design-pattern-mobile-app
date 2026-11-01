public class DemeterDemo {

    // ---- Ask style: the caller reaches through three objects and makes the decision ----
    static final class Wallet {
        private long balanceInCents;
        Wallet(long balance) { this.balanceInCents = balance; }
        long getBalanceInCents() { return balanceInCents; }
        void setBalanceInCents(long balance) { this.balanceInCents = balance; }

        // ---- Tell style: the wallet makes its own decision ----
        boolean tryDebit(long cents) {                                         // (1)!
            if (cents <= 0 || cents > balanceInCents) return false;
            balanceInCents -= cents;
            return true;
        }
    }

    static final class Customer {
        private final String name;
        private final Wallet wallet;
        Customer(String name, Wallet wallet) { this.name = name; this.wallet = wallet; }
        Wallet getWallet() { return wallet; }
        String name() { return name; }
        boolean payFromWallet(long cents) { return wallet.tryDebit(cents); }  // (2)!
    }

    static final class Order {
        private final Customer customer;
        private final long totalInCents;
        Order(Customer customer, long total) { this.customer = customer; this.totalInCents = total; }
        Customer getCustomer() { return customer; }

        String settle() {                                                      // (3)!
            return customer.payFromWallet(totalInCents)
                    ? "paid from " + customer.name() + "'s wallet"
                    : "wallet declined, ask for card";
        }
    }

    static String settleAskStyle(Order order, long total) {
        Wallet w = order.getCustomer().getWallet();                            // (4)!
        if (w.getBalanceInCents() >= total) {
            w.setBalanceInCents(w.getBalanceInCents() - total);
            return "paid (ask style)";
        }
        return "declined (ask style)";
    }

    public static void main(String[] args) {
        Order first = new Order(new Customer("Asha", new Wallet(5_000)), 3_499);
        System.out.println(settleAskStyle(first, 3_499));

        Order second = new Order(new Customer("Asha", new Wallet(5_000)), 3_499);
        System.out.println(second.settle());
        System.out.println(second.settle());
    }
}
