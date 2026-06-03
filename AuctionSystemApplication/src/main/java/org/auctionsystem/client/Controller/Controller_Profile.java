package org.auctionsystem.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Profile {

    @FXML private TextField     field_username;
    @FXML private TextField     field_email;
    @FXML private PasswordField field_password;
    @FXML private TextField     field_phone;
    @FXML private Circle        avatarCircle;
    @FXML private Button        btn_edit;

    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        field_username.setText(s.getUsername());
        field_email.setText(s.getEmail());
        field_phone.setText(s.getPhone() != null ? s.getPhone() : "");

        field_username.setEditable(false);
        field_email.setEditable(false);
        field_password.setEditable(false);
        field_phone.setEditable(false);
    }

    @FXML
    public void btn_edit(ActionEvent event) {
        // TODO: xử lý chỉnh sửa thông tin
    }
}
