package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller_Bidding_room
 *
 * Màn hình phòng đấu giá — chỉ truy cập được khi item ACTIVE,
 * thông qua nút "Vào phòng đấu giá" trong Controller_Item_Detail.
 *
 * Chức năng:
 *  - Đặt giá (quick bid + nhập tay)
 *  - Countdown realtime
 *  - Bảng lịch sử bid phiên hiện tại (realtime qua BID_PLACED)
 *  - Bảng toàn bộ lịch sử bid (static, load 1 lần)
 *  - Quay lại Item_Detail hoặc Searching_room
 */
public class Controller_Bidding_room {

    // ── FXML bindings ────────────────────────────────────────────────────────

    @FXML private Label     lbl_product_name;
    @FXML private Label     lbl_time_remaining;
    @FXML private Label     lbl_starting_price;
    @FXML private Label     lbl_current_price;
    @FXML private Label     lbl_balance;
    @FXML private Label     lbl_bid_error;
    @FXML private Label     lbl_status_message;
    @FXML private VBox      bid_panel;
    @FXML private TextField field_bid_amount;
    @FXML private Button    btn_bid;
    @FXML private ImageView productImageView;

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
    private String sellerId;

    private final ObservableList<JsonObject> bidHistoryList    = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> allBidHistoryList = FXCollections.observableArrayList();

    private ScheduledExecutorService countdownTimer;
    private LocalDateTime            endTime;

    private static final DateTimeFormatter DT_FMT          = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_DISPLAY       = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String           ITEM_DETAIL_VIEW  = "/org/auctionsystem/client/View/Item_Detail.fxml";
    private static final String           SEARCHING_ROOM_VIEW = "/org/auctionsystem/client/View/Searching_room.fxml";

    // ── Static setter ────────────────────────────────────────────────────────

    public static void setCurrentItem(JsonObject item) {
        currentItem = item;
    }

    // ── Khởi tạo ──────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        if (currentItem == null) return;

        itemId   = getString(currentItem, "id");
        sellerId = getString(currentItem, "sellerId");

        String userId   = UserSession.getInstance().getUserId();
        boolean isSeller = userId.equals(sellerId);

        setupBidHistoryTable();
        setupAllBidHistoryTable();
        populateStaticInfo();

        if (isSeller) {
            // Seller xem phòng đấu giá nhưng không tự đặt giá
            hide(bid_panel);
            setText(lbl_status_message, "Đây là sản phẩm của bạn — không thể tự đặt giá.");
            show(lbl_status_message);
        }

        loadBidHistory();
        loadAllBidHistory();
        startCountdown();
        loadProductImage();

        // Realtime events
        EventDispatcher.register(EventType.BID_PLACED,        this::onBidPlaced);
        EventDispatcher.register(EventType.END_TIME_EXTENDED,  this::onEndTimeExtended);
        EventDispatcher.register(EventType.AUCTION_SETTLED,    this::onAuctionSettled);
        EventDispatcher.register(EventType.ITEM_CANCELLED,     this::onItemCancelled);
        EventDispatcher.register(EventType.BID_DEDUCT,         this::onBalanceChanged);
        EventDispatcher.register(EventType.BID_CREDIT,         this::onBalanceChanged);
    }

    // ── Setup bảng lịch sử bid phiên ─────────────────────────────────────────

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
        setText(lbl_starting_price, String.format("%,.0f ₫", getDouble(currentItem, "startingPrice")));
        updateCurrentPriceLabel();
        updateBalanceLabel(UserSession.getInstance().getBalance());
    }

    // ── Load lịch sử bid ─────────────────────────────────────────────────────

    private void loadBidHistory() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",  "GET_ACTIVE_BIDS_BY_ITEM");
            req.addProperty("item_id", itemId);
            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                bidHistoryList.clear();
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    JsonArray arr = res.get("message").getAsJsonArray();
                    for (int i = arr.size() - 1; i >= 0; i--) {
                        bidHistoryList.add(arr.get(i).getAsJsonObject());
                    }
                }
            });
        }, "BiddingRoom-LoadBidHistory").start();
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
            });
        }, "BiddingRoom-LoadAllBidHistory").start();
    }

    // ── Countdown ────────────────────────────────────────────────────────────

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
            Thread t = new Thread(r, "BiddingRoom-Countdown");
            t.setDaemon(true);
            return t;
        });
        countdownTimer.scheduleAtFixedRate(() -> {
            long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
            Platform.runLater(() -> {
                if (lbl_time_remaining == null) return;
                if (secs <= 0) {
                    lbl_time_remaining.setText("⏰ Đã kết thúc");
                    stopCountdown();
                } else if (secs < 60) {
                    lbl_time_remaining.setText("⏰ " + secs + " giây");
                } else if (secs < 3600) {
                    lbl_time_remaining.setText("⏰ " + (secs / 60) + " phút " + (secs % 60) + " giây");
                } else {
                    long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
                    lbl_time_remaining.setText("⏰ " + h + "h " + m + "m " + s + "s");
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopCountdown() {
        if (countdownTimer != null && !countdownTimer.isShutdown())
            countdownTimer.shutdownNow();
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
                System.err.println("[BiddingRoom] Lỗi load ảnh: " + e.getMessage());
            }
        }, "BiddingRoom-LoadImage").start();
    }

    // ── Nút tăng nhanh ────────────────────────────────────────────────────────

    @FXML public void onQuickBid10k(ActionEvent e)  { addToBidField(10_000); }
    @FXML public void onQuickBid20k(ActionEvent e)  { addToBidField(20_000); }
    @FXML public void onQuickBid50k(ActionEvent e)  { addToBidField(50_000); }
    @FXML public void onQuickBid100k(ActionEvent e) { addToBidField(100_000); }
    @FXML public void onQuickBid500k(ActionEvent e) { addToBidField(500_000); }
    @FXML public void onQuickBid1tr(ActionEvent e)  { addToBidField(1_000_000); }
    @FXML public void onQuickBid2tr(ActionEvent e)  { addToBidField(2_000_000); }
    @FXML public void onQuickBid5tr(ActionEvent e)  { addToBidField(5_000_000); }

    private void addToBidField(double amount) {
        if (field_bid_amount == null) return;
        String cur  = field_bid_amount.getText().trim();
        double base = 0;
        try { base = Double.parseDouble(cur); } catch (NumberFormatException ignored) {}
        field_bid_amount.setText(String.valueOf((long)(base + amount)));
    }

    // ── Đặt giá ──────────────────────────────────────────────────────────────

    @FXML
    public void onPlaceBid(ActionEvent event) {
        if (field_bid_amount == null) return;
        clearError();

        String amountStr = field_bid_amount.getText().trim();
        if (amountStr.isEmpty()) { setError("Vui lòng nhập số tiền muốn đặt."); return; }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            setError("Số tiền không hợp lệ."); return;
        }

        if (btn_bid != null) btn_bid.setDisable(true);

        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",     "PLACE_BID");
            req.addProperty("item_id",    itemId);
            req.addProperty("bid_amount", amount);
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                if (btn_bid != null) btn_bid.setDisable(false);
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    field_bid_amount.clear();
                    clearError();
                    // Bảng + giá cập nhật qua BID_PLACED event
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Lỗi kết nối server.";
                    setError(msg);
                }
            });
        }, "BiddingRoom-PlaceBid").start();
    }

    // ── Điều hướng ────────────────────────────────────────────────────────────

    /**
     * Quay lại Item_Detail — giữ nguyên currentItem trong Controller_Item_Detail
     * (static field vẫn còn đó vì chưa bị clear).
     */
    @FXML
    public void back_to_item_detail(ActionEvent event) {
        unregisterEvents();
        stopCountdown();
        // Đồng bộ currentHighestPrice về Controller_Item_Detail.currentItem
        // (cùng object reference nên không cần set lại)
        try {
            Scene_Utils.Change_Scene(event, ITEM_DETAIL_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void back_to_searching_room(ActionEvent event) {
        unregisterEvents();
        stopCountdown();
        currentItem = null;
        Controller_Item_Detail.setCurrentItem(null);
        try {
            Scene_Utils.Change_Scene(event, SEARCHING_ROOM_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Real-time event handlers ──────────────────────────────────────────────

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
            clearError();
        });
    }

    private void onEndTimeExtended(JsonObject payload) {
        if (!isSameItem(payload)) return;
        String newEnd = payload.has("new_end_time") ? payload.get("new_end_time").getAsString() : "";
        long   extBy  = payload.has("extended_by")  ? payload.get("extended_by").getAsLong()   : 30;
        try {
            endTime = LocalDateTime.parse(newEnd.replace("T", " "), DT_FMT);
            if (currentItem != null) currentItem.addProperty("endTime", newEnd);
            restartCountdown();
        } catch (Exception ignored) {}
        Platform.runLater(() -> setError("⏱ Gia hạn thêm " + extBy + " giây do bid sát giờ kết thúc!"));
    }

    private void onAuctionSettled(JsonObject payload) {
        if (!isSameItem(payload)) return;
        stopCountdown();
        if (btn_bid != null) Platform.runLater(() -> btn_bid.setDisable(true));
        hide(bid_panel);
        setText(lbl_status_message, "🏁 Phiên đấu giá đã kết thúc.");
        show(lbl_status_message);

        String buyerId = payload.has("buyer_id") ? payload.get("buyer_id").getAsString() : "";
        boolean iWon   = UserSession.getInstance().getUserId().equals(buyerId);
        Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Kết thúc đấu giá",
                iWon ? "🎉 Chúc mừng! Bạn đã thắng phiên đấu giá này!"
                     : "Phiên đấu giá đã kết thúc."));
    }

    private void onItemCancelled(JsonObject payload) {
        if (!isSameItem(payload)) return;
        stopCountdown();
        Platform.runLater(() -> {
            hide(bid_panel);
            setText(lbl_status_message, "❌ Sản phẩm này đã bị hủy.");
            show(lbl_status_message);
            if (btn_bid != null) btn_bid.setDisable(true);
        });
        showAlert(Alert.AlertType.WARNING, "Sản phẩm bị hủy",
                "Sản phẩm này đã bị hủy bởi người bán hoặc quản trị viên.");
    }

    private void onBalanceChanged(JsonObject payload) {
        try {
            double newBalance = payload.get("balance").getAsDouble();
            UserSession.getInstance().setBalance(newBalance);
            Platform.runLater(() -> updateBalanceLabel(newBalance));
        } catch (Exception e) {
            System.err.println("[BiddingRoom] Lỗi parse balance: " + e.getMessage());
        }
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

    private void updateBalanceLabel(double balance) {
        setText(lbl_balance, String.format("%,.0f ₫", balance));
    }

    private void unregisterEvents() {
        EventDispatcher.unregister(EventType.BID_PLACED);
        EventDispatcher.unregister(EventType.END_TIME_EXTENDED);
        EventDispatcher.unregister(EventType.AUCTION_SETTLED);
        EventDispatcher.unregister(EventType.ITEM_CANCELLED);
        EventDispatcher.unregister(EventType.BID_DEDUCT);
        EventDispatcher.unregister(EventType.BID_CREDIT);
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

    private void setError(String msg) {
        Platform.runLater(() -> { if (lbl_bid_error != null) lbl_bid_error.setText(msg); });
    }

    private void clearError() {
        if (lbl_bid_error != null) lbl_bid_error.setText("");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private static String getString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : "";
    }

    private static double getDouble(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsDouble() : 0.0;
    }

    private static String formatDatetime(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        try {
            LocalDateTime dt = LocalDateTime.parse(raw.replace("T", " "), DT_FMT);
            return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        } catch (Exception e) {
            return raw;
        }
    }
}
