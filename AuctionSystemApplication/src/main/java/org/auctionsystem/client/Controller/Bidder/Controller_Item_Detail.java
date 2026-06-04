package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.auctionsystem.client.Connectivity.ImageClient;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller_Item_Detail
 *
 * Màn hình chi tiết item — truy cập từ Searching_room.
 * Hiển thị thông tin item và lịch sử bid (readonly).
 *
 * Khi item ACTIVE:
 *   - Hiện nút "Vào phòng đấu giá" → chuyển sang Bidding_room
 *   - Hiện đồng hồ đếm ngược (readonly, không đặt giá tại đây)
 *   - Hiện bảng lịch sử bid realtime
 *
 * Khi item không ACTIVE:
 *   - Hiện thông báo trạng thái tương ứng
 *   - Hiện lịch sử bid (readonly, nếu có)
 */
public class Controller_Item_Detail {

    // ── FXML bindings ────────────────────────────────────────────────────────

    @FXML private Label     lbl_product_name;
    @FXML private Label     lbl_description;
    @FXML private Label     lbl_seller_username;
    @FXML private Label     lbl_starting_price;
    @FXML private Label     lbl_current_price;
    @FXML private Label     lbl_start_time;
    @FXML private Label     lbl_end_time;
    @FXML private Label     lbl_time_remaining;
    @FXML private Label     lbl_status_badge;
    @FXML private Label     lbl_status_message;
    @FXML private VBox      enter_room_panel;   // panel chứa nút + countdown, chỉ hiện khi ACTIVE
    @FXML private Button    btn_enter_room;
    @FXML private VBox      bid_history_panel;
    @FXML private ImageView productImageView;
    @FXML private Button    btn_change_item_image;

    @FXML private TableView<JsonObject>           tableBidHistory;
    @FXML private TableColumn<JsonObject, String> col_bid_bidder;
    @FXML private TableColumn<JsonObject, String> col_bid_amount;
    @FXML private TableColumn<JsonObject, String> col_bid_time;

    @FXML private TableView<JsonObject>           tableAllBidHistory;
    @FXML private TableColumn<JsonObject, String> col_all_bidder;
    @FXML private TableColumn<JsonObject, String> col_all_amount;
    @FXML private TableColumn<JsonObject, String> col_all_time;

    // ── State ─────────────────────────────────────────────────────────────────

    private static JsonObject currentItem;

    private String itemId;
    private String itemStatus;

    private final ObservableList<JsonObject> bidHistoryList    = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> allBidHistoryList = FXCollections.observableArrayList();

    private ScheduledExecutorService countdownTimer;
    private LocalDateTime            endTime;

    private static final DateTimeFormatter DT_FMT     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String SEARCHING_ROOM_VIEW   = "/org/auctionsystem/client/View/Searching_room.fxml";
    private static final String BIDDING_ROOM_VIEW     = "/org/auctionsystem/client/View/Bidding_room.fxml";

    // ── Static setter ────────────────────────────────────────────────────────

    public static void setCurrentItem(JsonObject item) {
        currentItem = item;
    }

    public static JsonObject getCurrentItem() {
        return currentItem;
    }

    // ── Khởi tạo ──────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        if (currentItem == null) return;

        itemId     = getString(currentItem, "id");
        itemStatus = getString(currentItem, "status").toUpperCase();

        setupBidHistoryTable();
        setupAllBidHistoryTable();
        populateStaticInfo();
        applyStatusLayout();
        loadProductImage();
        setupItemImageClickHandler();

        // BID_PLACED real-time đã được xóa khỏi màn hình này.
        // Bảng lịch sử bid chỉ load 1 lần; real-time bid chỉ có trong Bidding_room.
        EventDispatcher.register(EventType.ITEM_STARTED, payload -> {
            if (!isSameItem(payload)) return;
            Platform.runLater(() -> onSessionStarted(payload));
        });
        EventDispatcher.register(EventType.END_TIME_EXTENDED, this::onEndTimeExtended);
        EventDispatcher.register(EventType.AUCTION_SETTLED, payload -> {
            if (!isSameItem(payload)) return;
            Platform.runLater(this::onSessionEnded);
        });
        EventDispatcher.register(EventType.ITEM_CANCELLED, payload -> {
            if (!isSameItem(payload)) return;
            Platform.runLater(this::onSessionCancelled);
        });
    }

    // ── Setup bảng lịch sử bid (phiên hiện tại) ──────────────────────────────

    private void setupBidHistoryTable() {
        col_bid_bidder.setCellValueFactory(data -> {
            JsonObject row = data.getValue();
            String display = "";
            if (row.has("bidderName") && !row.get("bidderName").isJsonNull()
                    && !row.get("bidderName").getAsString().isBlank()) {
                display = row.get("bidderName").getAsString();
            } else if (row.has("bidderId") && !row.get("bidderId").isJsonNull()) {
                display = row.get("bidderId").getAsString();
            }
            return new SimpleStringProperty(display);
        });

        col_bid_amount.setCellValueFactory(data -> {
            double amount = data.getValue().has("bidAmount")
                    ? data.getValue().get("bidAmount").getAsDouble() : 0;
            return new SimpleStringProperty(String.format("%,.0f ₫", amount));
        });

        col_bid_time.setCellValueFactory(data -> {
            String raw = data.getValue().has("bidTime")
                    ? data.getValue().get("bidTime").getAsString() : "";
            return new SimpleStringProperty(formatDatetime(raw));
        });

        tableBidHistory.setItems(bidHistoryList);

        // Highlight hàng của chính mình
        tableBidHistory.setRowFactory(tv -> {
            javafx.scene.control.TableRow<JsonObject> row = new javafx.scene.control.TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.setStyle("");
                } else {
                    String myId     = UserSession.getInstance().getUserId();
                    String bidderId = newItem.has("bidderId")
                            ? newItem.get("bidderId").getAsString() : "";
                    row.setStyle(myId.equals(bidderId)
                            ? "-fx-background-color: rgba(100,180,255,0.18);"
                            : "");
                }
            });
            return row;
        });
    }

    // ── Setup bảng toàn bộ lịch sử bid ──────────────────────────────────────

    private void setupAllBidHistoryTable() {
        col_all_bidder.setCellValueFactory(data -> {
            JsonObject row = data.getValue();
            String display = "";
            if (row.has("bidderName") && !row.get("bidderName").isJsonNull()
                    && !row.get("bidderName").getAsString().isBlank()) {
                display = row.get("bidderName").getAsString();
            } else if (row.has("bidderId") && !row.get("bidderId").isJsonNull()) {
                display = row.get("bidderId").getAsString();
            }
            return new SimpleStringProperty(display);
        });

        col_all_amount.setCellValueFactory(data -> {
            double amount = data.getValue().has("bidAmount")
                    ? data.getValue().get("bidAmount").getAsDouble() : 0;
            return new SimpleStringProperty(String.format("%,.0f ₫", amount));
        });

        col_all_time.setCellValueFactory(data -> {
            String raw = data.getValue().has("bidTime")
                    ? data.getValue().get("bidTime").getAsString() : "";
            return new SimpleStringProperty(formatDatetime(raw));
        });

        tableAllBidHistory.setItems(allBidHistoryList);
    }

    // ── Điền thông tin tĩnh ──────────────────────────────────────────────────

    private void populateStaticInfo() {
        setText(lbl_product_name,   getString(currentItem, "name"));
        setText(lbl_description,    getString(currentItem, "description"));
        setText(lbl_seller_username, getSellerDisplayName());
        setText(lbl_starting_price, String.format("%,.0f ₫", getDouble(currentItem, "startingPrice")));
        updateCurrentPriceLabel();
        setText(lbl_start_time, formatDatetime(getString(currentItem, "startTime")));
        setText(lbl_end_time,   formatDatetime(getString(currentItem, "endTime")));
        setText(lbl_status_badge, itemStatus);
    }

    // ── Ẩn/hiện layout theo trạng thái ───────────────────────────────────────

    private void applyStatusLayout() {
        // Toàn bộ lịch sử luôn được load
        loadAllBidHistory();

        switch (itemStatus) {
            case "ACTIVE" -> {
                // Hiện panel nút vào phòng + countdown (readonly)
                show(enter_room_panel);
                startCountdown();
                // Hiện lịch sử bid phiên hiện tại
                loadBidHistory();
            }
            case "PENDING" ->
                    showStatusMessage("⏳ Phiên đấu giá chưa bắt đầu. Quay lại sau nhé!");
            case "ENDED", "CLOSED" -> {
                showStatusMessage("🏁 Phiên đấu giá đã kết thúc.");
                loadBidHistory();
            }
            case "CANCELLED" ->
                    showStatusMessage("❌ Sản phẩm này đã bị hủy.");
            default ->
                    showStatusMessage("Trạng thái: " + itemStatus);
        }
    }

    private void showStatusMessage(String msg) {
        setText(lbl_status_message, msg);
        show(lbl_status_message);
        hide(enter_room_panel);
    }

    // ── Load lịch sử bid ─────────────────────────────────────────────────────

    private void loadBidHistory() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",  "GET_ACTIVE_BIDS_BY_ITEM");
            req.addProperty("item_id", itemId);
            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> populateBidHistory(res));
        }, "ItemDetail-LoadBidHistory").start();
    }

    private void populateBidHistory(JsonObject response) {
        bidHistoryList.clear();
        if (response != null && "success".equals(response.get("status").getAsString())) {
            JsonArray arr = response.get("message").getAsJsonArray();
            for (int i = arr.size() - 1; i >= 0; i--) {
                bidHistoryList.add(arr.get(i).getAsJsonObject());
            }
        }
        if (!bidHistoryList.isEmpty() || !allBidHistoryList.isEmpty()) {
            show(bid_history_panel);
        }
    }

    private void loadAllBidHistory() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",  "GET_ALL_BIDS_BY_ITEM");
            req.addProperty("item_id", itemId);
            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                allBidHistoryList.clear();
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    res.get("message").getAsJsonArray()
                            .forEach(el -> allBidHistoryList.add(el.getAsJsonObject()));
                }
                if (!allBidHistoryList.isEmpty() || !bidHistoryList.isEmpty()) {
                    show(bid_history_panel);
                }
            });
        }, "ItemDetail-LoadAllBidHistory").start();
    }

    // ── Countdown (readonly — chỉ hiển thị, không đặt giá tại đây) ──────────

    private void startCountdown() {
        String rawEnd = getString(currentItem, "endTime");
        if (rawEnd.isBlank()) return;
        try {
            endTime = LocalDateTime.parse(rawEnd.replace("T", " "), DT_FMT);
        } catch (Exception e) { return; }
        restartCountdown();
    }

    private void restartCountdown() {
        stopCountdown();
        countdownTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ItemDetail-Countdown");
            t.setDaemon(true);
            return t;
        });
        countdownTimer.scheduleAtFixedRate(() -> {
            long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
            Platform.runLater(() -> {
                if (lbl_time_remaining == null) return;
                if (secs <= 0) {
                    lbl_time_remaining.setText("⏰ Thời gian còn lại: Đã kết thúc");
                    stopCountdown();
                } else if (secs < 60) {
                    lbl_time_remaining.setText("⏰ Thời gian còn lại: " + secs + " giây");
                } else if (secs < 3600) {
                    lbl_time_remaining.setText("⏰ Thời gian còn lại: "
                            + (secs / 60) + " phút " + (secs % 60) + " giây");
                } else {
                    long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
                    lbl_time_remaining.setText("⏰ Thời gian còn lại: " + h + "h " + m + "m " + s + "s");
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopCountdown() {
        if (countdownTimer != null && !countdownTimer.isShutdown())
            countdownTimer.shutdownNow();
    }

    // ── Setup nút đổi ảnh item (chỉ seller của item / admin mới thấy nút) ────

    private void setupItemImageClickHandler() {
        if (btn_change_item_image == null) return;

        String sellerId = getString(currentItem, "sellerId");
        String myId     = UserSession.getInstance().getUserId();
        String myRole   = UserSession.getInstance().getRole();

        boolean isSeller = sellerId != null && sellerId.equals(myId);
        boolean isAdmin  = "ADMIN".equalsIgnoreCase(myRole);

        if (isSeller || isAdmin) {
            // Hiện nút camera bên dưới ảnh sản phẩm
            btn_change_item_image.setVisible(true);
            btn_change_item_image.setManaged(true);
        }
        // Nếu không phải seller/admin: nút vẫn ẩn (visible=false, managed=false từ FXML)
    }

    @FXML
    private void onChangeItemImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh sản phẩm");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.jpeg", "*.png", "*.webp"));

        javafx.stage.Window window = productImageView != null
                ? productImageView.getScene().getWindow() : null;
        File file = fc.showOpenDialog(window);
        if (file == null) return;

        // Preview ngay
        try {
            if (productImageView != null)
                productImageView.setImage(new Image(file.toURI().toString()));
        } catch (Exception ignored) {}

        // Upload lên server
        new Thread(() -> {
            JsonObject result = ImageClient.uploadItemImage(file);
            Platform.runLater(() -> {
                if (result != null && "success".equals(result.get("status").getAsString())) {
                    String newImageUrl = result.has("image_url")
                            ? result.get("image_url").getAsString() : null;
                    if (newImageUrl != null && currentItem != null) {
                        currentItem.addProperty("imageUrl", newImageUrl);
                        sendUpdateItemImage(newImageUrl);
                    }
                } else {
                    String msg = (result != null && result.has("message"))
                            ? result.get("message").getAsString() : "Lỗi upload ảnh.";
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi");
                    alert.setHeaderText(null);
                    alert.setContentText(msg);
                    alert.showAndWait();
                    loadProductImage();
                }
            });
        }, "ItemDetail-UploadImage").start();
    }

    private void sendUpdateItemImage(String newImageUrl) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",    "UPDATE_ITEM_IMAGE");
            req.addProperty("item_id",   itemId);
            req.addProperty("image_url", newImageUrl);
            req.addProperty("user_id",   UserSession.getInstance().getUserId());

            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Thành công");
                    alert.setHeaderText(null);
                    alert.setContentText("Cập nhật ảnh sản phẩm thành công!");
                    alert.showAndWait();
                } else {
                    System.err.println("[ItemDetail] Cảnh báo: upload OK nhưng UPDATE_ITEM_IMAGE thất bại.");
                }
            });
        }, "ItemDetail-UpdateImageUrl").start();
    }

    // ── Load ảnh ─────────────────────────────────────────────────────────────

    private void loadProductImage() {
        if (productImageView == null) return;
        String url = getString(currentItem, "imageUrl");
        if (url.isBlank()) return;

        new Thread(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action",    "GET_IMAGE");
                req.addProperty("image_url", url);
                JsonObject res = ServerConnection.sendAuthRequest(req);
                if (res != null && "success".equals(res.get("status").getAsString())
                        && res.has("image_data")) {
                    byte[] bytes = java.util.Base64.getDecoder()
                            .decode(res.get("image_data").getAsString());
                    Image img = new Image(new java.io.ByteArrayInputStream(bytes));
                    Platform.runLater(() -> productImageView.setImage(img));
                }
            } catch (Exception e) {
                System.err.println("[ItemDetail] Lỗi load ảnh: " + e.getMessage());
            }
        }, "ItemDetail-LoadImage").start();
    }

    // ── Nút vào phòng đấu giá ────────────────────────────────────────────────

    @FXML
    public void onEnterBiddingRoom(ActionEvent event) {
        // Truyền currentItem sang BiddingRoom rồi chuyển scene
        // currentItem vẫn giữ nguyên trong static field — BiddingRoom dùng chung
        Controller_Bidding_room.setCurrentItem(currentItem);
        unregisterEvents();
        stopCountdown();
        try {
            Scene_Utils.Change_Scene(event, BIDDING_ROOM_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Quay lại Searching_room ───────────────────────────────────────────────

    @FXML
    public void back_to_searching_room(ActionEvent event) {
        unregisterEvents();
        stopCountdown();
        currentItem = null;
        try {
            Scene_Utils.Change_Scene(event, SEARCHING_ROOM_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Real-time event handlers (readonly — chỉ cập nhật giá + lịch sử) ────

    /**
     * BID_PLACED: cập nhật giá cao nhất + thêm vào bảng lịch sử phiên hiện tại.
     * Không cho đặt giá tại đây — người dùng phải vào Bidding_room.
     */
    private void onBidPlaced(JsonObject payload) {
        if (!isSameItem(payload)) return;
        double amount = payload.has("bid_amount") ? payload.get("bid_amount").getAsDouble() : 0;
        if (currentItem != null) currentItem.addProperty("currentHighestPrice", amount);

        JsonObject entry = new JsonObject();
        entry.addProperty("bidderId",   payload.has("bidder_id")   ? payload.get("bidder_id").getAsString()   : "");
        entry.addProperty("bidderName", payload.has("bidder_name") ? payload.get("bidder_name").getAsString() : "");
        entry.addProperty("bidAmount",  amount);
        entry.addProperty("bidTime",    payload.has("bid_time")    ? payload.get("bid_time").getAsString()    : "");

        Platform.runLater(() -> {
            updateCurrentPriceLabel();
            bidHistoryList.add(0, entry);
            show(bid_history_panel);
        });
    }

    private void onEndTimeExtended(JsonObject payload) {
        if (!isSameItem(payload)) return;
        String newEnd = payload.has("new_end_time") ? payload.get("new_end_time").getAsString() : "";
        try {
            endTime = LocalDateTime.parse(newEnd.replace("T", " "), DT_FMT);
            if (currentItem != null) currentItem.addProperty("endTime", newEnd);
            Platform.runLater(() -> setText(lbl_end_time, formatDatetime(newEnd)));
            restartCountdown();
        } catch (Exception ignored) {}
    }

    private void onSessionEnded() {
        stopCountdown();
        itemStatus = "ENDED";
        hide(enter_room_panel);
        showStatusMessage("🏁 Phiên đấu giá vừa kết thúc.");
        show(bid_history_panel);
    }

    private void onSessionStarted(JsonObject payload) {
        itemStatus = "ACTIVE";
        // Cập nhật end_time nếu server gửi kèm
        if (payload.has("end_time")) {
            String newEnd = payload.get("end_time").getAsString();
            if (currentItem != null) currentItem.addProperty("endTime", newEnd);
            setText(lbl_end_time, formatDatetime(newEnd));
        }
        setText(lbl_status_badge, "ACTIVE");
        hide(lbl_status_message);
        show(enter_room_panel);
        startCountdown();
        loadBidHistory();
    }

    private void onSessionCancelled() {
        stopCountdown();
        itemStatus = "CANCELLED";
        hide(enter_room_panel);
        showStatusMessage("❌ Sản phẩm này vừa bị hủy.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isSameItem(JsonObject payload) {
        return payload.has("item_id") && itemId.equals(payload.get("item_id").getAsString());
    }

    private void updateCurrentPriceLabel() {
        if (lbl_current_price == null || currentItem == null) return;
        double price = currentItem.has("currentHighestPrice")
                && !currentItem.get("currentHighestPrice").isJsonNull()
                ? currentItem.get("currentHighestPrice").getAsDouble()
                : getDouble(currentItem, "startingPrice");
        setText(lbl_current_price, String.format("%,.0f ₫", price));
    }

    private void unregisterEvents() {
        EventDispatcher.unregister(EventType.ITEM_STARTED);
        EventDispatcher.unregister(EventType.END_TIME_EXTENDED);
        EventDispatcher.unregister(EventType.AUCTION_SETTLED);
        EventDispatcher.unregister(EventType.ITEM_CANCELLED);
    }

    private static void setText(Label lbl, String text) {
        if (lbl != null) lbl.setText(text != null ? text : "");
    }

    private static void show(javafx.scene.Node node) {
        if (node != null) { node.setVisible(true); node.setManaged(true); }
    }

    private static void hide(javafx.scene.Node node) {
        if (node != null) { node.setVisible(false); node.setManaged(false); }
    }

    private static String getString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : "";
    }

    private static double getDouble(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsDouble() : 0.0;
    }

    private static String getSellerDisplayName() {
        String username = getString(currentItem, "sellerUsername");
        if (!username.isBlank()) return "@" + username;

        String sellerId = getString(currentItem, "sellerId");
        return sellerId.isBlank() ? "—" : sellerId;
    }

    private static String formatDatetime(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        try {
            LocalDateTime dt = LocalDateTime.parse(raw.replace("T", " "), DT_FMT);
            return dt.format(DT_DISPLAY);
        } catch (Exception e) {
            return raw;
        }
    }
}
