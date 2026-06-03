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
import javafx.scene.control.*;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Bidding_History {

    @FXML private TableView<JsonObject>           tableBiddingHistory;
    @FXML private TableColumn<JsonObject, String> col_item_name;
    @FXML private TableColumn<JsonObject, String> col_bid_price;
    @FXML private TableColumn<JsonObject, String> col_bid_time;

    private final ObservableList<JsonObject> masterList = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        loadData();

        // Real-time auto-refresh bảng đã được xóa. Dữ liệu load 1 lần khi vào màn hình.
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup cột
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        // Tên sản phẩm
        col_item_name.setCellValueFactory(data -> {
            JsonObject row = data.getValue();
            String name = row.has("itemName") && !row.get("itemName").isJsonNull()
                    ? row.get("itemName").getAsString()
                    : (row.has("itemId") ? row.get("itemId").getAsString() : "");
            return new SimpleStringProperty(name);
        });

        // Giá đã đặt
        col_bid_price.setCellValueFactory(data -> {
            JsonObject row = data.getValue();
            String val = row.has("bidAmount")
                    ? String.format("%,.0f ₫", row.get("bidAmount").getAsDouble()) : "";
            return new SimpleStringProperty(val);
        });

        // Thời gian đặt
        col_bid_time.setCellValueFactory(data -> {
            JsonObject row = data.getValue();
            String val = row.has("bidTime") ? row.get("bidTime").getAsString() : "";
            return new SimpleStringProperty(val);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load dữ liệu — GET_BIDS_BY_BIDDER
    // ─────────────────────────────────────────────────────────────────────────

    private void loadData() {
        String userId = UserSession.getInstance().getUserId();

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action",    "GET_BIDS_BY_BIDDER");
            request.addProperty("bidder_id", userId);
            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                masterList.clear();
                if (response != null && "success".equals(response.get("status").getAsString())) {
                    JsonArray arr = response.get("message").getAsJsonArray();
                    for (JsonElement el : arr) masterList.add(el.getAsJsonObject());
                }
                tableBiddingHistory.setItems(masterList);
            });
        }, "BiddingHistory-Load").start();
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String getStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
