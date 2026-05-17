package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Connectivity.ServerConnection;

import java.io.IOException;

public class Controller_Register {
    @FXML private RadioButton register_as_bidder;
    @FXML private RadioButton register_as_seller;
    @FXML private TextField register_name;
    @FXML private TextField register_username;
    @FXML private TextField register_email;
    @FXML private TextField register_phone;      // [MỚI] nullable
    @FXML private PasswordField register_password;
    @FXML private Label register_error_announcement;

    @FXML
    public void Registering(ActionEvent event) throws IOException {
        String name     = register_name.getText().trim();
        String username = register_username.getText().trim();
        String email    = register_email.getText().trim();
        String phone    = register_phone.getText().trim();  // [MỚI] để trống = null khi gửi
        String password = register_password.getText();
        // Kiểm tra vai trò
        if (!register_as_bidder.isSelected() && !register_as_seller.isSelected()) {
            register_error_announcement.setText("Vui lòng chọn vai trò (Bidder hoặc Seller)");
            return;
        }
        String role;
        if (register_as_bidder.isSelected()) {
            role = "bidder";
        } else {
            role = "seller";
        }

        // Bước 1: Validate phía client
        if (name.isEmpty() || username.isEmpty() || email.isEmpty()) {
            register_error_announcement.setText("Vui lòng điền đầy đủ thông tin!");
            return;
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            register_error_announcement.setText("Email không hợp lệ!");
            return;
        }
        if (password.length() < 8) {
            register_error_announcement.setText("Mật khẩu phải có ít nhất 8 ký tự!");
            return;
        }

        // Bước 2: Gửi yêu cầu đăng ký lên Server
        JsonObject request = new JsonObject();
        request.addProperty("action",   "REGISTER");
        request.addProperty("username", username);
        request.addProperty("password", password);
        request.addProperty("email",    email);
        request.addProperty("name",     name);
        request.addProperty("role",     role);
        // [MỚI] phone không bắt buộc — gửi null nếu để trống
        if (!phone.isEmpty()) request.addProperty("phone", phone);
        else request.addProperty("phone", (String) null);

        JsonObject response = ServerConnection.sendRequest(request);

        // Bước 3: Xử lý phản hồi
        if (response == null) {
            register_error_announcement.setText("Không thể kết nối tới Server!");
            return;
        }

        String status = response.get("status").getAsString();
        if ("success".equals(status)) {
            // [MỚI] Hiện pop-up thông báo thành công trước khi chuyển về Login
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Đăng ký thành công");
            alert.setHeaderText(null);
            alert.setContentText("Tài khoản đã được tạo thành công! Vui lòng đăng nhập.");
            alert.showAndWait();

            Scene_Utils.Change_Scene(
                    event, "/org/auctionsystem/client/View/Login_scene.fxml");
        } else {
            String message = response.has("message")
                    ? response.get("message").getAsString()
                    : "Đăng ký thất bại!";
            register_error_announcement.setText(message);
        }
    }
    @FXML
    public void Switching_to_login_scene(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Login_scene.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}