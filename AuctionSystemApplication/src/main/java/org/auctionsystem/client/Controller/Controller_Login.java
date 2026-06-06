package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.event.BanWatcher;
import org.auctionsystem.client.event.BalanceWatcher;
import org.auctionsystem.client.event.NotificationManager;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Login {
    @FXML private PasswordField log_in_password;
    @FXML private TextField     log_in_username;
    @FXML private Label         log_in_password_error;
    @FXML private Label         log_in_username_error;

    @FXML
    private void Log_in_condition(ActionEvent event) {
        String username = log_in_username.getText().trim();
        String password = log_in_password.getText();

        // Reset thông báo lỗi cũ
        log_in_username_error.setText("");
        log_in_password_error.setText("");

        // Bước 1: Validate phía client
        if (username.isEmpty()) {
            log_in_username_error.setText("Hãy nhập tên người dùng!");
            return;
        }
        if (password.length() < 8) {
            log_in_password_error.setText("Mật khẩu phải có ít nhất 8 ký tự!");
            return;
        }

        // Bước 2: Gửi yêu cầu lên Server
        JsonObject request = new JsonObject();
        request.addProperty("action",   "LOGIN");
        request.addProperty("username", username);
        request.addProperty("password", password);

        // Thiết lập kết nối persistent (bật Reader Thread nhận event từ server)
        ServerConnection.connect();

        JsonObject response = ServerConnection.sendAuthRequest(request);

        // Bước 3: Xử lý phản hồi từ Server
        if (response == null) {
            log_in_password_error.setText("Không thể kết nối tới Server!");
            return;
        }

        String status = response.get("status").getAsString();
        if ("success".equals(status)) {
            if (!response.has("role")) {
                log_in_password_error.setText("Lỗi hệ thống: Server không trả về vai trò người dùng!");
                return;
            }

            // Lưu đầy đủ thông tin vào UserSession
            UserSession s = UserSession.getInstance();
            s.setSessionId(response.get("session_id").getAsString());
            s.setUserId(response.get("user_id").getAsString());
            s.setName(response.get("name").getAsString());
            s.setUsername(response.get("username").getAsString());
            s.setEmail(response.get("email").getAsString());
            s.setRole(response.get("role").getAsString());
            s.setBalance(response.get("balance").getAsDouble());
            s.setPhone(
                    response.has("phone") && !response.get("phone").isJsonNull()
                            ? response.get("phone").getAsString() : null
            );
            s.setRating(
                    response.has("rating") && !response.get("rating").isJsonNull()
                            ? response.get("rating").getAsDouble() : null
            );
            s.setRatingCount(
                    response.has("rating_count")
                            ? response.get("rating_count").getAsInt() : 0
            );
            s.setAvatarUrl(
                    response.has("avatar_url") && !response.get("avatar_url").isJsonNull()
                            ? response.get("avatar_url").getAsString() : null
            );

            // Kích hoạt BanWatcher — lắng nghe BANNED event trong suốt phiên đăng nhập.
            // Stage lấy tự động từ Scene_Utils.getPrimaryStage() (đã set trong Main.start()).
            BanWatcher.activate();
            NotificationManager.activate();

            // Chuyển màn hình theo role
            String role = response.get("role").getAsString();
            String fxml_path;
            if ("SELLER".equalsIgnoreCase(role)) {
                BalanceWatcher.activate();
                fxml_path = "/org/auctionsystem/client/View/Seller_Dashboard.fxml";
            } else if ("BIDDER".equalsIgnoreCase(role)) {
                BalanceWatcher.activate();
                fxml_path = "/org/auctionsystem/client/View/Bidder_Dashboard.fxml";
            } else if ("ADMIN".equalsIgnoreCase(role)) {
                fxml_path = "/org/auctionsystem/client/View/Admin_Dashboard.fxml";
            } else {
                log_in_password_error.setText("Lỗi: Vai trò '" + role + "' không hợp lệ!");
                return;
            }

            try {
                Scene_Utils.Change_Scene(event, fxml_path);
            } catch (IOException e) {
                log_in_password_error.setText("Lỗi: Không tìm thấy file giao diện!");
                throw new RuntimeException(e);
            }

        } else {
            String message = response.has("message")
                    ? response.get("message").getAsString()
                    : "Đăng nhập thất bại!";
            log_in_password_error.setText(message);
        }
    }

    @FXML
    private void Switching_to_register_scene(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Register_scene.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}