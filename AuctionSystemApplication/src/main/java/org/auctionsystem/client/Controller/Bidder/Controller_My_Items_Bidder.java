package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_My_Items_Bidder {

    // fx:id khớp với My_Items_Bidder.fxml
    @FXML private TableView<JsonObject>           tableMyItems;
    @FXML private TableColumn<JsonObject, String> col_name;
    @FXML private TableColumn<JsonObject, String> col_start_price;
    @FXML private TableColumn<JsonObject, String> col_current_price;
    @FXML private TableColumn<JsonObject, String> col_end_time;
    @FXML private TableColumn<JsonObject, String> col_status;

    private final ObservableList<JsonObject> masterList = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        loadData();

        // Real-time auto-update trạng thái bảng đã được xóa. Dữ liệu load 1 lần khi vào màn hình.
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup cột
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        col_name.setCellValueFactory(data ->
                new SimpleStringProperty(getString(data.getValue(), "name")));

        col_start_price.setCellValueFactory(data ->
                new SimpleStringProperty(money(data.getValue(), "startingPrice")));

        col_current_price.setCellValueFactory(data -> {
            JsonObject item = data.getValue();
            boolean hasHighest = item.has("currentHighestPrice")
                    && !item.get("currentHighestPrice").isJsonNull();
            String val = hasHighest
                    ? String.format("%,.0f ₫", item.get("currentHighestPrice").getAsDouble())
                    : "—";
            return new SimpleStringProperty(val);
        });

        col_end_time.setCellValueFactory(data ->
                new SimpleStringProperty(getString(data.getValue(), "endTime")));

        col_status.setCellValueFactory(data ->
                new SimpleStringProperty(translateStatus(getString(data.getValue(), "status"))));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load dữ liệu — GET_ITEMS_BY_OWNER (sản phẩm mà bidder đang sở hữu)
    // ─────────────────────────────────────────────────────────────────────────

    private void loadData() {
        String userId = UserSession.getInstance().getUserId();

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action",   "GET_ITEMS_BY_OWNER");
            request.addProperty("owner_id", userId);
            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                masterList.clear();
                if (response != null && "success".equals(response.get("status").getAsString())) {
                    JsonArray arr = response.get("message").getAsJsonArray();
                    for (JsonElement el : arr) masterList.add(el.getAsJsonObject());
                }
                tableMyItems.setItems(masterList);
            });
        }, "MyItemsBidder-Load").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time: cập nhật status item tại chỗ (không reload toàn bộ)
    // ─────────────────────────────────────────────────────────────────────────

    private void updateItemStatus(JsonObject payload, String newStatus) {
        if (!payload.has("item_id")) return;
        String itemId = payload.get("item_id").getAsString();
        Platform.runLater(() -> {
            for (JsonObject item : masterList) {
                if (itemId.equals(item.has("id") ? item.get("id").getAsString() : "")) {
                    item.addProperty("status", newStatus);
                    tableMyItems.refresh();
                    break;
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tiện ích
    // ─────────────────────────────────────────────────────────────────────────

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : "";
    }

    private String money(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return String.format("%,.0f ₫", obj.get(key).getAsDouble());
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        return switch (status.toUpperCase()) {
            case "PENDING"   -> "Chờ duyệt";
            case "APPROVED"  -> "Đã duyệt";
            case "ACTIVE"    -> "Đang đấu giá";
            case "ENDED"     -> "Đã kết thúc";
            case "CLOSED"    -> "Đã đóng";
            case "CANCELLED" -> "Đã huỷ";
            default          -> status;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void back_to_bidding_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
