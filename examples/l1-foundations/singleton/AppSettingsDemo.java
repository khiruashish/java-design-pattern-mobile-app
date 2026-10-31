/** The smallest useful singleton: one settings object for the whole app, as an enum. */
public class AppSettingsDemo {

    enum AppSettings {
        INSTANCE;                                                                       // (1)!

        private final String region = System.getProperty("freshcart.region", "IN-MH");
        private final int maxBasketItems = Integer.getInteger("freshcart.maxBasketItems", 50);

        String region() { return region; }
        int maxBasketItems() { return maxBasketItems; }
    }

    static String checkBasket(int items) {
        int max = AppSettings.INSTANCE.maxBasketItems();                                 // (2)!
        return items <= max ? "ok (" + items + "/" + max + ")" : "too many items (" + items + "/" + max + ")";
    }

    public static void main(String[] args) {
        System.out.println("region: " + AppSettings.INSTANCE.region());
        System.out.println("basket of 12: " + checkBasket(12));
        System.out.println("basket of 80: " + checkBasket(80));
    }
}
