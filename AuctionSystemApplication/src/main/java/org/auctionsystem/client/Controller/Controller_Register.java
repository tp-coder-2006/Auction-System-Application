package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Connectivity.ServerConnection;

import java.io.IOException;

public class Controller_Register {
    @FXML private RadioButton   register_as_bidder;
    @FXML private RadioButton   register_as_seller;
    @FXML private TextField     register_name;
    @FXML private TextField     register_username;
    @FXML private TextField     register_email;
    @FXML private PasswordField register_password;
    @FXML private Label         register_error_announcement;

    @FXML
    public void Registering(ActionEvent event) {
        clearError();

        // Lấy dữ liệu từ form
        String name     = register_name.getText().trim();
        String username = register_username.getText().trim();
        String email    = register_email.getText().trim();
        String password = register_password.getText();

        // Validate phía client
        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            setError("Vui lòng điền đầy đủ thông tin!");
            return;
        }

        if (!register_as_bidder.isSelected() && !register_as_seller.isSelected()) {
            setError("Vui lòng chọn vai trò (Bidder hoặc Seller)!");
            return;
        }

        // Validate password cơ bản — server sẽ validate chi tiết hơn
        if (password.length() < 8) {
            setError("Mật khẩu phải có ít nhất 8 ký tự!");
            return;
        }

        String role = register_as_bidder.isSelected() ? "bidder" : "seller";

        // Gửi request lên server
        JsonObject request = new JsonObject();
        request.addProperty("action",   "REGISTER");
        request.addProperty("name",     name);
        request.addProperty("username", username);
        request.addProperty("email",    email);
        request.addProperty("password", password);
        request.addProperty("role",     role);

        JsonObject response = ServerConnection.sendRequest(request);

        if (response == null) {
            setError("Không thể kết nối tới Server!");
            return;
        }

        String status = response.get("status").getAsString();
        if ("success".equals(status)) {
            // Đăng ký thành công → về trang Login
            try {
                Scene_Utils.Change_Scene(event,
                        "/org/auctionsystem/client/View/Login_scene.fxml");
            } catch (IOException e) {
                setError("Lỗi: Không tìm thấy file giao diện!");
            }
        } else {
            // Hiển thị lỗi từ server (validate password, trùng username/email...)
            setError(response.has("message")
                    ? response.get("message").getAsString()
                    : "Đăng ký thất bại!");
        }
    }

    @FXML
    public void Switching_to_login_scene(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event,
                    "/org/auctionsystem/client/View/Login_scene.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setError(String message) {
        register_error_announcement.setText(message);
    }

    private void clearError() {
        register_error_announcement.setText("");
    }
}