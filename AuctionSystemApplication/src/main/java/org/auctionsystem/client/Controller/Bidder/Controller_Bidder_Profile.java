package org.auctionsystem.client.Controller.Bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Bidder_Profile {

    @FXML private TextField     field_name;
    @FXML private TextField     field_username;
    @FXML private PasswordField field_password;
    @FXML private TextField     field_email;
    @FXML private TextField     field_phone;

    @FXML
    public void initialize() {
        // Đọc trực tiếp từ UserSession — dữ liệu đã được cập nhật
        // trong Go_to_profile() trước khi chuyển sang màn hình này
        // Thứ tự khớp FXML: name → username → password → email → phone
        UserSession s = UserSession.getInstance();
        field_name    .setText(s.getName());
        field_username.setText(s.getUsername());
        field_password.setText("");                                         // không hiển thị mật khẩu
        field_email   .setText(s.getEmail());
        field_phone   .setText(s.getPhone() != null ? s.getPhone() : ""); // nullable
    }

    @FXML
    public void back_to_bidder_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}