package org.auctionsystem.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;

public class Controller_Login {
    @FXML
    private PasswordField log_in_password;
    @FXML
    private TextField log_in_username;
    @FXML
    private Label log_in_password_error;
    @FXML
    private Label log_in_username_error;
    @FXML
    private void Log_in_condition(ActionEvent event) {
        String username = log_in_username.getText().trim();
        String password = log_in_password.getText();
        if (username.isEmpty()) {
            log_in_username_error.setText("Hãy đặt tên người dùng!");
        } else if (password.length() < 8) {
            log_in_password_error.setText("Mật khẩu phải có ít nhất 8 ký tự!");
            log_in_username_error.setText("");
        } else {
            log_in_username_error.setText("");
            log_in_password_error.setText("");
            try {
                Scene_Utils.Change_Scene(event, "/org/auctionsystem/View/Bidder_Dashboard.fxml");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    @FXML
    private void Switching_to_register_scene(ActionEvent event) throws IOException {
        Scene_Utils.Change_Scene(event,"/org/auctionsystem/View/Register_scene.fxml");
    }
}
