package org.auctionsystem.Controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import javafx.event.ActionEvent;

import java.io.IOException;

public class Controller_Dashboard {
    private Scene scene;
    private Stage stage;
    private Parent root;

    @FXML
    public void switch_to_Login_scene(ActionEvent event) throws IOException {
        root = FXMLLoader.load(getClass().getResource("/org/auctionsystem/View/Login_scene.fxml"));
        stage = (Stage) ((Node)event.getSource()).getScene().getWindow();
        scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    private TextField search_bar;
    @FXML
    private ListView<String> item_list;
    private ObservableList<String> data = FXCollections.observableArrayList("Java","Python","C++");
    // Ghi tạm để chỉnh sửa sau

    @FXML
    public void initialize() {
        FilteredList<String> filtered_data = new FilteredList<>(data, b -> true);
        search_bar.textProperty().addListener(((observableValue, old_value, new_value) -> {
            filtered_data.setPredicate(item ->  {
                // Nếu thanh tìm kiếm trống, hiển thị tất cả sản phẩm
                if (new_value == null || new_value.isEmpty()) {
                    return true;
                }
                String lower_case_filter = new_value.toLowerCase();

                // Kiểm tra xem item có chứa từ khóa hay không
                if (item.toLowerCase().contains(lower_case_filter)) {
                    return true;
                }
                return false;
            });
        } ));
        item_list.setItems(filtered_data);
    }
}
