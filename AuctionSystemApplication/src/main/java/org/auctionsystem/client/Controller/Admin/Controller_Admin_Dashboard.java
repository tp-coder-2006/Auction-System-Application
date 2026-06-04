package org.auctionsystem.client.Controller.Admin;

import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.BalanceWatcher;
import org.auctionsystem.client.event.BanWatcher;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.event.NotificationManager;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Controller_Admin_Dashboard  (CẬP NHẬT)
 * ─────────────────────────────────────────────────────────────────────────────
 * Thay đổi so với bản cũ:
 *
 *   1. Nhận TOÀN BỘ payload ADMIN_STATS_UPDATE thay vì chỉ 3 con số:
 *        - user_stats  → lbl_total_users, lbl_total_sellers, lbl_total_bidders,
 *                         lbl_active_users, lbl_banned_users
 *        - item_stats  → lbl_total_items, lbl_active_items, lbl_pending_items,
 *                         lbl_closed_cancelled_items
 *        - transaction_stats → lbl_total_transactions, lbl_total_revenue
 *
 *   2. Thêm điều hướng đến Admin_Stats_Detail.fxml (màn hình thống kê chi tiết).
 *
 *   3. Hiển thị thời gian cập nhật cuối trên label lbl_update_time.
 *
 * Cấu trúc event ADMIN_STATS_UPDATE nhận được:
 *   payload.data.system_stats.user_stats
 *   payload.data.system_stats.item_stats
 *   payload.data.system_stats.transaction_stats
 *   payload.data.system_stats.top_sellers
 *   payload.data.system_stats.top_bidders
 *   payload.data.item_trend
 *   payload.data.revenue_trend
 */
public class Controller_Admin_Dashboard {

    // ─── FXML: Sidebar ───────────────────────────────────────────────────────
    @FXML private VBox  sidebar;

    // ─── FXML: User Stats ────────────────────────────────────────────────────
    @FXML private Label lbl_total_users;
    @FXML private Label lbl_total_sellers;
    @FXML private Label lbl_total_bidders;
    @FXML private Label lbl_active_users;
    @FXML private Label lbl_banned_users;

    // ─── FXML: Item Stats ────────────────────────────────────────────────────
    @FXML private Label lbl_total_items;
    @FXML private Label lbl_active_items;
    @FXML private Label lbl_pending_items;
    @FXML private Label lbl_closed_cancelled_items;

    // ─── FXML: Transaction Stats ─────────────────────────────────────────────
    @FXML private Label lbl_total_transactions;
    @FXML private Label lbl_total_revenue;

    // ─── FXML: Header ────────────────────────────────────────────────────────
    @FXML private Label lbl_update_time;

    // ─── Navigation paths ────────────────────────────────────────────────────
    private static final String Admin_User_Management_View    = "/org/auctionsystem/client/View/Admin_User_Management.fxml";
    private static final String Admin_Financial_Auditing_View = "/org/auctionsystem/client/View/Admin_Financial_Management.fxml";
    private static final String Admin_Item_Management_View    = "/org/auctionsystem/client/View/Admin_Item_Management.fxml";
    private static final String Admin_Stats_Detail_View       = "/org/auctionsystem/client/View/Admin_Stats_Detail.fxml";
    private static final String Login_View                    = "/org/auctionsystem/client/View/Login_scene.fxml";

    private boolean isMenuExpanded = true;
    private final double expanded_width = 210.0;

    private static final NumberFormat CURRENCY_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Đăng ký nhận event real-time từ AdminStatsScheduler (push mỗi 30s + event-driven)
        EventDispatcher.register(EventType.ADMIN_STATS_UPDATE, this::onStatsUpdate);

        // Chủ động tải ngay khi mở dashboard (không chờ push đầu tiên)
        requestStatsFromServer();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Request-Response: GET_SYSTEM_STATS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gửi GET_SYSTEM_STATS lên server và cập nhật toàn bộ labels ngay lập tức.
     * Chạy trên background thread để không block JavaFX UI thread.
     */
    private void requestStatsFromServer() {
        new Thread(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("action", "GET_SYSTEM_STATS");
                JsonObject response = ServerConnection.sendAuthRequest(request);

                if (response == null || !"success".equals(response.get("status").getAsString())) {
                    System.err.println("[AdminDashboard] GET_SYSTEM_STATS thất bại.");
                    return;
                }

                JsonObject data = response.get("message").getAsJsonObject();
                javafx.application.Platform.runLater(() -> applySystemStats(data));

            } catch (Exception e) {
                System.err.println("[AdminDashboard] Lỗi requestStatsFromServer: " + e.getMessage());
            }
        }, "AdminDashboard-StatsRequest").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Xử lý event ADMIN_STATS_UPDATE (server push)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Nhận payload đầy đủ từ AdminStatsScheduler và cập nhật tất cả labels.
     *
     * Payload structure:
     *   payload.data.system_stats  → user_stats, item_stats, transaction_stats,
     *                                top_sellers, top_bidders
     *   payload.data.item_trend    → [ {month, count} ]
     *   payload.data.revenue_trend → [ {month, transactions, revenue} ]
     *
     * Dashboard chỉ dùng system_stats; item_trend và revenue_trend được
     * hiển thị chi tiết hơn tại Admin_Stats_Detail.
     */
    private void onStatsUpdate(JsonObject payload) {
        try {
            JsonObject systemStats = payload
                    .get("data").getAsJsonObject()
                    .get("system_stats").getAsJsonObject();

            javafx.application.Platform.runLater(() -> applySystemStats(systemStats));
        } catch (Exception e) {
            System.err.println("[AdminDashboard] Lỗi parse ADMIN_STATS_UPDATE: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Áp dụng system_stats lên tất cả labels
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param systemStats JSON gồm user_stats, item_stats, transaction_stats
     *                    (top_sellers, top_bidders không hiển thị ở đây — xem
     *                    Admin_Stats_Detail để thấy đầy đủ)
     */
    private void applySystemStats(JsonObject systemStats) {
        // ── User Stats ──
        if (systemStats.has("user_stats")) {
            JsonObject u = systemStats.get("user_stats").getAsJsonObject();
            safe(lbl_total_users,   String.valueOf(u.get("totalUsers").getAsLong()));
            safe(lbl_total_sellers, String.valueOf(u.get("totalSellers").getAsLong()));
            safe(lbl_total_bidders, String.valueOf(u.get("totalBidders").getAsLong()));
            safe(lbl_active_users,  String.valueOf(u.get("activeUsers").getAsLong()));
            safe(lbl_banned_users,  String.valueOf(u.get("bannedUsers").getAsLong()));
        }

        // ── Item Stats ──
        if (systemStats.has("item_stats")) {
            JsonObject i = systemStats.get("item_stats").getAsJsonObject();
            safe(lbl_total_items,   String.valueOf(i.get("totalItems").getAsLong()));
            safe(lbl_active_items,  String.valueOf(i.get("activeItems").getAsLong()));
            safe(lbl_pending_items, String.valueOf(i.get("pendingItems").getAsLong()));
            long closedAndCancelled = i.get("closedItems").getAsLong()
                    + i.get("cancelledItems").getAsLong();
            safe(lbl_closed_cancelled_items, String.valueOf(closedAndCancelled));
        }

        // ── Transaction Stats ──
        if (systemStats.has("transaction_stats")) {
            JsonObject t = systemStats.get("transaction_stats").getAsJsonObject();
            safe(lbl_total_transactions,
                    String.valueOf(t.get("totalTransactions").getAsLong()));
            safe(lbl_total_revenue,
                    CURRENCY_FMT.format((long) t.get("totalRevenue").getAsDouble()) + " ₫");
        }

        // ── Timestamp ──
        if (lbl_update_time != null) {
            String now = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"));
            lbl_update_time.setText("Cập nhật: " + now);
        }
    }

    /** Null-safe setText — tránh NPE nếu label chưa inject (ví dụ unit test). */
    private void safe(Label label, String text) {
        if (label != null) label.setText(text);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toggle sidebar
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void toggleMenu() {
        Timeline timeline = new Timeline();
        KeyValue keyValue;
        if (isMenuExpanded) {
            keyValue = new KeyValue(sidebar.prefWidthProperty(), 0);
            sidebar.getChildren().forEach(node -> node.setVisible(false));
        } else {
            keyValue = new KeyValue(sidebar.prefWidthProperty(), expanded_width);
            timeline.setOnFinished(event ->
                    sidebar.getChildren().forEach(node -> node.setVisible(true)));
        }
        KeyFrame keyFrame = new KeyFrame(Duration.millis(300), keyValue);
        timeline.getKeyFrames().add(keyFrame);

        if (isMenuExpanded) {
            timeline.setOnFinished(event -> isMenuExpanded = false);
        } else {
            isMenuExpanded = true;
        }
        timeline.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            Scene_Utils.Change_Scene(event, fxml_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void Go_to_admin_user_management(ActionEvent event) {
        EventDispatcher.unregister(EventType.ADMIN_STATS_UPDATE);
        switch_scene(event, Admin_User_Management_View);
    }

    @FXML
    public void Go_to_admin_financial_auditing(ActionEvent event) {
        EventDispatcher.unregister(EventType.ADMIN_STATS_UPDATE);
        switch_scene(event, Admin_Financial_Auditing_View);
    }

    @FXML
    public void Go_to_admin_item_management(ActionEvent event) {
        EventDispatcher.unregister(EventType.ADMIN_STATS_UPDATE);
        switch_scene(event, Admin_Item_Management_View);
    }

    @FXML
    public void Go_to_admin_stats_detail(ActionEvent event) {
        EventDispatcher.unregister(EventType.ADMIN_STATS_UPDATE);
        switch_scene(event, Admin_Stats_Detail_View);
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
                UserSession.getInstance().clear();
                ServerConnection.disconnect();

                switch_scene(event, Login_View);
            }
        });
    }
}