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
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Bidding_History {

    @FXML private TableView<JsonObject>           tableBiddingHistory;
    @FXML private TableColumn<JsonObject, String> col_item_name;
    @FXML private TableColumn<JsonObject, String> col_bid_price;
    @FXML private TableColumn<JsonObject, String> col_bid_time;

    private final ObservableList<JsonObject> masterList = FXCollections.observableArrayList();

    // UUID duy nhất cho instance này — tránh đụng handler của màn hình khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        loadData();
        registerEvents();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time — lắng nghe BID_PLACED
    // ─────────────────────────────────────────────────────────────────────────

    private void registerEvents() {
        EventDispatcher.registerGlobal(EventType.BID_PLACED, handlerKey, this::onBidPlaced);
    }

    private void unregisterEvents() {
        EventDispatcher.unregisterGlobal(EventType.BID_PLACED, handlerKey);
    }

    /**
     * Khi có bid mới được đặt thành công, server broadcast BID_PLACED.
     * Nếu bidder_id trùng user hiện tại → thêm dòng vào đầu bảng ngay lập tức.
     *
     * Payload: item_id, item_name, bidder_id, bid_amount, bid_time
     */
    private void onBidPlaced(JsonObject payload) {
        String myId     = UserSession.getInstance().getUserId();
        String bidderId = getStr(payload, "bidder_id");

        // Chỉ xử lý bid của chính mình
        if (!myId.equals(bidderId)) return;

        // Xây dựng JsonObject theo cùng cấu trúc trả về bởi GET_BIDS_BY_BIDDER
        JsonObject row = new JsonObject();
        row.addProperty("itemId",     getStr(payload, "item_id"));
        row.addProperty("itemName",   getStr(payload, "item_name"));
        row.addProperty("bidAmount",  payload.has("bid_amount")
                ? payload.get("bid_amount").getAsDouble() : 0.0);
        row.addProperty("bidTime",    getStr(payload, "bid_time"));

        // Thêm vào đầu danh sách (bid mới nhất hiện lên trên)
        Platform.runLater(() -> masterList.add(0, row));
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
        unregisterEvents();
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
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
