import java.util.List;
import java.util.Map;

/** Backend for Frontend: one backend per client type, shaping and aggregating data for that client's screens. */
public class BffDemo {

    // Downstream microservices, each returning more than any one screen needs.
    record Order(String id, String status, long totalPaise, String warehouse, String internalNotes) {}
    record Rider(String id, String name, String phone, double lat, double lng, String vehicleNo) {}
    record Eta(String orderId, int minutes, String model) {}

    interface OrderService { Order get(String id); }
    interface RiderService { Rider forOrder(String id); }
    interface EtaService { Eta get(String id); }

    // What the mobile tracking screen needs: one small payload, one request.
    record MobileTrackingDto(String status, String total, String rider, String eta) {}             // (1)!
    // What the web support console needs: more detail, different shape.
    record SupportConsoleDto(String orderId, String status, String warehouse, String notes, String riderPhone, String vehicle) {}

    record MobileBff(OrderService orders, RiderService riders, EtaService etas) {                  // (2)!
        MobileTrackingDto tracking(String orderId) {
            Order o = orders.get(orderId);
            Rider r = riders.forOrder(orderId);
            Eta e = etas.get(orderId);
            return new MobileTrackingDto(o.status(), "Rs " + o.totalPaise() / 100, r.name(), e.minutes() + " min");
        }
    }
    record SupportBff(OrderService orders, RiderService riders) {                                  // (3)!
        SupportConsoleDto view(String orderId) {
            Order o = orders.get(orderId);
            Rider r = riders.forOrder(orderId);
            return new SupportConsoleDto(o.id(), o.status(), o.warehouse(), o.internalNotes(), r.phone(), r.vehicleNo());
        }
    }

    public static void main(String[] args) {
        OrderService orders = id -> new Order(id, "OUT_FOR_DELIVERY", 51_800, "BLR-HSR-2", "customer asked for no plastic");
        RiderService riders = id -> new Rider("R7", "Bina", "9000000007", 12.91, 77.64, "KA01AB1234");
        EtaService etas = id -> new Eta(id, 12, "eta-v3");

        var mobile = new MobileBff(orders, riders, etas).tracking("FC-501");
        var support = new SupportBff(orders, riders).view("FC-501");
        System.out.println("mobile:  " + mobile);
        System.out.println("support: " + support);
        System.out.println("mobile payload fields: " + MobileTrackingDto.class.getRecordComponents().length
                + ", downstream fields: " + (Order.class.getRecordComponents().length + Rider.class.getRecordComponents().length + Eta.class.getRecordComponents().length));
    }
}
