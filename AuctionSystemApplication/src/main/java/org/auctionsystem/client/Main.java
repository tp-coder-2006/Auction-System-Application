package org.auctionsystem.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Main extends Application {
    public void start(Stage stage) throws IOException {
        Scene_Utils.setPrimaryStage(stage);

        stage.setOnCloseRequest(event -> {
            Scene_Utils.stopSessionChecker();
            Platform.exit();
            System.exit(0); // đảm bảo JVM tắt hẳn
        });

        Parent root = FXMLLoader.load(getClass().getResource("/org/auctionsystem/client/View/Login_scene.fxml"));
        Scene scene = new Scene(root);

        stage.setTitle("Auction System Application");
        stage.setScene(scene);
        stage.show();
        Scene_Utils.Init_Stage_Size(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
