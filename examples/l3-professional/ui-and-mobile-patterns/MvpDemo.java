import java.util.ArrayList;
import java.util.List;

/** MVP: a passive view interface and a presenter holding all UI logic, testable without any UI toolkit. */
public class MvpDemo {

    interface LoginView {                                                // (1)!
        void showLoading(boolean on);
        void showError(String message);
        void goToHome(String name);
    }

    interface AuthApi { String login(String phone, String otp); }   // returns name or throws

    static final class LoginPresenter {                                  // (2)!
        private final LoginView view; private final AuthApi api;
        LoginPresenter(LoginView view, AuthApi api) { this.view = view; this.api = api; }

        void onLoginClicked(String phone, String otp) {
            if (!phone.matches("\\d{10}")) { view.showError("Enter a 10-digit phone number"); return; }
            if (!otp.matches("\\d{6}")) { view.showError("OTP has 6 digits"); return; }
            view.showLoading(true);
            try {
                String name = api.login(phone, otp);
                view.goToHome(name);
            } catch (RuntimeException e) {
                view.showError("Login failed: " + e.getMessage());
            } finally {
                view.showLoading(false);
            }
        }
    }

    /** A recording view: what a test (or this demo) uses instead of an Android Activity. */
    static final class RecordingView implements LoginView {              // (3)!
        final List<String> calls = new ArrayList<>();
        public void showLoading(boolean on) { calls.add("loading=" + on); }
        public void showError(String m) { calls.add("error: " + m); }
        public void goToHome(String n) { calls.add("home: " + n); }
    }

    public static void main(String[] args) {
        AuthApi api = (phone, otp) -> {
            if (otp.equals("123456")) return "Asha";
            throw new IllegalArgumentException("wrong OTP");
        };
        for (String[] attempt : new String[][] {{"98765", "123456"}, {"9876543210", "12"}, {"9876543210", "000000"}, {"9876543210", "123456"}}) {
            RecordingView view = new RecordingView();
            new LoginPresenter(view, api).onLoginClicked(attempt[0], attempt[1]);
            System.out.println(attempt[0] + "/" + attempt[1] + " -> " + view.calls);
        }
    }
}
