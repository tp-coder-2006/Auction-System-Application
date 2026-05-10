package org.auctionsystem.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Main extends Application {
    public void start(Stage stage) throws IOException {
        // Đường dẫn đúng sau khi chuyển FXML vào thư mục client/View
        Parent root = FXMLLoader.load(getClass().getResource("/org/auctionsystem/client/View/Login_scene.fxml"));
        Scene scene = new Scene(root);
        Scene_Utils.Apply_Default_CSS_Style(scene);

        stage.setTitle("Auction System Application");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
