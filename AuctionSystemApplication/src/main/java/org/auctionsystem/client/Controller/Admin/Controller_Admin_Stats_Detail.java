package org.auctionsystem.client.Controller.Admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Controller_Admin_Stats_Detail
 * ─────────────────────────────────────────────────────────────────────────────
 * Màn hình thống kê chi tiết toàn hệ thống cho Admin.
 *
 * Nhận dữ liệu realtime từ event ADMIN_STATS_UPDATE (server push mỗi 30 giây
 * hoặc ngay sau khi có sự kiện thay đổi), và cũng có thể chủ động yêu cầu
 * GET_SYSTEM_STATS + GET_ITEM_TREND + GET_REVENUE_TREND từ server.
 *
 * Cấu trúc payload ADMIN_STATS_UPDATE từ AdminStatsScheduler:
 * {
 *   "event": "ADMIN_STATS_UPDATE",
 *   "data": {
 *     "system_stats": {
 *       "user_stats":        { totalUsers, totalSellers, totalBidders, activeUsers, bannedUsers }
 *       "item_stats":        { totalItems, pendingItems, activeItems, closedItems, cancelledItems, hiddenItems }
 *       "transaction_stats": { totalTransactions, totalRevenue, avgSoldPrice, maxSoldPrice, minSoldPrice }
 *       "top_sellers":       [ { rank, id, username, name, rating, totalSold, totalRevenue, avatarUrl } ]
 *       "top_bidders":       [ { rank, id, username, name, totalBids, totalWins, avatarUrl } ]
 *     },
 *     "item_trend":    [ { month, count } ]
 *     "revenue_trend": [ { month, transactions, revenue } ]
 *   }
 * }
 */
public class Controller_Admin_Stats_Detail {
    // UUID duy nhất cho mỗi instance — tránh ghi đè handler của cửa sổ khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    // ─── Labels: User Stats ───────────────────────────────────────────────────
    @FXML private Label lbl_total_users;
    @FXML private Label lbl_total_sellers;
    @FXML private Label lbl_total_bidders;
    @FXML private Label lbl_active_users;
    @FXML private Label lbl_banned_users;

    // ─── Labels: Item Stats ───────────────────────────────────────────────────
    @FXML private Label lbl_total_items;
    @FXML private Label lbl_pending_items;
    @FXML private Label lbl_active_items;
    @FXML private Label lbl_closed_items;
    @FXML private Label lbl_cancelled_items;
    @FXML private Label lbl_hidden_items;

    // ─── Labels: Transaction Stats ────────────────────────────────────────────
    @FXML private Label lbl_total_transactions;
    @FXML private Label lbl_total_revenue;
    @FXML private Label lbl_avg_price;
    @FXML private Label lbl_max_price;
    @FXML private Label lbl_min_price;

    // ─── TableView: Top Sellers ───────────────────────────────────────────────
    @FXML private TableView<SellerRow>              tbl_top_sellers;
    @FXML private TableColumn<SellerRow, Integer>   col_seller_rank;
    @FXML private TableColumn<SellerRow, String>    col_seller_name;
    @FXML private TableColumn<SellerRow, Integer>   col_seller_sold;
    @FXML private TableColumn<SellerRow, String>    col_seller_revenue;
    @FXML private TableColumn<SellerRow, String>    col_seller_rating;

    // ─── TableView: Top Bidders ───────────────────────────────────────────────
    @FXML private TableView<BidderRow>              tbl_top_bidders;
    @FXML private TableColumn<BidderRow, Integer>   col_bidder_rank;
    @FXML private TableColumn<BidderRow, String>    col_bidder_name;
    @FXML private TableColumn<BidderRow, Integer>   col_bidder_total_bids;
    @FXML private TableColumn<BidderRow, Integer>   col_bidder_wins;
    @FXML private TableColumn<BidderRow, String>    col_bidder_winrate;

    // ─── Trend Bars ───────────────────────────────────────────────────────────
    @FXML private HBox hbox_item_trend;
    @FXML private HBox hbox_item_month_labels;
    @FXML private HBox hbox_revenue_trend;
    @FXML private HBox hbox_revenue_month_labels;

    // ─── Header ───────────────────────────────────────────────────────────────
    @FXML private Label lbl_last_updated;

    // ─── Internal data ────────────────────────────────────────────────────────
    private final ObservableList<SellerRow>  sellerRows  = FXCollections.observableArrayList();
    private final ObservableList<BidderRow>  bidderRows  = FXCollections.observableArrayList();
    private static final NumberFormat CURRENCY_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final String ADMIN_DASHBOARD_VIEW =
            "/org/auctionsystem/client/View/Admin_Dashboard.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupTables();

        // Đăng ký nhận event real-time từ AdminStatsScheduler
        EventDispatcher.registerGlobal(EventType.ADMIN_STATS_UPDATE, handlerKey, this::onStatsUpdate);

        // Chủ động tải ngay khi mở màn hình
        loadAllStats();
    }

    private void setupTables() {
        // Top Sellers
        col_seller_rank   .setCellValueFactory(new PropertyValueFactory<>("rank"));
        col_seller_name   .setCellValueFactory(new PropertyValueFactory<>("displayName"));
        col_seller_sold   .setCellValueFactory(new PropertyValueFactory<>("totalSold"));
        col_seller_revenue.setCellValueFactory(new PropertyValueFactory<>("revenueFormatted"));
        col_seller_rating .setCellValueFactory(new PropertyValueFactory<>("ratingFormatted"));
        tbl_top_sellers.setItems(sellerRows);
        tbl_top_sellers.setPlaceholder(new Label("Đang tải dữ liệu..."));

        // Top Bidders
        col_bidder_rank      .setCellValueFactory(new PropertyValueFactory<>("rank"));
        col_bidder_name      .setCellValueFactory(new PropertyValueFactory<>("displayName"));
        col_bidder_total_bids.setCellValueFactory(new PropertyValueFactory<>("totalBids"));
        col_bidder_wins      .setCellValueFactory(new PropertyValueFactory<>("totalWins"));
        col_bidder_winrate   .setCellValueFactory(new PropertyValueFactory<>("winRateFormatted"));
        tbl_top_bidders.setItems(bidderRows);
        tbl_top_bidders.setPlaceholder(new Label("Đang tải dữ liệu..."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load dữ liệu từ server (Request-Response)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tải đồng thời 3 loại thống kê: system_stats, item_trend, revenue_trend.
     */
    private void loadAllStats() {
        lbl_last_updated.setText("⏳ Đang tải...");

        new Thread(() -> {
            try {
                // 1. GET_SYSTEM_STATS
                JsonObject reqStats = new JsonObject();
                reqStats.addProperty("action", "GET_SYSTEM_STATS");
                JsonObject respStats = ServerConnection.sendAuthRequest(reqStats);

                // 2. GET_ITEM_TREND
                JsonObject reqItemTrend = new JsonObject();
                reqItemTrend.addProperty("action", "GET_ITEM_TREND");
                reqItemTrend.addProperty("months", 6);
                JsonObject respItemTrend = ServerConnection.sendAuthRequest(reqItemTrend);

                // 3. GET_REVENUE_TREND
                JsonObject reqRevTrend = new JsonObject();
                reqRevTrend.addProperty("action", "GET_REVENUE_TREND");
                reqRevTrend.addProperty("months", 6);
                JsonObject respRevTrend = ServerConnection.sendAuthRequest(reqRevTrend);

                Platform.runLater(() -> {
                    if (respStats != null && "success".equals(
                            respStats.get("status").getAsString())) {
                        applySystemStats(respStats.get("message").getAsJsonObject());
                    }
                    if (respItemTrend != null && "success".equals(
                            respItemTrend.get("status").getAsString())) {
                        renderItemTrend(respItemTrend.get("message").getAsJsonArray());
                    }
                    if (respRevTrend != null && "success".equals(
                            respRevTrend.get("status").getAsString())) {
                        renderRevenueTrend(respRevTrend.get("message").getAsJsonArray());
                    }
                    updateTimestamp();
                });

            } catch (Exception e) {
                System.err.println("[StatsDetail] loadAllStats lỗi: " + e.getMessage());
                Platform.runLater(() -> lbl_last_updated.setText("❌ Lỗi tải dữ liệu!"));
            }
        }, "StatsDetail-Load").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Nhận event realtime ADMIN_STATS_UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Được gọi mỗi khi server push ADMIN_STATS_UPDATE.
     * Payload:
     *   data.system_stats  → user_stats, item_stats, transaction_stats, top_sellers, top_bidders
     *   data.item_trend    → [ { month, count } ]
     *   data.revenue_trend → [ { month, transactions, revenue } ]
     */
    private void onStatsUpdate(JsonObject payload) {
        try {
            JsonObject data        = payload.get("data").getAsJsonObject();
            JsonObject systemStats = data.get("system_stats").getAsJsonObject();
            JsonArray  itemTrend   = data.get("item_trend").getAsJsonArray();
            JsonArray  revTrend    = data.get("revenue_trend").getAsJsonArray();

            Platform.runLater(() -> {
                applySystemStats(systemStats);
                renderItemTrend(itemTrend);
                renderRevenueTrend(revTrend);
                updateTimestamp();
            });
        } catch (Exception e) {
            System.err.println("[StatsDetail] onStatsUpdate lỗi: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Áp dụng system_stats lên UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param systemStats JSON gồm: user_stats, item_stats, transaction_stats,
     *                    top_sellers, top_bidders
     */
    private void applySystemStats(JsonObject systemStats) {
        // ── User Stats ──
        if (systemStats.has("user_stats")) {
            JsonObject u = systemStats.get("user_stats").getAsJsonObject();
            lbl_total_users   .setText(String.valueOf(u.get("totalUsers").getAsLong()));
            lbl_total_sellers .setText(String.valueOf(u.get("totalSellers").getAsLong()));
            lbl_total_bidders .setText(String.valueOf(u.get("totalBidders").getAsLong()));
            lbl_active_users  .setText(String.valueOf(u.get("activeUsers").getAsLong()));
            lbl_banned_users  .setText(String.valueOf(u.get("bannedUsers").getAsLong()));
        }

        // ── Item Stats ──
        if (systemStats.has("item_stats")) {
            JsonObject i = systemStats.get("item_stats").getAsJsonObject();
            lbl_total_items    .setText(String.valueOf(i.get("totalItems").getAsLong()));
            lbl_pending_items  .setText(String.valueOf(i.get("pendingItems").getAsLong()));
            lbl_active_items   .setText(String.valueOf(i.get("activeItems").getAsLong()));
            lbl_closed_items   .setText(String.valueOf(i.get("closedItems").getAsLong()));
            lbl_cancelled_items.setText(String.valueOf(i.get("cancelledItems").getAsLong()));
            lbl_hidden_items   .setText(String.valueOf(i.get("hiddenItems").getAsLong()));
        }

        // ── Transaction Stats ──
        if (systemStats.has("transaction_stats")) {
            JsonObject t = systemStats.get("transaction_stats").getAsJsonObject();
            lbl_total_transactions.setText(String.valueOf(t.get("totalTransactions").getAsLong()));
            lbl_total_revenue.setText(formatCurrency(t.get("totalRevenue").getAsDouble()));
            lbl_avg_price    .setText(formatCurrency(t.get("avgSoldPrice").getAsDouble()));
            lbl_max_price    .setText(formatCurrency(t.get("maxSoldPrice").getAsDouble()));
            lbl_min_price    .setText(formatCurrency(t.get("minSoldPrice").getAsDouble()));
        }

        // ── Top Sellers ──
        if (systemStats.has("top_sellers")) {
            JsonArray arr = systemStats.get("top_sellers").getAsJsonArray();
            sellerRows.clear();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                double rating = o.get("rating").isJsonNull() ? 0.0
                        : o.get("rating").getAsDouble();
                sellerRows.add(new SellerRow(
                        o.get("rank").getAsInt(),
                        o.get("username").getAsString(),
                        o.get("name").getAsString(),
                        o.get("totalSold").getAsInt(),
                        o.get("totalRevenue").getAsDouble(),
                        rating
                ));
            }
        }

        // ── Top Bidders ──
        if (systemStats.has("top_bidders")) {
            JsonArray arr = systemStats.get("top_bidders").getAsJsonArray();
            bidderRows.clear();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                bidderRows.add(new BidderRow(
                        o.get("rank").getAsInt(),
                        o.get("username").getAsString(),
                        o.get("name").getAsString(),
                        o.get("totalBids").getAsInt(),
                        o.get("totalWins").getAsInt()
                ));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Vẽ Bar Chart đơn giản bằng VBox (item_trend)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Vẽ biểu đồ cột item_trend lên hbox_item_trend.
     * Mỗi cột = 1 VBox có chiều cao tỉ lệ với giá trị.
     * @param arr [ { month: "2025-01", count: 12 }, ... ]
     */
    private void renderItemTrend(JsonArray arr) {
        hbox_item_trend.getChildren().clear();
        hbox_item_month_labels.getChildren().clear();

        if (arr == null || arr.size() == 0) {
            hbox_item_trend.getChildren().add(
                    new Label("Không có dữ liệu") {{ setStyle("-fx-text-fill:#aaa;"); }});
            return;
        }

        // Tìm max để scale
        long maxCount = 1;
        for (JsonElement el : arr) {
            long c = el.getAsJsonObject().get("count").getAsLong();
            if (c > maxCount) maxCount = c;
        }

        double maxBarHeight = 110.0;

        for (JsonElement el : arr) {
            JsonObject o     = el.getAsJsonObject();
            String month     = o.get("month").getAsString();
            long   count     = o.get("count").getAsLong();
            double barHeight = maxCount > 0 ? (count * maxBarHeight / maxCount) : 4;
            barHeight = Math.max(barHeight, 4); // tối thiểu 4px

            // Cột
            VBox bar = new VBox();
            bar.setAlignment(javafx.geometry.Pos.BOTTOM_CENTER);
            bar.setPrefWidth(55);
            bar.setMinHeight(maxBarHeight);

            VBox fill = new VBox();
            fill.setPrefHeight(barHeight);
            fill.setPrefWidth(40);
            fill.setStyle("-fx-background-color:#2196F3; -fx-background-radius:4 4 0 0;");
            fill.setAlignment(javafx.geometry.Pos.TOP_CENTER);

            Label countLbl = new Label(String.valueOf(count));
            countLbl.setStyle("-fx-font-size:10px; -fx-font-weight:bold; -fx-text-fill:#1565C0;");
            countLbl.setPadding(new javafx.geometry.Insets(0, 0, 2, 0));

            bar.getChildren().addAll(countLbl, fill);
            hbox_item_trend.getChildren().add(bar);

            // Nhãn tháng
            Label monthLbl = new Label(shortMonth(month));
            monthLbl.setPrefWidth(55);
            monthLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#555; -fx-alignment:CENTER;");
            hbox_item_month_labels.getChildren().add(monthLbl);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Vẽ Bar Chart revenue_trend
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Vẽ biểu đồ cột revenue_trend.
     * @param arr [ { month: "2025-01", transactions: 3, revenue: 150000.0 }, ... ]
     */
    private void renderRevenueTrend(JsonArray arr) {
        hbox_revenue_trend.getChildren().clear();
        hbox_revenue_month_labels.getChildren().clear();

        if (arr == null || arr.size() == 0) {
            hbox_revenue_trend.getChildren().add(
                    new Label("Không có dữ liệu") {{ setStyle("-fx-text-fill:#aaa;"); }});
            return;
        }

        double maxRevenue = 1.0;
        for (JsonElement el : arr) {
            double r = el.getAsJsonObject().get("revenue").getAsDouble();
            if (r > maxRevenue) maxRevenue = r;
        }

        double maxBarHeight = 110.0;

        for (JsonElement el : arr) {
            JsonObject o     = el.getAsJsonObject();
            String month     = o.get("month").getAsString();
            double revenue   = o.get("revenue").getAsDouble();
            long   txCount   = o.get("transactions").getAsLong();
            double barHeight = maxRevenue > 0 ? (revenue * maxBarHeight / maxRevenue) : 4;
            barHeight = Math.max(barHeight, 4);

            VBox bar = new VBox();
            bar.setAlignment(javafx.geometry.Pos.BOTTOM_CENTER);
            bar.setPrefWidth(60);
            bar.setMinHeight(maxBarHeight);

            VBox fill = new VBox();
            fill.setPrefHeight(barHeight);
            fill.setPrefWidth(44);
            fill.setStyle("-fx-background-color:#4CAF50; -fx-background-radius:4 4 0 0;");

            Label revLbl = new Label(shortAmount(revenue));
            revLbl.setStyle("-fx-font-size:9px; -fx-font-weight:bold; -fx-text-fill:#2E7D32;");
            revLbl.setPadding(new javafx.geometry.Insets(0, 0, 1, 0));

            Label txLbl = new Label(txCount + " GD");
            txLbl.setStyle("-fx-font-size:9px; -fx-text-fill:#666;");

            bar.getChildren().addAll(revLbl, txLbl, fill);
            hbox_revenue_trend.getChildren().add(bar);

            Label monthLbl = new Label(shortMonth(month));
            monthLbl.setPrefWidth(60);
            monthLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#555; -fx-alignment:CENTER;");
            hbox_revenue_month_labels.getChildren().add(monthLbl);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updateTimestamp() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
        lbl_last_updated.setText("Cập nhật: " + now);
    }

    private String formatCurrency(double amount) {
        return CURRENCY_FMT.format((long) amount) + " ₫";
    }

    /** Rút gọn số tiền lớn: 1.500.000 → "1.5M" */
    private String shortAmount(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)         return String.format("%.0fK", amount / 1_000);
        return String.valueOf((long) amount);
    }

    /** "2025-01" → "T1/25" */
    private String shortMonth(String month) {
        try {
            String[] parts = month.split("-");
            return "T" + Integer.parseInt(parts[1]) + "/" + parts[0].substring(2);
        } catch (Exception e) {
            return month;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Actions
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onRefresh() {
        loadAllStats();
    }

    @FXML
    private void goBack(ActionEvent event) {
        EventDispatcher.unregisterGlobal(EventType.ADMIN_STATS_UPDATE, handlerKey);
        try {
            Scene_Utils.Change_Scene(event, ADMIN_DASHBOARD_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inner Model Classes — dùng cho TableView
    // ─────────────────────────────────────────────────────────────────────────

    /** Dòng dữ liệu cho bảng Top Sellers */
    public static class SellerRow {
        private final int    rank;
        private final String displayName;
        private final int    totalSold;
        private final String revenueFormatted;
        private final String ratingFormatted;

        public SellerRow(int rank, String username, String name,
                         int totalSold, double totalRevenue, double rating) {
            this.rank             = rank;
            this.displayName      = name + " (@" + username + ")";
            this.totalSold        = totalSold;
            this.revenueFormatted = NumberFormat.getNumberInstance(new Locale("vi", "VN"))
                    .format((long) totalRevenue) + " ₫";
            this.ratingFormatted  = rating > 0 ? String.format("%.1f ⭐", rating) : "—";
        }

        public int    getRank()             { return rank; }
        public String getDisplayName()      { return displayName; }
        public int    getTotalSold()        { return totalSold; }
        public String getRevenueFormatted() { return revenueFormatted; }
        public String getRatingFormatted()  { return ratingFormatted; }
    }

    /** Dòng dữ liệu cho bảng Top Bidders */
    public static class BidderRow {
        private final int    rank;
        private final String displayName;
        private final int    totalBids;
        private final int    totalWins;
        private final String winRateFormatted;

        public BidderRow(int rank, String username, String name,
                         int totalBids, int totalWins) {
            this.rank             = rank;
            this.displayName      = name + " (@" + username + ")";
            this.totalBids        = totalBids;
            this.totalWins        = totalWins;
            double rate = totalBids > 0 ? (totalWins * 100.0 / totalBids) : 0;
            this.winRateFormatted = String.format("%.0f%%", rate);
        }

        public int    getRank()             { return rank; }
        public String getDisplayName()      { return displayName; }
        public int    getTotalBids()        { return totalBids; }
        public int    getTotalWins()        { return totalWins; }
        public String getWinRateFormatted() { return winRateFormatted; }
    }
}