package org.auctionsystem.client.Controller;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;

public class Scene_Utils {

    // Lưu primaryStage — set 1 lần duy nhất trong Main.start()
    private static Stage primaryStage;

    // Lưu kích thước stage gốc 1 lần duy nhất khi app khởi động
    private static double initialWidth  = -1;
    private static double initialHeight = -1;

    /** Gọi 1 lần duy nhất trong Main.start() trước stage.show() */
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    /** Lấy primaryStage — dùng trong BanWatcher hoặc bất kỳ nơi nào cần Stage mà không có ActionEvent */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /** Gọi 1 lần trong Main.java sau stage.show() */
    public static void Init_Stage_Size(Stage stage) {
        initialWidth  = stage.getWidth();
        initialHeight = stage.getHeight();
    }

    @FXML
    public static void Change_Scene(ActionEvent event, String fxml_path) throws IOException {
        Parent root = FXMLLoader.load(Objects.requireNonNull(Scene_Utils.class.getResource(fxml_path)));
        Stage stage;

        if (event.getSource() instanceof Node) {
            stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        } else if (event.getSource() instanceof MenuItem) {
            stage = (Stage) ((MenuItem) event.getSource()).getParentPopup().getOwnerWindow();
        } else {
            throw new IllegalArgumentException();
        }

        boolean wasMaximized = stage.isMaximized();
        double targetWidth   = stage.getWidth();
        double targetHeight  = stage.getHeight();

        Scene scene = new Scene(root);
        Apply_Default_CSS_Style(scene);

        stage.setMaximized(false);
        stage.setOpacity(0);
        stage.setScene(scene);

        Platform.runLater(() -> {
            if (wasMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(targetWidth);
                stage.setHeight(targetHeight);
            }
            stage.setOpacity(1);
            FadeTransition fade = new FadeTransition(Duration.millis(250), root);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });
        stage.show();
    }

    @FXML
    public static void set_up_search_logic(TextField search_bar, ListView<String> item_list, ObservableList<String> data) {
        FilteredList<String> filtered_data = new FilteredList<>(data, p -> true);
        search_bar.textProperty().addListener((observable, old_value, new_value) -> {
            filtered_data.setPredicate(item -> {
                if (new_value == null || new_value.isBlank()) return true;
                return item.toLowerCase().contains(new_value.toLowerCase());
            });
            boolean hasText = new_value != null && !new_value.isBlank();
            item_list.setVisible(hasText);
            item_list.setManaged(hasText);
        });
        item_list.setItems(filtered_data);
    }

    public static void Apply_Default_CSS_Style(Scene scene) {
        if (scene == null) return;
        String default_css_path = "/org/auctionsystem/CSS/style.css";
        java.net.URL css_resource = Scene_Utils.class.getResource(default_css_path);
        if (css_resource != null) {
            String css_url_string = css_resource.toExternalForm();
            if (!scene.getStylesheets().contains(css_url_string)) {
                scene.getStylesheets().add(css_url_string);
            }
        } else {
            System.err.println("⚠️ Không tìm thấy file CSS tại: " + default_css_path);
        }
    }
}