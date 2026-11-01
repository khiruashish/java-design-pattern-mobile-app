import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientConnectionException;

/** Each layer translates low-level exceptions into its own vocabulary, keeping the original as the cause. */
public class ExceptionTranslationDemo {

    // Infrastructure-neutral data-access exceptions (like Spring's DataAccessException hierarchy).
    static class DataAccessException extends RuntimeException {
        DataAccessException(String m, Throwable cause) { super(m, cause); }
    }
    static class DuplicateKeyException extends DataAccessException {
        DuplicateKeyException(String m, Throwable c) { super(m, c); }
    }
    static class TransientDataAccessException extends DataAccessException {
        TransientDataAccessException(String m, Throwable c) { super(m, c); }
    }

    // Domain exception: what the business cares about.
    static class CouponAlreadyRedeemedException extends RuntimeException {
        CouponAlreadyRedeemedException(String code, Throwable cause) { super("coupon " + code + " was already used", cause); }
    }

    /** JDBC-level code: throws SQLExceptions. */
    static void insertRedemption(String code, String scenario) throws SQLException {
        switch (scenario) {
            case "duplicate" -> throw new SQLIntegrityConstraintViolationException("Duplicate entry 'WELCOME50-c42' for key 'uk_coupon_customer'", "23000", 1062);
            case "timeout" -> throw new SQLTransientConnectionException("Connection is not available, request timed out after 30000ms", "08001");
            default -> { }
        }
    }

    /** Repository: translates SQL exceptions; callers never see java.sql. */
    static void saveRedemption(String code, String scenario) {             // (1)!
        try {
            insertRedemption(code, scenario);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DuplicateKeyException("redemption exists", e);
        } catch (SQLTransientConnectionException e) {
            throw new TransientDataAccessException("database busy", e);
        } catch (SQLException e) {
            throw new DataAccessException("redemption insert failed", e);
        }
    }

    /** Service: translates data-access exceptions that have business meaning. */
    static String redeem(String code, String scenario) {                   // (2)!
        try {
            saveRedemption(code, scenario);
            return "redeemed " + code;
        } catch (DuplicateKeyException e) {
            throw new CouponAlreadyRedeemedException(code, e);
        }
    }

    public static void main(String[] args) {
        for (String scenario : new String[] {"ok", "duplicate", "timeout"}) {
            try {
                System.out.println(scenario + ": " + redeem("WELCOME50", scenario));
            } catch (CouponAlreadyRedeemedException e) {
                System.out.println(scenario + ": tell customer '" + e.getMessage() + "'");
                System.out.println("  cause chain: " + chain(e));                // (3)!
            } catch (TransientDataAccessException e) {
                System.out.println(scenario + ": retry later (" + e.getMessage() + ")");
                System.out.println("  cause chain: " + chain(e));
            }
        }
    }

    static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) sb.append(sb.isEmpty() ? "" : " <- ").append(c.getClass().getSimpleName());
        return sb.toString();
    }
}
