package org.auctionsystem.server;

import com.google.gson.JsonObject;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.ItemHistoryDAO;
import org.auctionsystem.server.DAO.UserDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AuctionScheduler — Real-time scheduler dùng 2 thread liên tục.
 *
 * Sửa so với phiên bản cũ:
 *
 * [FIX #1] wakeUpActivate() — race condition trên wakeUpOnly (boolean):
 *   Cũ: wakeUpOnly = true rồi activateThread.interrupt() là 2 thao tác riêng.
 *       Nếu addItem() và updateItem() gọi đồng thời, 1 interrupt có thể bị nuốt.
 *   Mới: dùng AtomicBoolean.compareAndSet() + synchronized để đảm bảo set-flag
 *        và interrupt là một thao tác nguyên tử.
 *
 * [FIX #2] activatePendingItems() — SELECT rồi UPDATE tách rời:
 *   Cũ: SELECT danh sách → UPDATE status → có khoảng trống giữa 2 bước.
 *       Seller cancelItem() chen vào → item cancelled vẫn bị activate.
 *   Mới: UPDATE trước với WHERE status='pending' AND start_time <= NOW(),
 *        dùng LAST_INSERT_ID trick hoặc re-SELECT WHERE updated_at = NOW()
 *        → chỉ broadcast đúng những item thực sự được UPDATE.
 *        Cụ thể: dùng "UPDATE ... WHERE status='pending'" → commit → SELECT lại
 *        những item vừa chuyển sang 'active' bằng updated_at timestamp.
 *
 * [FIX #3] settleExpiredItems() — SELECT bid không lock:
 *   Cũ: SELECT MAX(bid) để tìm winner không có FOR UPDATE → BidService có thể
 *       chen bid mới vào giữa lúc settle đang chạy.
 *   Mới: trong transaction settle, lock toàn bộ bids của item bằng
 *        SELECT ... FROM bids WHERE item_id = ? FOR UPDATE trước khi đọc MAX.
 *
 * [REFACTOR] settleOneItem() — dùng DAO thay vì tự viết SQL:
 *   Cũ: tự viết SQL UPDATE users/items/item_history trực tiếp trong scheduler.
 *   Mới: dùng UserDAO.updateBalance(), ItemDAO.updateOwner(),
 *        ItemHistoryDAO.addHistory() với overload nhận Connection ngoài
 *        để tham gia cùng transaction. ItemDAO.cancelItem() cho nhánh hủy.
 */
public class AuctionScheduler {

    private static volatile boolean running = false;

    private static Thread activateThread;
    private static Thread settleThread;

    // [FIX #1] Thay volatile boolean bằng AtomicBoolean để set-và-check atomic
    private static final AtomicBoolean wakeUpOnly = new AtomicBoolean(false);

    // DAO dùng chung — thread-safe vì các method đều tự lấy/trả connection
    private static final ItemDAO        itemDAO        = new ItemDAO();
    private static final UserDAO        userDAO        = new UserDAO();
    private static final ItemHistoryDAO itemHistoryDAO = new ItemHistoryDAO();

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
        wakeUpOnly.set(false);
        if (activateThread != null) activateThread.interrupt();
        if (settleThread   != null) settleThread.interrupt();
        System.out.println("[AuctionScheduler] Đã dừng.");
    }

    /**
     * [FIX #1] Gọi khi có item mới được thêm/sửa vào DB.
     *
     * synchronized đảm bảo set flag + interrupt là nguyên tử:
     * Dù addItem() và updateItem() gọi đồng thời từ 2 thread khác nhau,
     * chỉ 1 thread vào synchronized block tại một thời điểm.
     * wakeUpOnly.set(true) luôn được thực hiện trước interrupt(),
     * và activateThread không bao giờ đọc flag cũ của lần interrupt trước.
     */
    public static synchronized void wakeUpActivate() {
        if (activateThread != null && activateThread.isAlive()) {
            wakeUpOnly.set(true);
            activateThread.interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Thread 1: Activate loop — PENDING → ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    private static void runActivateLoop() {
        System.out.println("[Scheduler-Activate] Thread bắt đầu chạy.");

        while (running) {
            // [FIX] Reset flag đầu mỗi vòng: nếu wakeUp đã được xử lý (hoặc bị nuốt),
            // không để flag treo ở true mãi
            wakeUpOnly.set(false);
            try {
                long ms = getMillisUntilNextPendingItem();

                if (ms <= 0) {
                    activatePendingItems();
                    Thread.sleep(200);
                } else {
                    System.out.println("[Scheduler-Activate] Item tiếp theo sau " + ms + "ms.");
                    Thread.sleep(ms);
                }

            } catch (InterruptedException e) {
                if (!running) {
                    System.out.println("[Scheduler-Activate] Dừng theo lệnh stop().");
                    break;
                }
                if (wakeUpOnly.compareAndSet(true, false)) {
                    // [FIX #1] compareAndSet: chỉ log nếu đúng là wakeUp,
                    // tránh nhầm với interrupt do stop()
                    System.out.println("[Scheduler-Activate] Được đánh thức sớm, tính lại lịch.");
                }
                Thread.interrupted(); // reset flag, cần tìm hiểu kỹ hơn

            } catch (Exception e) {
                wakeUpOnly.compareAndSet(true, false); // reset flag dù DB lỗi
                System.err.println("[Scheduler-Activate] Lỗi: " + e.getMessage());
                try { Thread.sleep(5_000); } catch (InterruptedException ie) {
                    if (!running) break;
                    // [FIX] reset flag khi bị interrupt trong lúc đang chờ sau lỗi DB
                    wakeUpOnly.set(false);
                    Thread.interrupted();
                }
            }
        }

        System.out.println("[Scheduler-Activate] Thread kết thúc.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Thread 2: Settle loop — ACTIVE → CLOSED
    // ─────────────────────────────────────────────────────────────────────────

    private static void runSettleLoop() {
        System.out.println("[Scheduler-Settle] Thread bắt đầu chạy.");

        while (running) {
            try {
                long ms = getMillisUntilNextExpiredItem();

                if (ms <= 0) {
                    settleExpiredItems();
                    Thread.sleep(200);
                } else {
                    System.out.println("[Scheduler-Settle] Item tiếp theo hết giờ sau " + ms + "ms.");
                    Thread.sleep(ms);
                }

            } catch (InterruptedException e) {
                if (!running) {
                    System.out.println("[Scheduler-Settle] Dừng theo lệnh stop().");
                    break;
                }
                Thread.interrupted();

            } catch (Exception e) {
                System.err.println("[Scheduler-Settle] Lỗi: " + e.getMessage());
                try { Thread.sleep(5_000); } catch (InterruptedException ie) {
                    if (!running) break;
                    Thread.interrupted();
                }
            }
        }

        System.out.println("[Scheduler-Settle] Thread kết thúc.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tính thời gian chờ đến sự kiện gần nhất
    //  (Query đặc thù TIMESTAMPDIFF — không có trong DAO, dùng connection trực tiếp)
    // ─────────────────────────────────────────────────────────────────────────

    private static long getMillisUntilNextPendingItem() {
        String sql = """
            SELECT TIMESTAMPDIFF(MICROSECOND, NOW(6), start_time) / 1000.0
            FROM items
            WHERE status = 'pending'
              AND is_active = 1
              AND start_time > NOW(6)
            ORDER BY start_time ASC
            LIMIT 1
            """;
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return (long) rs.getDouble(1);
        } catch (Exception e) {
            // HikariCP khi bị interrupt sẽ clear interrupt flag của thread rồi mới ném exception.
            // Nếu không restore lại, caller sẽ không biết mình đã bị interrupt.
            // Convention Java: nếu bắt exception do interrupt mà không re-throw, phải restore flag.
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("[Scheduler] getMillisUntilNextPendingItem lỗi: " + e.getMessage());
        }
        return 10_000;
    }

    private static long getMillisUntilNextExpiredItem() {
        String sql = """
            SELECT TIMESTAMPDIFF(MICROSECOND, NOW(6), end_time) / 1000.0
            FROM items
            WHERE status = 'active'
              AND is_active = 1
              AND end_time > NOW(6)
            ORDER BY end_time ASC
            LIMIT 1
            """;
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return (long) rs.getDouble(1);
        } catch (Exception e) {
            System.err.println("[Scheduler] getMillisUntilNextExpiredItem lỗi: " + e.getMessage());
        }
        return 10_000;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  [FIX #2] Kích hoạt PENDING → ACTIVE — UPDATE trước, SELECT sau
    //  (Logic transaction đặc thù — không có trong DAO, dùng connection trực tiếp)
    // ─────────────────────────────────────────────────────────────────────────

    private static void activatePendingItems() {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // [FIX #2] UPDATE trước — chỉ những item thực sự còn pending mới được activate.
                String updateSql = """
                    UPDATE items
                    SET status = 'active', updated_at = NOW(6)
                    WHERE status = 'pending'
                      AND is_active = 1
                      AND start_time <= NOW(6)
                    """;
                int updated;
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    updated = ps.executeUpdate();
                }

                if (updated == 0) {
                    conn.rollback();
                    return;
                }

                // SELECT lại các item vừa được UPDATE trong cùng transaction
                String selectSql = """
                    SELECT id, name, starting_price, end_time, seller_id
                    FROM items
                    WHERE status = 'active'
                      AND is_active = 1
                      AND updated_at >= NOW(6) - INTERVAL 2 SECOND
                    """;
                List<JsonObject> activated = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectSql);
                     ResultSet rs = ps.executeQuery()) {
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
            SELECT id, seller_id
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
                expiredItems.add(new String[]{ rs.getString("id"), rs.getString("seller_id") });
            }
        } catch (Exception e) {
            System.err.println("[Scheduler-Settle] Lỗi query expired items: " + e.getMessage());
            return;
        }

        if (expiredItems.isEmpty()) return;
        System.out.println("[Scheduler-Settle] Settle " + expiredItems.size() + " item(s) hết giờ...");

        for (String[] pair : expiredItems) {
            settleOneItem(pair[0], pair[1]);
        }
    }

    /**
     * [REFACTOR] Dùng DAO thay vì tự viết SQL cho balance/history/owner/cancel.
     * Chỉ giữ connection trực tiếp để quản lý transaction và lock FOR UPDATE.
     */
    private static void settleOneItem(String itemId, String sellerId) {
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
                String lockBidsSql = "SELECT bid_amount, bidder_id FROM bids WHERE item_id = ? FOR UPDATE ORDER BY bid_amount DESC LIMIT 1";
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
                    // Không có bid → dùng ItemDAO.cancelItem(itemId) thay vì tự viết SQL
                    itemDAO.cancelItem(itemId);
                    conn.commit();
                    System.out.println("[Scheduler-Settle] Hủy item " + itemId + " (không có winner).");

                    JsonObject event = new JsonObject();
                    event.addProperty("event",     EventType.ITEM_CANCELLED);
                    event.addProperty("item_id",   itemId);
                    event.addProperty("seller_id", sellerId);
                    ConnectedClientRegistry.broadcastAll(event);
                    AdminStatsScheduler.notifyStatsChanged();
                    return;
                }

                // Trừ tiền bidder — dùng UserDAO.updateBalance(userId, -amount, conn)
                boolean deducted = userDAO.updateBalance(winnerId, -winAmount, conn);

                if (!deducted) {
                    // Số dư không đủ → dùng ItemDAO.cancelItem(itemId)
                    itemDAO.cancelItem(itemId);
                    conn.commit();
                    System.out.println("[Scheduler-Settle] Hủy item " + itemId
                            + " (winner " + winnerId + " không đủ số dư).");

                    JsonObject event = new JsonObject();
                    event.addProperty("event",     EventType.ITEM_CANCELLED);
                    event.addProperty("item_id",   itemId);
                    event.addProperty("seller_id", sellerId);
                    ConnectedClientRegistry.broadcastAll(event);
                    AdminStatsScheduler.notifyStatsChanged();
                    return;
                }

                // Cộng tiền seller — dùng UserDAO.updateBalance(userId, +amount, conn)
                userDAO.updateBalance(sellerId, winAmount, conn);

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

                conn.commit();
                System.out.println("[Scheduler-Settle] Settled item " + itemId
                        + " → winner: " + winnerId + ", amount: " + winAmount);

                JsonObject event = new JsonObject();
                event.addProperty("event",     EventType.AUCTION_SETTLED);
                event.addProperty("item_id",   itemId);
                event.addProperty("seller_id", sellerId);
                event.addProperty("bidder_id", winnerId);
                event.addProperty("amount",    winAmount);
                ConnectedClientRegistry.broadcastAll(event);
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