package org.auctionsystem.client.Controller.Admin;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;

public class Controller_Admin_Dashboard {
    @FXML private VBox sidebar;
    private boolean isMenuExpanded = true;
    private final double expanded_width = 190.0;

    @FXML
    private void toggleMenu() {
        Timeline timeline = new Timeline();
        KeyValue keyValue;
        if (isMenuExpanded) {
            //Nếu sidebar đang mở rộng -> thu gọn sidebar
            keyValue = new KeyValue(sidebar.prefWidthProperty(), 0);
            sidebar.getChildren().forEach(node -> node.setVisible(false));
        } else {
            //Nếu sidebar đang đóng -> mở rộng sidebar
            keyValue = new KeyValue(sidebar.prefWidthProperty(), expanded_width);
            timeline.setOnFinished(event -> sidebar.getChildren().forEach(
                    node -> node.setVisible(true)));
        }
        KeyFrame keyFrame = new KeyFrame(Duration.millis(300), keyValue);
        timeline.getKeyFrames().add(keyFrame);

        //Đảo ngược trạng thái menu sau khi bấm
        if (isMenuExpanded) {
            timeline.setOnFinished(event -> isMenuExpanded = false);
        } else {
            isMenuExpanded = true;
        }
        timeline.play();
    }

    private static final String Admin_User_Management_View = "/org/auctionsystem/client/View/Admin_User_Management.fxml";
    private static final String Login_View = "/org/auctionsystem/client/View/Login_scene.fxml";

    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            org.auctionsystem.client.Controller.Scene_Utils.Change_Scene(event, fxml_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @FXML
    public void Go_to_admin_user_management(ActionEvent event) {
        switch_scene(event, Admin_User_Management_View);
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
