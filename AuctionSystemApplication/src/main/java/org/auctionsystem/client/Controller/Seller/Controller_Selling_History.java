package org.auctionsystem.client.Controller.Seller;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Controller_Selling_History {

    // fx:id khớp Selling_History.fxml
    @FXML private TableView<JsonObject>           tableSellingHistory;
    @FXML private TableColumn<JsonObject, String> col_name;
    @FXML private TableColumn<JsonObject, String> col_buyer;
    @FXML private TableColumn<JsonObject, String> col_sold_price;
    @FXML private TableColumn<JsonObject, String> col_sold_time;

    private final ObservableList<JsonObject> historyList = FXCollections.observableArrayList();

    private static final DateTimeFormatter DT_FMT     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        loadHistory();

        // Real-time auto-refresh bảng đã được xóa. Dữ liệu load 1 lần khi vào màn hình.
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup cột — dữ liệu từ ItemHistory entity
    //  fields: itemId, itemName, sellerId, buyerId, buyerName, soldPrice, soldTime
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        // Tên sản phẩm — ưu tiên itemName, fallback itemId
        col_name.setCellValueFactory(d -> {
            JsonObject row = d.getValue();
            String name = row.has("itemName") && !row.get("itemName").isJsonNull()
                    ? row.get("itemName").getAsString() : "";
            String id   = row.has("itemId") && !row.get("itemId").isJsonNull()
                    ? row.get("itemId").getAsString() : "";
            return new SimpleStringProperty(name.isBlank() ? id : name);
        });

        // Người mua — ưu tiên buyerName (username), fallback buyerId
        col_buyer.setCellValueFactory(d -> {
            JsonObject row = d.getValue();
            String name = row.has("buyerName") && !row.get("buyerName").isJsonNull()
                    ? row.get("buyerName").getAsString() : "";
            String id   = row.has("buyerId") && !row.get("buyerId").isJsonNull()
                    ? row.get("buyerId").getAsString() : "";
            return new SimpleStringProperty(name.isBlank() ? id : name);
        });

        col_sold_price.setCellValueFactory(d -> {
            JsonObject row = d.getValue();
            if (!row.has("soldPrice") || row.get("soldPrice").isJsonNull())
                return new SimpleStringProperty("—");
            return new SimpleStringProperty(
                    String.format("%,.0f ₫", row.get("soldPrice").getAsDouble()));
        });

        col_sold_time.setCellValueFactory(d -> {
            JsonObject row = d.getValue();
            String raw = row.has("soldTime") && !row.get("soldTime").isJsonNull()
                    ? row.get("soldTime").getAsString() : "";
            if (raw.isBlank()) return new SimpleStringProperty("—");
            try {
                LocalDateTime dt = LocalDateTime.parse(raw.replace("T", " "), DT_FMT);
                return new SimpleStringProperty(dt.format(DT_DISPLAY));
            } catch (Exception e) {
                return new SimpleStringProperty(raw);
            }
        });

        tableSellingHistory.setItems(historyList);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load — GET_HISTORY_BY_SELLER
    // ─────────────────────────────────────────────────────────────────────────

    private void loadHistory() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",    "GET_HISTORY_BY_SELLER");
            req.addProperty("seller_id", UserSession.getInstance().getUserId());
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                historyList.clear();
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    JsonArray arr = res.get("message").getAsJsonArray();
                    for (JsonElement el : arr) historyList.add(el.getAsJsonObject());
                }
            });
        }, "SellingHistory-Load").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void back_to_seller_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Seller_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}