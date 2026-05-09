package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Connectivity.ServerConnection;

import java.io.IOException;

public class Controller_Login {
    @FXML private PasswordField log_in_password;
    @FXML private TextField log_in_username;
    @FXML private Label log_in_password_error;
    @FXML private Label log_in_username_error;

    @FXML
    private void Log_in_condition(ActionEvent event) {
        String username = log_in_username.getText().trim();
        String password = log_in_password.getText();

        // Bước 1: Validate phía client trước — nhanh, không cần hỏi server
        if (username.isEmpty()) {
            log_in_username_error.setText("Hãy nhập tên người dùng!");
            return;
        }
        if (password.length() < 8) {
            log_in_username_error.setText("");
            log_in_password_error.setText("Mật khẩu phải có ít nhất 8 ký tự!");
            return;
        }

        // Bước 2: Gửi yêu cầu lên Server để kiểm tra với database thật
        JsonObject request = new JsonObject();
        request.addProperty("action", "LOGIN");
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
            try {
                Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
            } catch (IOException e) {
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
