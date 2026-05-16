package org.auctionsystem.client.Controller.Seller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;

public class Controller_Seller_Dashboard {
    private static final String Login_View = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Profile_View = "/org/auctionsystem/client/View/Seller_Profile.fxml";
    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            org.auctionsystem.client.Controller.Scene_Utils.Change_Scene(event, fxml_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @FXML //Đến trang hồ sơ
    public void Go_to_profile(ActionEvent event) {
        switch_scene(event, Profile_View);
    }

    @FXML //Nếu đăng xuất ra khỏi tài khoản
    public void Logging_out(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn chuẩn bị đăng xuất khỏi tài khoản này.");
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                switch_scene(event, Login_View);
            }
        });
    }
}
