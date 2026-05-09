package org.auctionsystem.client.Controller;

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

import java.io.IOException;
import java.util.Objects;

public class Scene_Utils {
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

        Scene scene = new Scene(root);

        Apply_Default_CSS_Style(scene);

        stage.setScene(scene);
        stage.show();
    }

    @FXML
    public static void set_up_search_logic(TextField search_bar, ListView<String> item_list, ObservableList<String> data) {
        FilteredList<String> filtered_data = new FilteredList<>(data, p -> true);
        search_bar.textProperty().addListener((observable,old_value,new_value) -> {
            filtered_data.setPredicate(item -> {
                if (new_value == null || new_value.isBlank()) {
                    return true;
                }
                return item.toLowerCase().contains(new_value.toLowerCase());
            });
            if (new_value == null || new_value.isBlank()) {
                item_list.setVisible(false);
                item_list.setManaged(false);
            } else {
                item_list.setVisible(true);
                item_list.setManaged(true);
            }
        });
        item_list.setItems(filtered_data);
    }
    public static void Apply_Default_CSS_Style(Scene scene) {
        if (scene == null) {
            return;
        }
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

