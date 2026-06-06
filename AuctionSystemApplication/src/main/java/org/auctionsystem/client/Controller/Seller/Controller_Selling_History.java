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
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
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

    // UUID duy nhất cho instance này
    private final String handlerKey = java.util.UUID.randomUUID().toString();

    private static final DateTimeFormatter DT_FMT     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        loadHistory();
        registerEvents();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time — lắng nghe AUCTION_SETTLED
    // ─────────────────────────────────────────────────────────────────────────

    private void registerEvents() {
        EventDispatcher.registerGlobal(EventType.AUCTION_SETTLED, handlerKey, this::onAuctionSettled);
    }

    private void unregisterEvents() {
        EventDispatcher.unregisterGlobal(EventType.AUCTION_SETTLED, handlerKey);
    }

    /**
     * Khi phiên đấu giá kết thúc, server broadcast AUCTION_SETTLED.
     * Nếu seller_id trùng user hiện tại → thêm dòng vào đầu bảng ngay lập tức.
     *
     * Payload: item_id, item_name, seller_id, bidder_id, amount
     */
    private void onAuctionSettled(JsonObject payload) {
        String myId    = UserSession.getInstance().getUserId();
        String sellerId = getStr(payload, "seller_id");

        // Chỉ xử lý phiên đấu giá của chính mình
        if (!myId.equals(sellerId)) return;

        // Xây dựng JsonObject theo cùng cấu trúc trả về bởi GET_HISTORY_BY_SELLER
        JsonObject row = new JsonObject();
        row.addProperty("itemId",    getStr(payload, "item_id"));
        row.addProperty("itemName",  getStr(payload, "item_name"));
        row.addProperty("sellerId",  sellerId);
        row.addProperty("buyerId",   getStr(payload, "bidder_id"));
        // bidder_name không có trong payload AUCTION_SETTLED — để trống, fallback về buyerId
        row.addProperty("buyerName", "");
        row.addProperty("soldPrice", payload.has("amount")
                ? payload.get("amount").getAsDouble() : 0.0);
        row.addProperty("soldTime",
                LocalDateTime.now().format(DT_FMT));

        // Thêm vào đầu danh sách (phiên mới nhất hiện lên trên)
        historyList.add(0, row);
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
        unregisterEvents();
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Seller_Dashboard.fxml");
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
}
