package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Connectivity.ServerConnection;
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

        // Bước 1: Validate phía client trước
        if (username.isEmpty()) {
            log_in_username_error.setText("Hãy nhập tên người dùng!");
            return;
        }
        if (password.length() < 8) {
            log_in_username_error.setText("");
            log_in_password_error.setText("Mật khẩu phải có ít nhất 8 ký tự!");
            return;
        }

        // Bước 2: Gửi yêu cầu lên Server
        JsonObject request = new JsonObject();
        request.addProperty("action",   "LOGIN");
        request.addProperty("username", username);
        request.addProperty("password", password);

        JsonObject response = ServerConnection.sendRequest(request);

        // Bước 3: Xử lý phản hồi từ Server
        if (response == null) {
            log_in_username_error.setText("");
            log_in_password_error.setText("Không thể kết nối tới Server!");
            return;
        }

        String status = response.get("status").getAsString();
        if ("success".equals(status)) {
            log_in_username_error.setText("");
            log_in_password_error.setText("");

            if (!response.has("role")) {
                log_in_password_error.setText("Lỗi hệ thống: Server không trả về vai trò người dùng!");
                return;
            }

            // [MỚI] Lưu đầy đủ thông tin vào UserSession — trước đây thiếu nhiều field
            UserSession.getInstance().setSessionId(response.get("session_id").getAsString());
            UserSession.getInstance().setUserId(response.get("user_id").getAsString());
            UserSession.getInstance().setName(response.get("name").getAsString());
            UserSession.getInstance().setUsername(response.get("username").getAsString());
            UserSession.getInstance().setEmail(response.get("email").getAsString());
            UserSession.getInstance().setRole(response.get("role").getAsString());
            UserSession.getInstance().setBalance(response.get("balance").getAsDouble());
            // phone — nullable
            UserSession.getInstance().setPhone(
                    response.has("phone") && !response.get("phone").isJsonNull()
                            ? response.get("phone").getAsString() : null
            );
            // rating — nullable, chỉ có giá trị khi role = SELLER
            UserSession.getInstance().setRating(
                    response.has("rating") && !response.get("rating").isJsonNull()
                            ? response.get("rating").getAsDouble() : null
            );

            // [MỚI] Reset ping timer và bắt đầu kiểm tra session định kỳ
            Scene_Utils.resetLastPingTime();
            Scene_Utils.startSessionChecker();

            String role = response.get("role").getAsString();
            String fxml_path;
            if ("seller".equalsIgnoreCase(role)) {
                fxml_path = "/org/auctionsystem/client/View/Seller_Dashboard.fxml";
            } else if ("bidder".equalsIgnoreCase(role)) {
                fxml_path = "/org/auctionsystem/client/View/Bidder_Dashboard.fxml";
            } else if ("admin".equalsIgnoreCase(role)) {
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
            log_in_username_error.setText("");
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