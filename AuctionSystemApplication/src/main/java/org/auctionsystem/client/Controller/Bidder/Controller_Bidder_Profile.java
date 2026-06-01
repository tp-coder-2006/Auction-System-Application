package org.auctionsystem.client.Controller.Bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Bidder_Profile {

    @FXML private TextField field_name;
    @FXML private TextField field_username;
    @FXML private TextField field_email;
    @FXML private TextField field_phone;
    @FXML private Circle        avatarCircle;
    @FXML private Button        Button_ChinhSua;

    @FXML
    public void initialize() {
        // Đọc trực tiếp từ UserSession — dữ liệu đã được cập nhật
        // trong Go_to_profile() trước khi chuyển sang màn hình này
        // Thứ tự khớp FXML: name → username → password → email → phone
        UserSession s = UserSession.getInstance();
        field_name.setText(s.getName());
        field_username.setText(s.getUsername());
        field_email.setText(s.getEmail());
        field_phone.setText(s.getPhone() != null ? s.getPhone() : ""); // nullable
    }

    @FXML
    public void back_to_bidder_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isEditing = false;

    @FXML
    private void onChinhSua(ActionEvent event) {
        isEditing = !isEditing;

        // Bật/tắt chỉnh sửa
        field_name.    setEditable(isEditing);
        field_username.setEditable(isEditing);
        field_email.   setEditable(isEditing);
        field_phone.   setEditable(isEditing);

        // Chuỗi CSS giúp đổi màu viền xanh cyan bắt mắt khi đang ở chế độ sửa
        String activeStyle = "-fx-background-color: white; -fx-border-color: #029ef2; -fx-border-radius: 5px; -fx-background-radius: 5px;";

        // Chuỗi CSS mặc định (Màu xám nhạt như file CSS gốc để giữ nguyên bo góc)
        String defaultStyle = "-fx-background-color: white; -fx-border-color: #dcdde1; -fx-border-radius: 5px; -fx-background-radius: 5px;";

        if (isEditing) {
            Button_ChinhSua.setText("Lưu thông tin");
            // Highlight các field đang được chỉnh
            field_name.    setStyle(activeStyle);
            field_username.setStyle(activeStyle);
            field_email.   setStyle(activeStyle);
            field_phone.   setStyle(activeStyle);
        } else {
            Button_ChinhSua.setText("Chỉnh sửa thông tin");
            // Gửi dữ liệu lên server ở đây
            luuThongTin();
            // Reset style
            field_name.    setStyle(defaultStyle);
            field_username.setStyle(defaultStyle);
            field_email.   setStyle(defaultStyle);
            field_phone.   setStyle(defaultStyle);
        }
    }

    private void luuThongTin() {
        // Gọi ServerConnection để cập nhật
    }
    @FXML
    public void changing_password(ActionEvent event) {
        try {
            Scene_Utils.Open_Dialog(event,"/org/auctionsystem/client/View/Change_Password.fxml","Đổi mật khẩu");
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
