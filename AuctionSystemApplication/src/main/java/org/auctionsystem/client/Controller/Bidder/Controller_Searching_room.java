package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class Controller_Searching_room {

    @FXML private TextField                       field_search;
    @FXML private Button                          btn_search;
    @FXML private TableView<JsonObject>           tableItems;
    @FXML private TableColumn<JsonObject, String> col_name;
    @FXML private TableColumn<JsonObject, String> col_current_price;
    @FXML private TableColumn<JsonObject, String> col_time_left;
    @FXML private TableColumn<JsonObject, String> col_status;

    private final ObservableList<JsonObject> masterList   = FXCollections.observableArrayList();
    private       FilteredList<JsonObject>   filteredList;

    private static final DateTimeFormatter DT_FMT         = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String            Item_Detail_View = "/org/auctionsystem/client/View/Item_Detail.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        setupRowClickNavigation();   // ← bind double-click trực tiếp lên TableRow
        setupSearchListener();
        loadAllItems();

        // Real-time auto-refresh bảng đã bị xóa. Dữ liệu load 1 lần khi vào màn hình.
        // Người dùng có thể bấm nút refresh thủ công nếu cần.
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Double-click trên từng TableRow → navigate
    //  Cách này đáng tin cậy hơn onMouseClicked trên TableView vì:
    //  - Chỉ fire khi click đúng vào hàng có dữ liệu (không fire khi click vùng trống)
    //  - Row đã được select trước khi handler chạy
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRowClickNavigation() {
        tableItems.setRowFactory(tv -> {
            TableRow<JsonObject> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                // Chỉ xử lý double-click chuột trái trên hàng có dữ liệu
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !row.isEmpty()) {
                    JsonObject item = row.getItem();
                    if (item != null) {
                        navigateToItemDetail(item);
                    }
                }
            });
            return row;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng đến Item Detail
    // ─────────────────────────────────────────────────────────────────────────

    private void navigateToItemDetail(JsonObject item) {
        unregisterEvents();
        Controller_Item_Detail.setCurrentItem(item);
        try {
            // Dùng Scene_Utils.class.getResource để đảm bảo classpath đúng
            java.net.URL fxmlUrl = Scene_Utils.class.getResource(Item_Detail_View);
            if (fxmlUrl == null) {
                System.err.println("[SearchRoom] Không tìm thấy FXML: " + Item_Detail_View);
                return;
            }
            Parent root = FXMLLoader.load(fxmlUrl);

            Stage stage = (Stage) tableItems.getScene().getWindow();
            double w   = stage.getWidth();
            double h   = stage.getHeight();
            boolean max = stage.isMaximized();

            Scene scene = new Scene(root);
            Scene_Utils.Apply_Default_CSS_Style(scene);

            stage.setMaximized(false);
            stage.setOpacity(0);
            stage.setScene(scene);

            Platform.runLater(() -> {
                if (max) {
                    stage.setMaximized(true);
                } else {
                    stage.setWidth(w);
                    stage.setHeight(h);
                }
                stage.setOpacity(1);
                javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(250), root);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.play();
            });
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup cột
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        col_name.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().has("name")
                                ? data.getValue().get("name").getAsString() : ""));

        col_current_price.setCellValueFactory(data -> {
            JsonObject item = data.getValue();
            double price = item.has("currentHighestPrice") && !item.get("currentHighestPrice").isJsonNull()
                    ? item.get("currentHighestPrice").getAsDouble()
                    : (item.has("startingPrice") ? item.get("startingPrice").getAsDouble() : 0);
            return new SimpleStringProperty(String.format("%,.0f ₫", price));
        });

        col_time_left.setCellValueFactory(data -> {
            JsonObject item = data.getValue();
            if (!item.has("endTime") || item.get("endTime").isJsonNull())
                return new SimpleStringProperty("—");
            try {
                LocalDateTime endTime = LocalDateTime.parse(
                        item.get("endTime").getAsString().replace(" ", "T"));
                long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), endTime);
                if (minutes < 0)  return new SimpleStringProperty("Đã kết thúc");
                if (minutes < 60) return new SimpleStringProperty(minutes + " phút");
                return new SimpleStringProperty((minutes / 60) + " giờ " + (minutes % 60) + " phút");
            } catch (Exception e) {
                return new SimpleStringProperty(item.get("endTime").getAsString());
            }
        });

        col_status.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().has("status")
                                ? data.getValue().get("status").getAsString() : ""));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Listener thanh tìm kiếm
    // ─────────────────────────────────────────────────────────────────────────

    private void setupSearchListener() {
        filteredList = new FilteredList<>(masterList, item -> true);
        tableItems.setItems(filteredList);

        field_search.textProperty().addListener((obs, oldVal, newVal) -> {
            String keyword = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredList.setPredicate(item -> {
                if (keyword.isEmpty()) return true;
                String name = item.has("name") ? item.get("name").getAsString().toLowerCase() : "";
                return name.contains(keyword);
            });
        });

        btn_search.setOnAction(this::onSearchButton);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load dữ liệu
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAllItems() {
        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action", "GET_VISIBLE_ITEMS");
            JsonObject response = ServerConnection.sendAuthRequest(request);
            Platform.runLater(() -> populateMasterList(response));
        }, "SearchRoom-LoadAll").start();
    }

    private void onSearchButton(ActionEvent event) {
        String keyword = field_search.getText().trim();
        if (keyword.isEmpty()) { loadAllItems(); return; }

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action",  "SEARCH_ITEMS");
            request.addProperty("keyword", keyword);
            JsonObject response = ServerConnection.sendAuthRequest(request);
            Platform.runLater(() -> populateMasterList(response));
        }, "SearchRoom-Search").start();
    }

    private void populateMasterList(JsonObject response) {
        masterList.clear();
        if (response != null && "success".equals(response.get("status").getAsString())) {
            JsonArray arr = response.get("message").getAsJsonArray();
            for (JsonElement el : arr) masterList.add(el.getAsJsonObject());
        }
        tableItems.refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time
    // ─────────────────────────────────────────────────────────────────────────

    private void onBidPlaced(JsonObject payload) {
        if (!payload.has("item_id")) return;
        String itemId = payload.get("item_id").getAsString();
        double amount = payload.has("bid_amount") ? payload.get("bid_amount").getAsDouble() : 0;
        for (JsonObject item : masterList) {
            if (itemId.equals(item.has("id") ? item.get("id").getAsString() : "")) {
                item.addProperty("currentHighestPrice", amount);
                break;
            }
        }
        tableItems.refresh();
    }

    private void onEndTimeExtended(JsonObject payload) {
        if (!payload.has("item_id") || !payload.has("new_end_time")) return;
        String itemId     = payload.get("item_id").getAsString();
        String newEndTime = payload.get("new_end_time").getAsString();
        for (JsonObject item : masterList) {
            if (itemId.equals(item.has("id") ? item.get("id").getAsString() : "")) {
                item.addProperty("endTime", newEndTime);
                break;
            }
        }
        tableItems.refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FXML handlers giữ lại để tương thích
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void Go_to_item_detail(ActionEvent event) {
        JsonObject selected = tableItems.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Chọn sản phẩm", "Vui lòng chọn một sản phẩm để xem chi tiết.");
            return;
        }
        navigateToItemDetail(selected);
    }

    @FXML
    public void Go_to_bidding_room(ActionEvent event) {
        Go_to_item_detail(event);
    }

    @FXML
    public void Go_to_search_user(ActionEvent event) {
        unregisterEvents();
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Search_User.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void Back_to_Dashboard(ActionEvent event) {
        unregisterEvents();
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void unregisterEvents() {
        // Không còn đăng ký events nào trong màn hình này
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}