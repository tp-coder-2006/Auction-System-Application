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
    @FXML private TextField     log_in_username;
    @FXML private PasswordField log_in_password;
    @FXML private Label         log_in_username_error;
    @FXML private Label         log_in_password_error;

    @FXML
    private void Log_in_condition(ActionEvent event) {
        clearErrors();

        String username = log_in_username.getText().trim();
        String password = log_in_password.getText();

        // Validate phía client
        if (username.isEmpty()) {
            log_in_username_error.setText("Hãy nhập tên người dùng!");
            return;
        }
        if (password.length() < 8) {
            log_in_password_error.setText("Mật khẩu phải có ít nhất 8 ký tự!");
            return;
        }

        // Gửi request lên server
        JsonObject request = new JsonObject();
        request.addProperty("action",   "LOGIN");
        request.addProperty("username", username);
        request.addProperty("password", password);

        JsonObject response = ServerConnection.sendRequest(request);

        if (response == null) {
            log_in_password_error.setText("Không thể kết nối tới Server!");
            return;
        }

        String status = response.get("status").getAsString();
        if (!"success".equals(status)) {
            log_in_password_error.setText(response.has("message")
                    ? response.get("message").getAsString()
                    : "Đăng nhập thất bại!");
            return;
        }

        if (!response.has("role") || !response.has("user_id")) {
            log_in_password_error.setText("Lỗi hệ thống: Server thiếu thông tin!");
            return;
        }

        // Lưu session
        String sessionId = response.get("session_id").getAsString();
        String sessionUsername  = response.get("username").getAsString();
        String role      = response.get("role").getAsString();
        String userId    = response.get("user_id").getAsString();
        double balance   = response.get("balance").getAsDouble();

        UserSession.getInstance().setSessionId(sessionId);
        UserSession.getInstance().setUsername(sessionUsername);
        UserSession.getInstance().setUserId(userId);
        UserSession.getInstance().setRole(role);
        UserSession.getInstance().setBalance(balance);

        Scene_Utils.resetLastPingTime();
        Scene_Utils.startSessionChecker();

        // Điều hướng theo role
        String fxmlPath = switch (role.toLowerCase()) {
            case "seller" -> "/org/auctionsystem/client/View/Seller_Dashboard.fxml";
            case "bidder" -> "/org/auctionsystem/client/View/Bidder_Dashboard.fxml";
            case "admin"  -> "/org/auctionsystem/client/View/Admin_Dashboard.fxml";
            default -> null;
        };

        if (fxmlPath == null) {
            log_in_password_error.setText("Lỗi: Vai trò '" + role + "' không hợp lệ!");
            return;
        }

        try {
            Scene_Utils.Change_Scene(event, fxmlPath);
        } catch (IOException e) {
            log_in_password_error.setText("Lỗi: Không tìm thấy file giao diện!");
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

    private void clearErrors() {
        log_in_username_error.setText("");
        log_in_password_error.setText("");
    }
}