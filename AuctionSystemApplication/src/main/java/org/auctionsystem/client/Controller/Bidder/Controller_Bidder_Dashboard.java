package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.BanWatcher;
import org.auctionsystem.client.event.BalanceWatcher;
import org.auctionsystem.client.event.NotificationManager;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class Controller_Bidder_Dashboard {

    @FXML private Label     lbl_balance;
    @FXML private VBox      widget_leading;
    @FXML private VBox      vbox_leading_items;
    @FXML private ScrollPane scroll_leading;
    @FXML private Label     lbl_leading_count;

    private static final String Login_View               = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Bidder_Profile_View      = "/org/auctionsystem/client/View/Bidder_Profile.fxml";
    private static final String Bidding_History_View     = "/org/auctionsystem/client/View/Bidding_History.fxml";
    private static final String Bidding_Result_View      = "/org/auctionsystem/client/View/Bidding_Result.fxml";
    private static final String Search_User_View         = "/org/auctionsystem/client/View/Search_User.fxml";
    private static final String Searching_Room_View      = "/org/auctionsystem/client/View/Searching_room.fxml";
    private static final String Wallet_View              = "/org/auctionsystem/client/View/Wallet_Transaction.fxml";
    private static final String Transaction_History_View = "/org/auctionsystem/client/View/Transaction_History.fxml";
    private static final String My_Items_Bidder_View     = "/org/auctionsystem/client/View/My_Items_Bidder.fxml";

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        updateBalanceLabel(UserSession.getInstance().getBalance());

        // Đăng ký nhận balance updates qua BalanceWatcher (global singleton)
        BalanceWatcher.registerListener("BidderDashboard", balance -> updateBalanceLabel(balance));

        // Đăng ký events real-time
        EventDispatcher.register(EventType.ITEM_DELETED,     this::onItemDeleted);
        EventDispatcher.register(EventType.BID_PLACED,       this::onBidPlaced);
        EventDispatcher.register(EventType.AUCTION_SETTLED,  this::onAuctionSettled);
        EventDispatcher.register(EventType.ITEM_CANCELLED,   this::onItemCancelled);

        // Load widget lần đầu
        loadLeadingBids();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Widget: Đang dẫn đầu
    // ─────────────────────────────────────────────────────────────────────────

    private void loadLeadingBids() {
        String bidderId = UserSession.getInstance().getUserId();
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",     "GET_ACTIVE_BIDS_BY_BIDDER");
            req.addProperty("bidder_id",  bidderId);
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                if (vbox_leading_items == null) return;
                vbox_leading_items.getChildren().clear();

                if (res == null || !"success".equals(getString(res, "status", ""))) {
                    Label err = new Label("Không thể tải dữ liệu.");
                    err.getStyleClass().add("leading-widget-empty");
                    vbox_leading_items.getChildren().add(err);
                    if (lbl_leading_count != null) lbl_leading_count.setText("");
                    return;
                }

                JsonArray arr = res.get("message").getAsJsonArray();

                if (arr.isEmpty()) {
                    Label empty = new Label("Bạn chưa dẫn đầu phiên nào đang diễn ra.");
                    empty.getStyleClass().add("leading-widget-empty");
                    vbox_leading_items.getChildren().add(empty);
                    if (lbl_leading_count != null) lbl_leading_count.setText("");
                    return;
                }

                if (lbl_leading_count != null)
                    lbl_leading_count.setText(arr.size() + " phiên");

                for (JsonElement el : arr) {
                    JsonObject item = el.getAsJsonObject();
                    vbox_leading_items.getChildren().add(buildItemRow(item));
                }
            });
        }, "Dashboard-LeadingBids").start();
    }

    /** Tạo một hàng hiển thị cho 1 item đang dẫn đầu */
    private HBox buildItemRow(JsonObject item) {
        String name      = getString(item, "itemName", getString(item, "item_id", "—"));
        String priceRaw  = item.has("bidAmount") && !item.get("bidAmount").isJsonNull()
                ? String.format("%,.0f ₫", item.get("bidAmount").getAsDouble())
                : "—";
        String timeLabel = buildTimeLabel(item);

        // Tên item (co giãn)
        Label lblName = new Label(name);
        lblName.getStyleClass().add("leading-item-name");
        lblName.setMaxWidth(Double.MAX_VALUE);
        lblName.setWrapText(false);
        lblName.setEllipsisString("…");
        HBox.setHgrow(lblName, Priority.ALWAYS);

        // Giá bid
        Label lblPrice = new Label(priceRaw);
        lblPrice.getStyleClass().add("leading-item-price");

        // Thời gian còn lại / kết thúc
        Label lblTime = new Label(timeLabel);
        lblTime.getStyleClass().add("leading-item-time");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.SOMETIMES);

        HBox row = new HBox(8, lblName, spacer, lblPrice, lblTime);
        row.getStyleClass().add("leading-item-row");
        row.setMaxWidth(Double.MAX_VALUE);
        // userData dùng để xóa row khi nhận event — ưu tiên "itemId" (Gson/Bid), fallback "item_id"
        String rowItemId = getString(item, "itemId", getString(item, "item_id", ""));
        row.setUserData(rowItemId);

        return row;
    }

    /** Tính nhãn thời gian: "còn Xh Ym" nếu < 24h, ngày giờ nếu xa hơn */
    private String buildTimeLabel(JsonObject item) {
        if (!item.has("itemEndTime") || item.get("itemEndTime").isJsonNull()) return "";
        try {
            // Gson serialize LocalDateTime thành object {date:{year,month,day}, time:{hour,minute,...}}
            // hoặc string tùy cấu hình — thử parse string trước
            String raw = item.get("itemEndTime").toString();
            // Nếu là JsonObject (Gson default LocalDateTime serialization)
            if (item.get("itemEndTime").isJsonObject()) {
                JsonObject dt = item.get("itemEndTime").getAsJsonObject();
                JsonObject date = dt.getAsJsonObject("date");
                JsonObject time = dt.getAsJsonObject("time");
                int year = date.get("year").getAsInt();
                int month = date.get("monthValue").getAsInt();
                int day   = date.get("dayOfMonth").getAsInt();
                int hour  = time.get("hour").getAsInt();
                int min   = time.get("minute").getAsInt();
                int sec   = time.get("second").getAsInt();
                LocalDateTime end = LocalDateTime.of(year, month, day, hour, min, sec);
                return formatCountdown(end);
            }
            // Nếu là string ISO
            String s = item.get("itemEndTime").getAsString();
            LocalDateTime end = LocalDateTime.parse(s.replace(" ", "T"));
            return formatCountdown(end);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatCountdown(LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        long totalSecs = ChronoUnit.SECONDS.between(now, end);
        if (totalSecs <= 0) return "Đã hết giờ";
        if (totalSecs < 3600) {
            long m = totalSecs / 60, s = totalSecs % 60;
            return String.format("còn %dm%02ds", m, s);
        }
        if (totalSecs < 86400) {
            long h = totalSecs / 3600, m = (totalSecs % 3600) / 60;
            return String.format("còn %dh%02dm", h, m);
        }
        return "đến " + end.format(TIME_FMT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time balance
    // ─────────────────────────────────────────────────────────────────────────

    private void onItemDeleted(JsonObject payload) {
        String itemId = payload.has("item_id") ? payload.get("item_id").getAsString() : "";
        if (itemId.isEmpty() || vbox_leading_items == null) return;
        Platform.runLater(() -> {
            vbox_leading_items.getChildren().removeIf(node -> itemId.equals(node.getUserData()));
            updateLeadingCount();
        });
    }

    /**
     * Có bid mới được đặt cho 1 item:
     * - Nếu là chính mình bid → thêm item vào danh sách nếu chưa có (vừa dẫn đầu item mới),
     *   hoặc cập nhật giá nếu đã có (tự bid thêm lên item mình đang dẫn đầu).
     * - Nếu là người khác bid → xóa item khỏi danh sách vì mình không còn dẫn đầu nữa.
     */
    private void onBidPlaced(JsonObject payload) {
        String itemId   = getString(payload, "item_id", "");
        String bidderId = getString(payload, "bidder_id", "");
        String myId     = UserSession.getInstance().getUserId();
        if (itemId.isEmpty() || vbox_leading_items == null) return;

        if (myId.equals(bidderId)) {
            // Mình vừa bid — kiểm tra item đã có trong danh sách chưa
            Platform.runLater(() -> {
                if (vbox_leading_items == null) return;
                boolean alreadyLeading = vbox_leading_items.getChildren().stream()
                        .anyMatch(node -> itemId.equals(node.getUserData()));
                if (alreadyLeading) {
                    // Đã có rồi — cập nhật giá hiển thị
                    double newPrice = payload.has("bid_amount")
                            ? payload.get("bid_amount").getAsDouble() : -1;
                    if (newPrice >= 0) {
                        vbox_leading_items.getChildren().stream()
                                .filter(node -> itemId.equals(node.getUserData()) && node instanceof HBox)
                                .map(node -> (HBox) node)
                                .forEach(row -> row.getChildren().stream()
                                        .filter(c -> c instanceof Label lbl
                                                && lbl.getStyleClass().contains("leading-item-price"))
                                        .map(c -> (Label) c)
                                        .findFirst()
                                        .ifPresent(lbl -> lbl.setText(String.format("%,.0f \u20ab", newPrice))));
                    }
                } else {
                    // Chưa có — fetch thông tin item từ server rồi thêm vào danh sách
                    new Thread(() -> {
                        JsonObject req = new JsonObject();
                        req.addProperty("action",    "GET_ACTIVE_BIDS_BY_BIDDER");
                        req.addProperty("bidder_id", myId);
                        JsonObject res = ServerConnection.sendAuthRequest(req);
                        if (res == null || !"success".equals(getString(res, "status", ""))) return;
                        JsonArray arr = res.get("message").getAsJsonArray();
                        for (JsonElement el : arr) {
                            JsonObject item = el.getAsJsonObject();
                            if (itemId.equals(getString(item, "itemId", getString(item, "item_id", "")))) {
                                Platform.runLater(() -> {
                                    if (vbox_leading_items == null) return;
                                    // Xóa label "chưa dẫn đầu" nếu còn đó
                                    vbox_leading_items.getChildren()
                                            .removeIf(n -> n.getUserData() == null);
                                    vbox_leading_items.getChildren().add(buildItemRow(item));
                                    updateLeadingCount();
                                });
                                break;
                            }
                        }
                    }, "Dashboard-AddLeadingItem").start();
                }
            });
            return;
        }

        // Người khác vừa bid — mình bị vượt qua → xóa item khỏi danh sách dẫn đầu
        Platform.runLater(() -> {
            if (vbox_leading_items == null) return;
            boolean removed = vbox_leading_items.getChildren()
                    .removeIf(node -> itemId.equals(node.getUserData()));
            if (removed) updateLeadingCount();
        });
    }

    /**
     * Phiên đấu giá kết thúc — xóa item khỏi danh sách vì không còn ACTIVE.
     */
    private void onAuctionSettled(JsonObject payload) {
        String itemId = getString(payload, "item_id", "");
        if (itemId.isEmpty() || vbox_leading_items == null) return;
        Platform.runLater(() -> {
            if (vbox_leading_items == null) return;
            vbox_leading_items.getChildren().removeIf(node -> itemId.equals(node.getUserData()));
            updateLeadingCount();
        });
    }

    /**
     * Item bị hủy — xóa khỏi danh sách dẫn đầu.
     */
    private void onItemCancelled(JsonObject payload) {
        String itemId = getString(payload, "item_id", "");
        if (itemId.isEmpty() || vbox_leading_items == null) return;
        Platform.runLater(() -> {
            if (vbox_leading_items == null) return;
            vbox_leading_items.getChildren().removeIf(node -> itemId.equals(node.getUserData()));
            updateLeadingCount();
        });
    }

    /** Cập nhật label đếm số phiên sau khi xóa row, hiện thông báo trống nếu danh sách rỗng. */
    private void updateLeadingCount() {
        if (lbl_leading_count == null || vbox_leading_items == null) return;
        long count = vbox_leading_items.getChildren().stream()
                .filter(n -> n.getUserData() != null)
                .count();
        lbl_leading_count.setText(count > 0 ? count + " phiên" : "");
        if (count == 0) {
            Label empty = new Label("Bạn chưa dẫn đầu phiên nào đang diễn ra.");
            empty.getStyleClass().add("leading-widget-empty");
            vbox_leading_items.getChildren().add(empty);
        }
    }

    private void updateBalanceLabel(double balance) {
        if (lbl_balance != null)
            lbl_balance.setText("Số dư tài khoản của bạn: " + String.format("%,.0f ₫", balance));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String getString(JsonObject o, String k, String fb) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull())
                ? o.get(k).getAsString() : fb;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    private void switch_scene(ActionEvent event, String fxml_path) {
        try { Scene_Utils.Change_Scene(event, fxml_path); }
        catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void Go_to_bidder_profile(ActionEvent event) {
        unregisterAll();
        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action",  "GET_PROFILE");
            request.addProperty("user_id", UserSession.getInstance().getUserId());
            JsonObject response = ServerConnection.sendAuthRequest(request);
            if (response != null && "success".equals(response.get("status").getAsString())) {
                JsonObject info = response.get("information").getAsJsonObject();
                UserSession s = UserSession.getInstance();
                s.setBalance(info.get("balance").getAsDouble());
                s.setPhone(info.has("phone") && !info.get("phone").isJsonNull()
                        ? info.get("phone").getAsString() : null);
                s.setRating(info.has("rating") && !info.get("rating").isJsonNull()
                        ? info.get("rating").getAsDouble() : null);
            }
            Platform.runLater(() -> switch_scene(event, Bidder_Profile_View));
        }, "Nav-BidderProfile").start();
    }

    @FXML public void Go_to_bidding_history(ActionEvent event)     { unregisterAll(); switch_scene(event, Bidding_History_View); }
    @FXML public void Go_to_bidding_result(ActionEvent event)      { unregisterAll(); switch_scene(event, Bidding_Result_View); }
    @FXML public void Go_to_search_user(ActionEvent event)         { unregisterAll(); switch_scene(event, Search_User_View); }
    @FXML public void Go_to_searching_room(ActionEvent event)      { unregisterAll(); switch_scene(event, Searching_Room_View); }
    @FXML public void Go_to_wallet(ActionEvent event)              { unregisterAll(); switch_scene(event, Wallet_View); }
    @FXML public void Go_to_transaction_history(ActionEvent event) { unregisterAll(); switch_scene(event, Transaction_History_View); }
    @FXML public void Go_to_my_items(ActionEvent event)            { unregisterAll(); switch_scene(event, My_Items_Bidder_View); }

    private void unregisterAll() {
        BalanceWatcher.unregisterListener("BidderDashboard");
        EventDispatcher.unregister(EventType.BID_PLACED);
        EventDispatcher.unregister(EventType.AUCTION_SETTLED);
        EventDispatcher.unregister(EventType.ITEM_DELETED);
        EventDispatcher.unregister(EventType.ITEM_CANCELLED);
    }

    @FXML
    public void Logging_out(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn chuẩn bị đăng xuất khỏi tài khoản này.");
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất?");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                JsonObject request = new JsonObject();
                request.addProperty("action", "LOGOUT");
                ServerConnection.sendAuthRequest(request);
                BanWatcher.deactivate();
                NotificationManager.deactivate();
                BalanceWatcher.deactivate();
                EventDispatcher.unregisterAll();
                UserSession.getInstance().clear();
                ServerConnection.disconnect();
                switch_scene(event, Login_View);
            }
        });
    }
}