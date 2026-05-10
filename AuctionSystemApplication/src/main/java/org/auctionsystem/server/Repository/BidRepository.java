package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BidRepository {
    public boolean saveBid(String bidderId, String itemId, double bidAmount) {
        // Mở một connection duy nhất — tất cả 3 bước dùng chung connection này
        // để đảm bảo chúng nằm trong cùng một transaction
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

            // Tắt auto-commit để kiểm soát transaction thủ công
            conn.setAutoCommit(false);

            try {
                // ── BƯỚC 1: Đọc giá hiện tại và KHÓA dòng item lại ──────────
                // FOR UPDATE = không cho thread khác đọc dòng này
                // cho đến khi transaction này commit hoặc rollback.
                String selectSql = "SELECT starting_price, current_highest_price, status "
                        + "FROM items WHERE id = ? FOR UPDATE";

                double minimumRequired;
                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setString(1, itemId);
                    ResultSet rs = selectStmt.executeQuery();

                    if (!rs.next()) {
                        conn.rollback();
                        System.err.println("❌ Không tìm thấy sản phẩm: " + itemId);
                        return false;
                    }

                    String status = rs.getString("status");
                    if (!"active".equals(status)) {
                        conn.rollback();
                        System.err.println("❌ Phiên đấu giá không còn mở: " + status);
                        return false;
                    }

                    double startingPrice = rs.getDouble("starting_price");
                    double currentPrice  = rs.getDouble("current_highest_price");
                    // Giá đặt phải cao hơn cả giá khởi điểm lẫn giá cao nhất hiện tại
                    minimumRequired = Math.max(startingPrice, currentPrice);
                }

                if (bidAmount <= minimumRequired) {
                    conn.rollback();
                    System.err.println("❌ Giá đặt " + bidAmount
                            + " không cao hơn mức tối thiểu " + minimumRequired);
                    return false;
                }

                // ── BƯỚC 2: Ghi bid vào bảng bids ──────────────────────────
                String insertSql = "INSERT INTO bids (id, bid_amount, bid_time, bidder_id, item_id) "
                        + "VALUES (UUID(), ?, NOW(), ?, ?)";

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setDouble(1, bidAmount);
                    insertStmt.setString(2, bidderId);
                    insertStmt.setString(3, itemId);
                    insertStmt.executeUpdate();
                }

                // ── BƯỚC 3: Cập nhật giá cao nhất trong bảng items ─────────
                String updateSql = "UPDATE items SET current_highest_price = ? WHERE id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setDouble(1, bidAmount);
                    updateStmt.setString(2, itemId);
                    updateStmt.executeUpdate();
                }

                // ── COMMIT: Xác nhận tất cả 3 bước, giải phóng lock ────────
                conn.commit();
                System.out.println("✅ Bid thành công: bidder=" + bidderId
                        + " item=" + itemId + " amount=" + bidAmount);
                return true;

            } catch (SQLException e) {
                // Nếu bất kỳ bước nào lỗi → rollback toàn bộ, không để DB ở trạng thái dở
                conn.rollback();
                System.err.println("❌ Lỗi trong transaction đặt giá, đã rollback: " + e.getMessage());
                return false;
            }

        } catch (SQLException e) {
            System.err.println("❌ Không thể mở connection: " + e.getMessage());
            return false;
        }
    }

    public JsonArray getBidHistoryByBidder(String bidderId) {
        JsonArray history = new JsonArray();

        String sql = "SELECT b.id, b.bid_amount, b.bid_time, i.name AS item_name, i.status "
                + "FROM bids b "
                + "JOIN items i ON b.item_id = i.id "
                + "WHERE b.bidder_id = ? "
                + "ORDER BY b.bid_time DESC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject bid = new JsonObject();
                bid.addProperty("bidId",    rs.getString("id"));
                bid.addProperty("amount",   rs.getDouble("bid_amount"));
                bid.addProperty("bidTime",  rs.getString("bid_time"));
                bid.addProperty("itemName", rs.getString("item_name"));
                bid.addProperty("status",   rs.getString("status"));
                history.add(bid);
            }

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy lịch sử đặt giá: " + e.getMessage());
        }

        return history;
    }
}