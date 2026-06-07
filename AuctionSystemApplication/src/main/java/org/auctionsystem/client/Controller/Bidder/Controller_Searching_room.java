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
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller_Searching_room
 *
 * Cập nhật dữ liệu theo 2 lớp:
 *
 * Lớp 1 — Real-time (tức thì):
 *   Lắng nghe event từ server qua EventDispatcher:
 *   - BID_PLACED      → cập nhật giá cao nhất của item ngay lập tức
 *   - END_TIME_EXTENDED → cập nhật endTime của item ngay lập tức
 *   - ITEM_STARTED    → cập nhật status PENDING → ACTIVE ngay lập tức
 *   - AUCTION_SETTLED → cập nhật status ACTIVE → CLOSED ngay lập tức
 *   - ITEM_CANCELLED  → cập nhật status → CANCELLED ngay lập tức
 *
 * Lớp 2 — Background (safety net):
 *   - Mỗi 1 giây: tableItems.refresh() để cột đếm ngược tính lại
 *   - Mỗi 30 giây: UPDATE_ITEM_STATUS + GET_VISIBLE_ITEMS từ DB
 *     (đảm bảo đồng bộ khi bỏ lỡ event hoặc có thay đổi khác)
 */
public class Controller_Searching_room {
    // UUID duy nhất cho mỗi instance — tránh ghi đè handler của cửa sổ khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    @FXML private TextField                       field_search;
    @FXML private Button                          btn_search;
    @FXML private TableView<JsonObject>           tableItems;
    @FXML private TableColumn<JsonObject, String> col_name;
    @FXML private TableColumn<JsonObject, String> col_current_price;
    @FXML private TableColumn<JsonObject, String> col_time_left;
    @FXML private TableColumn<JsonObject, String> col_status;

    private final ObservableList<JsonObject> masterList   = FXCollections.observableArrayList();
    private       FilteredList<JsonObject>   filteredList;

    private ScheduledExecutorService backgroundExecutor;
    private volatile int             reloadCountdown = 30;

    private static final DateTimeFormatter DT_FMT           = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String            ITEM_DETAIL_VIEW  = "/org/auctionsystem/client/View/Item_Detail.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        setupRowClickNavigation();
        setupSearchListener();
        loadFromDatabase();     // load ngay khi vào màn hình
        registerEvents();       // lắng nghe real-time từ server
        startBackgroundWorker();// refresh đếm ngược + reload DB định kỳ
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lớp 1: Real-time — cập nhật masterList tại chỗ khi nhận event
    // ─────────────────────────────────────────────────────────────────────────

    private void registerEvents() {
        EventDispatcher.registerGlobal(EventType.BID_PLACED, handlerKey, this::onBidPlaced);
        EventDispatcher.registerGlobal(EventType.END_TIME_EXTENDED, handlerKey, this::onEndTimeExtended);
        EventDispatcher.registerGlobal(EventType.ITEM_STARTED, handlerKey, this::onItemStarted);
        EventDispatcher.registerGlobal(EventType.AUCTION_SETTLED, handlerKey, this::onAuctionSettled);
        EventDispatcher.registerGlobal(EventType.ITEM_CANCELLED, handlerKey, this::onItemCancelled);
        EventDispatcher.registerGlobal(EventType.ITEM_RELISTED, handlerKey, this::onItemRelisted);
        EventDispatcher.registerGlobal(EventType.ITEM_DELETED, handlerKey, this::onItemDeleted);
        EventDispatcher.registerGlobal(EventType.ITEM_ADDED, handlerKey, this::onItemAdded);
        EventDispatcher.registerGlobal(EventType.ITEM_UPDATED, handlerKey, this::onItemUpdated);
    }

    private void unregisterEvents() {
        EventDispatcher.unregisterGlobal(EventType.BID_PLACED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.END_TIME_EXTENDED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_STARTED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.AUCTION_SETTLED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_CANCELLED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_RELISTED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_DELETED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_ADDED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_UPDATED, handlerKey);
    }

    /** BID_PLACED → cập nhật giá cao nhất tức thì */
    private void onBidPlaced(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        double amount = payload.has("bid_amount") ? payload.get("bid_amount").getAsDouble() : 0;
        updateItem(itemId, item -> item.addProperty("currentHighestPrice", amount));
    }

    /** END_TIME_EXTENDED → cập nhật endTime tức thì */
    private void onEndTimeExtended(JsonObject payload) {
        String itemId   = str(payload, "item_id");
        String newEnd   = str(payload, "new_end_time");
        if (itemId.isEmpty() || newEnd.isEmpty()) return;
        updateItem(itemId, item -> item.addProperty("endTime", newEnd));
    }

    /** ITEM_STARTED → PENDING → ACTIVE tức thì */
    private void onItemStarted(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        updateItem(itemId, item -> {
            item.addProperty("status", "ACTIVE");
            if (payload.has("end_time"))       item.addProperty("endTime",  str(payload, "end_time"));
            if (payload.has("name") && !payload.get("name").isJsonNull()) item.addProperty("name", str(payload, "name"));
        });
    }

    /** AUCTION_SETTLED → ACTIVE → CLOSED tức thì */
    private void onAuctionSettled(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        updateItem(itemId, item -> item.addProperty("status", "CLOSED"));
    }

    /** ITEM_CANCELLED → CANCELLED tức thì */
    private void onItemCancelled(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        updateItem(itemId, item -> item.addProperty("status", "CANCELLED"));
    }

    /** ITEM_RELISTED → seller đăng lại item CANCELLED → PENDING, cập nhật giá + thời gian tức thì */
    private void onItemRelisted(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        updateItem(itemId, item -> {
            item.addProperty("status", "PENDING");
            if (payload.has("starting_price")) {
                item.addProperty("startingPrice",        payload.get("starting_price").getAsDouble());
                item.addProperty("currentHighestPrice",  payload.get("starting_price").getAsDouble());
            }
            if (payload.has("start_time")) item.addProperty("startTime", str(payload, "start_time"));
            if (payload.has("end_time"))   item.addProperty("endTime",   str(payload, "end_time"));
        });
    }

    /** ITEM_DELETED → xóa item khỏi bảng ngay lập tức (cả hard và soft delete) */
    private void onItemDeleted(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        Platform.runLater(() -> masterList.removeIf(item -> itemId.equals(str(item, "id"))));
    }

    /** ITEM_ADDED → thêm item mới vào bảng ngay lập tức */
    private void onItemAdded(JsonObject payload) {
        JsonObject item = new JsonObject();
        item.addProperty("id",                   str(payload, "item_id"));
        item.addProperty("name",                 str(payload, "name"));
        item.addProperty("status",               "PENDING");
        item.addProperty("startingPrice",       payload.get("starting_price").getAsDouble());
        if (payload.has("current_highest_price") && !payload.get("current_highest_price").isJsonNull()) {
            item.addProperty("currentHighestPrice", payload.get("current_highest_price").getAsDouble());
        }
        item.addProperty("startTime",            str(payload, "start_time"));
        item.addProperty("endTime",              str(payload, "end_time"));
        item.addProperty("sellerId",             str(payload, "seller_id"));
        item.addProperty("sellerUsername",       str(payload, "seller_name"));
        if (payload.has("image_url") && !payload.get("image_url").isJsonNull()) {
            item.addProperty("imageUrl", str(payload, "image_url"));
        }
        Platform.runLater(() -> masterList.add(item));
    }

    /** ITEM_UPDATED → cập nhật tên, giá, thời gian, ảnh của item ngay lập tức */
    private void onItemUpdated(JsonObject payload) {
        String itemId = str(payload, "item_id");
        if (itemId.isEmpty()) return;
        updateItem(itemId, item -> {
            item.addProperty("name",                str(payload, "name"));
            item.addProperty("startingPrice",       payload.get("starting_price").getAsDouble());
            if (payload.has("current_highest_price") && !payload.get("current_highest_price").isJsonNull())
                item.addProperty("currentHighestPrice", payload.get("current_highest_price").getAsDouble());
            item.addProperty("startTime",           str(payload, "start_time"));
            item.addProperty("endTime",             str(payload, "end_time"));
            if (payload.has("image_url") && !payload.get("image_url").isJsonNull()) {
                item.addProperty("imageUrl", str(payload, "image_url"));
            }
        });
    }

    /**
     * Tìm item trong masterList theo id, áp dụng thay đổi, refresh bảng.
     * Chạy trên JavaFX thread (EventDispatcher đã bọc trong Platform.runLater).
     */
    private void updateItem(String itemId, java.util.function.Consumer<JsonObject> updater) {
        for (JsonObject item : masterList) {
            if (itemId.equals(str(item, "id"))) {
                updater.accept(item);
                break;
            }
        }
        tableItems.refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lớp 2: Background worker
    //  - Mỗi 1 giây: refresh cột đếm ngược
    //  - Mỗi 30 giây: UPDATE_ITEM_STATUS + reload toàn bộ từ DB
    // ─────────────────────────────────────────────────────────────────────────

    private void startBackgroundWorker() {
        stopBackgroundWorker();
        reloadCountdown = 30;
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SearchRoom-Background");
            t.setDaemon(true);
            return t;
        });
        backgroundExecutor.scheduleAtFixedRate(() -> {
            // Mỗi giây: refresh cột đếm ngược (tính lại từ data hiện có, không gọi server)
            Platform.runLater(() -> tableItems.refresh());

            reloadCountdown--;
            if (reloadCountdown <= 0) {
                reloadCountdown = 30;
                reloadFromDatabase();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopBackgroundWorker() {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow();
            backgroundExecutor = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load / Reload từ DB
    // ─────────────────────────────────────────────────────────────────────────

    private void loadFromDatabase() {
        new Thread(() -> {
            syncAndFetch();
        }, "SearchRoom-LoadDB").start();
    }

    private void reloadFromDatabase() {
        try {
            syncAndFetch();
        } catch (Exception e) {
            System.err.println("[SearchRoom] Lỗi reload DB: " + e.getMessage());
        }
    }

    /** UPDATE_ITEM_STATUS → GET_VISIBLE_ITEMS → populate */
    private void syncAndFetch() {
        JsonObject syncReq = new JsonObject();
        syncReq.addProperty("action", "UPDATE_ITEM_STATUS");
        ServerConnection.sendAuthRequest(syncReq);

        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_VISIBLE_ITEMS");
        JsonObject res = ServerConnection.sendAuthRequest(req);
        Platform.runLater(() -> populateMasterList(res));
    }

    private void populateMasterList(JsonObject response) {
        masterList.clear();
        if (response != null && "success".equals(str(response, "status"))) {
            JsonArray arr = response.get("message").getAsJsonArray();
            for (JsonElement el : arr) masterList.add(el.getAsJsonObject());
        }
        tableItems.refresh();
    }

    private void onSearchButton(ActionEvent event) {
        String keyword = field_search.getText().trim();
        if (keyword.isEmpty()) { loadFromDatabase(); return; }

        new Thread(() -> {
            JsonObject syncReq = new JsonObject();
            syncReq.addProperty("action", "UPDATE_ITEM_STATUS");
            ServerConnection.sendAuthRequest(syncReq);

            JsonObject req = new JsonObject();
            req.addProperty("action",  "SEARCH_ITEMS");
            req.addProperty("keyword", keyword);
            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> populateMasterList(res));
        }, "SearchRoom-Search").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup cột
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        col_name.setCellValueFactory(data ->
                new SimpleStringProperty(str(data.getValue(), "name")));

        col_current_price.setCellValueFactory(data -> new SimpleStringProperty(""));
        col_current_price.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String ignored, boolean empty) {
                super.updateItem(ignored, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                JsonObject item = (JsonObject) getTableRow().getItem();
                String status = str(item, "status").toUpperCase();
                if ("PENDING".equals(status)) {
                    double startingPrice = item.has("startingPrice") ? item.get("startingPrice").getAsDouble() : 0;
                    setText(String.format("%,.0f ₫", startingPrice));
                } else {
                    if (item.has("currentHighestPrice") && !item.get("currentHighestPrice").isJsonNull()) {
                        setText(String.format("%,.0f ₫", item.get("currentHighestPrice").getAsDouble()));
                    } else {
                        setText("_");
                    }
                }
            }
        });

        // Dùng setCellFactory để cell tính lại mỗi khi tableItems.refresh() được gọi
        col_time_left.setCellValueFactory(data -> new SimpleStringProperty(""));
        col_time_left.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String ignored, boolean empty) {
                super.updateItem(ignored, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                setText(computeTimeLeft((JsonObject) getTableRow().getItem()));
            }
        });

        // Cột trạng thái: đọc thẳng status từ DB
        col_status.setCellValueFactory(data -> {
            String raw = str(data.getValue(), "status").toUpperCase();
            String label = switch (raw) {
                case "PENDING"   -> "Chờ bắt đầu";
                case "ACTIVE"    -> "Đang diễn ra";
                case "CLOSED"    -> "Đã kết thúc";
                case "CANCELLED" -> "Đã hủy";
                default          -> raw;
            };
            return new SimpleStringProperty(label);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tính đếm ngược — dựa 100% vào status từ DB
    // ─────────────────────────────────────────────────────────────────────────

    private static String computeTimeLeft(JsonObject item) {
        String status = str(item, "status").toUpperCase();
        return switch (status) {
            case "CLOSED", "CANCELLED" -> "Đã kết thúc";
            case "PENDING" -> {
                String raw = str(item, "startTime");
                if (raw.isEmpty()) yield "Chờ bắt đầu";
                try {
                    long secs = ChronoUnit.SECONDS.between(
                            LocalDateTime.now(),
                            LocalDateTime.parse(raw.replace("T", " "), DT_FMT));
                    yield secs > 0 ? "Bắt đầu sau " + fmt(secs) : "Sắp bắt đầu";
                } catch (Exception e) { yield "Chờ bắt đầu"; }
            }
            case "ACTIVE" -> {
                String raw = str(item, "endTime");
                if (raw.isEmpty()) yield "—";
                try {
                    long secs = ChronoUnit.SECONDS.between(
                            LocalDateTime.now(),
                            LocalDateTime.parse(raw.replace("T", " "), DT_FMT));
                    yield secs > 0 ? fmt(secs) : "Đang kết thúc...";
                } catch (Exception e) { yield "—"; }
            }
            default -> "—";
        };
    }

    private static String fmt(long totalSecs) {
        long h = totalSecs / 3600, m = (totalSecs % 3600) / 60, s = totalSecs % 60;
        if (h > 0)    return h + " giờ " + m + " phút";
        if (m >= 5)   return m + " phút";
        if (m > 0)    return m + " phút " + s + " giây";
        return s + " giây";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRowClickNavigation() {
        tableItems.setRowFactory(tv -> {
            TableRow<JsonObject> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY
                        && e.getClickCount() == 2
                        && !row.isEmpty() && row.getItem() != null)
                    navigateToItemDetail(row.getItem());
            });
            return row;
        });
    }

    private void navigateToItemDetail(JsonObject item) {
        stopBackgroundWorker();
        unregisterEvents();
        Controller_Item_Detail.setCurrentItem(item);
        try {
            java.net.URL fxmlUrl = Scene_Utils.class.getResource(ITEM_DETAIL_VIEW);
            if (fxmlUrl == null) { System.err.println("[SearchRoom] FXML not found"); return; }
            Parent root = FXMLLoader.load(fxmlUrl);
            Stage stage = (Stage) tableItems.getScene().getWindow();
            double w = stage.getWidth(); double h = stage.getHeight(); boolean max = stage.isMaximized();
            Scene scene = new Scene(root);
            Scene_Utils.Apply_Default_CSS_Style(scene);
            stage.setMaximized(false); stage.setOpacity(0); stage.setScene(scene);
            Platform.runLater(() -> {
                if (max) stage.setMaximized(true); else { stage.setWidth(w); stage.setHeight(h); }
                stage.setOpacity(1);
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(250), root);
                ft.setFromValue(0); ft.setToValue(1); ft.play();
            });
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Search listener
    // ─────────────────────────────────────────────────────────────────────────

    private void setupSearchListener() {
        filteredList = new FilteredList<>(masterList, item -> true);
        tableItems.setItems(filteredList);
        field_search.textProperty().addListener((obs, o, n) -> {
            String kw = n == null ? "" : n.trim().toLowerCase();
            filteredList.setPredicate(item ->
                    kw.isEmpty() || str(item, "name").toLowerCase().contains(kw));
        });
        btn_search.setOnAction(this::onSearchButton);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FXML handlers
    // ─────────────────────────────────────────────────────────────────────────

    @FXML public void Go_to_item_detail(ActionEvent event) {
        JsonObject selected = tableItems.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Chọn sản phẩm", "Vui lòng chọn một sản phẩm để xem chi tiết."); return; }
        navigateToItemDetail(selected);
    }

    @FXML public void Go_to_bidding_room(ActionEvent event) { Go_to_item_detail(event); }

    @FXML public void Go_to_search_user(ActionEvent event) {
        stopBackgroundWorker(); unregisterEvents();
        try { Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Search_User.fxml"); }
        catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void Back_to_Dashboard(ActionEvent event) {
        stopBackgroundWorker(); unregisterEvents();
        try { Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml"); }
        catch (IOException e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String str(JsonObject o, String key) {
        return (o != null && o.has(key) && !o.get(key).isJsonNull())
                ? o.get(key).getAsString() : "";
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content);
        alert.showAndWait();
    }
}