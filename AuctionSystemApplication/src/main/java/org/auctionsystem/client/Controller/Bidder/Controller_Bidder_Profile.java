package org.auctionsystem.client.Controller.Bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
    @FXML private TextField TextField_HoVaTen, TextField_TenNguoiDung, TextField_Email, TextField_SoDienThoai;
    @FXML private PasswordField PasswordField_MatKhau;
    @FXML private Button Button_ChinhSua;

    private boolean isEditing = false;

    @FXML
    private void onChinhSua(ActionEvent event) {
        isEditing = !isEditing;

        // Bật/tắt chỉnh sửa
        TextField_HoVaTen.setEditable(isEditing);
        TextField_TenNguoiDung.setEditable(isEditing);
        TextField_Email.setEditable(isEditing);
        TextField_SoDienThoai.setEditable(isEditing);
        PasswordField_MatKhau.setEditable(isEditing);

        if (isEditing) {
            Button_ChinhSua.setText("Lưu thông tin");
            // Highlight các field đang được chỉnh
            TextField_HoVaTen.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            TextField_TenNguoiDung.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            TextField_Email.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            TextField_SoDienThoai.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            PasswordField_MatKhau.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
        } else {
            Button_ChinhSua.setText("Chỉnh sửa thông tin");
            // Gửi dữ liệu lên server ở đây
            luuThongTin();
            // Reset style
            TextField_HoVaTen.setStyle("");
            TextField_TenNguoiDung.setStyle("");
            TextField_Email.setStyle("");
            TextField_SoDienThoai.setStyle("");
            PasswordField_MatKhau.setStyle("");
        }
    }

    private void luuThongTin() {
        // Gọi ServerConnection để cập nhật
    }
}
