package org.auctionsystem.server;

import com.google.gson.JsonObject;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.ItemHistoryDAO;
import org.auctionsystem.server.DAO.UserDAO;
import org.auctionsystem.server.service.TransactionService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** AuctionScheduler — 2 thread vòng lặp cố định 200ms: PENDING→ACTIVE và ACTIVE→CLOSED. */
public class AuctionScheduler {

    private static volatile boolean running = false;

    private static Thread activateThread;
    private static Thread settleThread;


    // DAO/Service dùng chung — thread-safe vì các method đều tự lấy/trả connection
    private static final ItemDAO            itemDAO            = new ItemDAO();
    private static final ItemHistoryDAO     itemHistoryDAO     = new ItemHistoryDAO();
    private static final TransactionService transactionService = new TransactionService();
    private static final UserDAO            userDAO            = new UserDAO();

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi động / Dừng
    // ─────────────────────────────────────────────────────────────────────────

    public static void start() {
        if (running) return;
        running = true;

        activateThread = new Thread(AuctionScheduler::runActivateLoop, "Scheduler-Activate");
        settleThread   = new Thread(AuctionScheduler::runSettleLoop,   "Scheduler-Settle");

        activateThread.setDaemon(true);
        settleThread.setDaemon(true);

        activateThread.start();
        settleThread.start();

        System.out.println("[AuctionScheduler] Đã khởi động real-time.");
    }

    public static void stop() {
        running = false;
        if (activateThread != null) activateThread.interrupt();
        if (settleThread   != null) settleThread.interrupt();
        System.out.println("[AuctionScheduler] Đã dừng.");
    }

    // wakeUpActivate() đã bỏ — scheduler chạy cố định 200ms mỗi vòng — scheduler chạy cố định 200ms mỗi vòng

    // ─────────────────────────────────────────────────────────────────────────
    //  Thread 1: Activate loop — PENDING → ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    private static void runActivateLoop() {
        System.out.println("[Scheduler-Activate] Thread bắt đầu chạy.");
        while (running) {
            try {
                activatePendingItems();
                Thread.sleep(200);
            } catch (InterruptedException e) {
                if (!running) break;
                Thread.interrupted();
            } catch (Exception e) {
                System.err.println("[Scheduler-Activate] Lỗi: " + e.getMessage());
                try { Thread.sleep(200); } catch (InterruptedException ie) {
                    if (!running) break;
                    Thread.interrupted();
                }
            }
        }
        System.out.println("[Scheduler-Activate] Thread kết thúc.");
    }

    private static void runSettleLoop() {
        System.out.println("[Scheduler-Settle] Thread bắt đầu chạy.");
        while (running) {
            try {
                settleExpiredItems();
                Thread.sleep(200);
            } catch (InterruptedException e) {
                if (!running) break;
                Thread.interrupted();
            } catch (Exception e) {
                System.err.println("[Scheduler-Settle] Lỗi: " + e.getMessage());
                try { Thread.sleep(200); } catch (InterruptedException ie) {
                    if (!running) break;
                    Thread.interrupted();
                }
            }
        }
        System.out.println("[Scheduler-Settle] Thread kết thúc.");
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  [FIX #2] Kích hoạt PENDING → ACTIVE — UPDATE trước, SELECT sau
    //  (Logic transaction đặc thù — không có trong DAO, dùng connection trực tiếp)
    // ─────────────────────────────────────────────────────────────────────────

    private static void activatePendingItems() {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: SELECT id trước khi UPDATE (trong cùng transaction, dùng FOR UPDATE để lock).
                // DB không có cột updated_at nên không thể lọc lại bằng timestamp sau UPDATE.
                String selectIdSql = """
                    SELECT id FROM items
                    WHERE status = 'pending'
                      AND is_active = 1
                      AND start_time <= NOW(6)
                    FOR UPDATE
                    """;
                List<String> idsToActivate = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectIdSql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) idsToActivate.add(rs.getString("id"));
                }

                if (idsToActivate.isEmpty()) {
                    conn.rollback();
                    return;
                }

                // Bước 2: UPDATE đúng những id đó
                String placeholders = String.join(",",
                        java.util.Collections.nCopies(idsToActivate.size(), "?"));
                String updateSql = "UPDATE items SET status = 'active' WHERE id IN (" + placeholders + ")";
                int updated;
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    for (int i = 0; i < idsToActivate.size(); i++) ps.setString(i + 1, idsToActivate.get(i));
                    updated = ps.executeUpdate();
                }

                if (updated == 0) {
                    conn.rollback();
                    return;
                }

                // Bước 3: SELECT đầy đủ thông tin của đúng những item vừa activate
                String selectSql = "SELECT id, name, starting_price, end_time, seller_id FROM items WHERE id IN (" + placeholders + ")";
                List<JsonObject> activated = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    for (int i = 0; i < idsToActivate.size(); i++) ps.setString(i + 1, idsToActivate.get(i));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("item_id",        rs.getString("id"));
                            item.addProperty("name",           rs.getString("name"));
                            item.addProperty("starting_price", rs.getDouble("starting_price"));
                            item.addProperty("end_time",       rs.getString("end_time"));
                            item.addProperty("seller_id",      rs.getString("seller_id"));
                            activated.add(item);
                        }
                    }
                }

                conn.commit();
                System.out.println("[Scheduler-Activate] Kích hoạt " + updated + " item(s) → ACTIVE");

                // Broadcast ITEM_STARTED sau khi commit — chắc chắn DB đã ghi xong
                for (JsonObject item : activated) {
                    JsonObject event = new JsonObject();
                    event.addProperty("event",          EventType.ITEM_STARTED);
                    event.addProperty("item_id",        item.get("item_id").getAsString());
                    event.addProperty("name",           item.get("name").getAsString());
                    event.addProperty("starting_price", item.get("starting_price").getAsDouble());
                    event.addProperty("end_time",       item.get("end_time").getAsString());
                    event.addProperty("seller_id",      item.get("seller_id").getAsString());
                    ConnectedClientRegistry.broadcastAll(event);
                }

                AdminStatsScheduler.notifyStatsChanged();

            } catch (Exception e) {
                conn.rollback();
                System.err.println("[Scheduler-Activate] Lỗi activatePendingItems, rollback: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[Scheduler-Activate] Lỗi mở connection: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Settle ACTIVE → CLOSED
    // ─────────────────────────────────────────────────────────────────────────

    private static void settleExpiredItems() {
        // Dùng ItemDAO.getActiveItems() không lọc end_time — query thêm điều kiện expired
        String findSql = """
            SELECT id, seller_id, name
            FROM items
            WHERE status = 'active'
              AND is_active = 1
              AND end_time <= NOW(6)
            """;

        List<String[]> expiredItems = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(findSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                expiredItems.add(new String[]{ rs.getString("id"), rs.getString("seller_id"), rs.getString("name") });
            }
        } catch (Exception e) {
            System.err.println("[Scheduler-Settle] Lỗi query expired items: " + e.getMessage());
            return;
        }

        if (expiredItems.isEmpty()) return;
        System.out.println("[Scheduler-Settle] Settle " + expiredItems.size() + " item(s) hết giờ...");

        for (String[] pair : expiredItems) {
            settleOneItem(pair[0], pair[1], pair[2]);
        }
    }

    /**
     * [REFACTOR] Dùng DAO thay vì tự viết SQL cho balance/history/owner/cancel.
     * Chỉ giữ connection trực tiếp để quản lý transaction và lock FOR UPDATE.
     */
    private static void settleOneItem(String itemId, String sellerId, String itemName) {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock item row trước
                String lockItemSql = "SELECT id FROM items WHERE id = ? AND status = 'active' FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(lockItemSql)) {
                    ps.setString(1, itemId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        conn.rollback();
                        return; // Đã settle bởi luồng khác hoặc đã cancelled
                    }
                }

                // [FIX #3] Lock toàn bộ bids của item TRƯỚC khi đọc MAX
                String lockBidsSql = "SELECT bid_amount, bidder_id FROM bids WHERE item_id = ? ORDER BY bid_amount DESC LIMIT 1 FOR UPDATE";
                String winnerId  = null;
                double winAmount = 0;
                try (PreparedStatement ps = conn.prepareStatement(lockBidsSql)) {
                    ps.setString(1, itemId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        winnerId  = rs.getString("bidder_id");
                        winAmount = rs.getDouble("bid_amount");
                    }
                }

                if (winnerId == null) {
                    // Không có bid → UPDATE trực tiếp trên conn đang giữ lock, không mở connection mới
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE items SET status = 'cancelled' WHERE id = ? AND is_active = 1")) {
                        ps.setString(1, itemId);
                        ps.executeUpdate();
                    }
                    conn.commit();
                    System.out.println("[Scheduler-Settle] Hủy item " + itemId + " (không có winner).");

                    JsonObject event = new JsonObject();
                    event.addProperty("event",     EventType.ITEM_CANCELLED);
                    event.addProperty("item_id",   itemId);
                    event.addProperty("item_name", itemName);
                    event.addProperty("seller_id", sellerId);
                    ConnectedClientRegistry.broadcastAll(event);
                    AdminStatsScheduler.notifyStatsChanged();
                    return;
                }

                // Kiểm tra is_active của winner và seller trước khi settle
                boolean winnerActive = userDAO.isActiveById(winnerId, conn);
                boolean sellerActive = userDAO.isActiveById(sellerId, conn);

                if (!winnerActive || !sellerActive) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE items SET status = 'cancelled' WHERE id = ? AND is_active = 1")) {
                        ps.setString(1, itemId);
                        ps.executeUpdate();
                    }
                    conn.commit();

                    String reason = !winnerActive
                            ? "winner " + winnerId + " bị deactivate"
                            : "seller " + sellerId + " bị deactivate";
                    System.out.println("[Scheduler-Settle] Hủy item " + itemId + " (" + reason + ").");

                    JsonObject event = new JsonObject();
                    event.addProperty("event",     EventType.ITEM_CANCELLED);
                    event.addProperty("item_id",   itemId);
                    event.addProperty("item_name", itemName);
                    event.addProperty("seller_id", sellerId);
                    ConnectedClientRegistry.broadcastAll(event);
                    AdminStatsScheduler.notifyStatsChanged();
                    return;
                }

                // Trừ tiền bidder + cộng tiền seller + ghi 2 transaction — dùng Service
                double[] balances = transactionService.settleTransfer(
                        conn, winnerId, sellerId, winAmount, itemId, itemName);

                if (balances == null) {
                    // Số dư không đủ hoặc lỗi → hủy item
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE items SET status = 'cancelled' WHERE id = ? AND is_active = 1")) {
                        ps.setString(1, itemId);
                        ps.executeUpdate();
                    }
                    conn.commit();
                    System.out.println("[Scheduler-Settle] Hủy item " + itemId
                            + " (winner " + winnerId + " không đủ số dư).");

                    JsonObject event = new JsonObject();
                    event.addProperty("event",     EventType.ITEM_CANCELLED);
                    event.addProperty("item_id",   itemId);
                    event.addProperty("item_name", itemName);
                    event.addProperty("seller_id", sellerId);
                    ConnectedClientRegistry.broadcastAll(event);
                    AdminStatsScheduler.notifyStatsChanged();
                    return;
                }

                // Đổi owner, đóng item — dùng ItemDAO.updateOwner(conn, itemId, winnerId)
                // (method này cũng SET status = 'closed')
                itemDAO.updateOwner(conn, itemId, winnerId);

                // Cập nhật current_highest_price
                String updatePriceSql = "UPDATE items SET current_highest_price = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updatePriceSql)) {
                    ps.setDouble(1, winAmount);
                    ps.setString(2, itemId);
                    ps.executeUpdate();
                }

                // Ghi lịch sử — dùng ItemHistoryDAO.addHistory(conn, ...)
                itemHistoryDAO.addHistory(conn, itemId, sellerId, winnerId, winAmount);

                // Query username của winner trước commit — trong cùng transaction
                String winnerName = winnerId;
                String nameSql = "SELECT username FROM users WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(nameSql)) {
                    ps.setString(1, winnerId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) winnerName = rs.getString("username");
                }

                conn.commit();
                System.out.println("[Scheduler-Settle] Settled item " + itemId
                        + " → winner: " + winnerId + ", amount: " + winAmount);

                // Broadcast AUCTION_SETTLED cho tất cả client
                JsonObject event = new JsonObject();
                event.addProperty("event",       EventType.AUCTION_SETTLED);
                event.addProperty("item_id",     itemId);
                event.addProperty("item_name",   itemName);
                event.addProperty("seller_id",   sellerId);
                event.addProperty("bidder_id",   winnerId);
                event.addProperty("bidder_name", winnerName);
                event.addProperty("amount",      winAmount);
                ConnectedClientRegistry.broadcastAll(event);

                // Broadcast BID_DEDUCT → bidder, BID_CREDIT → seller (real-time transaction history)
                transactionService.broadcastSettleEvents(
                        winnerId, sellerId, winAmount, itemId, itemName,
                        balances[0], balances[1]);

                AdminStatsScheduler.notifyStatsChanged();

            } catch (Exception e) {
                try { conn.rollback(); } catch (Exception ignored) {}
                System.err.println("[Scheduler-Settle] Lỗi settleOneItem(" + itemId + "): " + e.getMessage());
            } finally {
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[Scheduler-Settle] Lỗi mở connection cho item " + itemId + ": " + e.getMessage());
        }
    }
}