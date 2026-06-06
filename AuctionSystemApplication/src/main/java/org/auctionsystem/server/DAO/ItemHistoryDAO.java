package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.ItemHistory;
import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;

public class ItemHistoryDAO {

    // ─── 1. GHI LỊCH SỬ (Giữ nguyên) ────────────────────────────────────────

    public boolean addHistory(String itemId, String sellerId, String buyerId, double soldPrice) {
        String sql = "INSERT INTO item_ownership_history (id, item_id, seller_id, buyer_id, sold_price, sold_time) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, itemId);
            ps.setString(3, sellerId);
            ps.setString(4, buyerId);
            ps.setDouble(5, soldPrice);
            ps.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.now()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi ghi lịch sử: " + e.getMessage());
            return false;
        }
    }

    public boolean addHistory(Connection conn, String itemId, String sellerId, String buyerId, double soldPrice) {
        String sql = "INSERT INTO item_ownership_history (id, item_id, seller_id, buyer_id, sold_price, sold_time) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, itemId);
            ps.setString(3, sellerId);
            ps.setString(4, buyerId);
            ps.setDouble(5, soldPrice);
            ps.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.now()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi ghi lịch sử (conn): " + e.getMessage());
            return false;
        }
    }

    // ─── 2. TRUY XUẤT LỊCH SỬ ────────────────────────────────────────────────

    /**
     * Lịch sử theo seller — JOIN items lấy tên sản phẩm, JOIN users lấy username người mua.
     */
    public ArrayList<ItemHistory> getHistoryBySeller(String sellerId) {
        String sql = "SELECT h.*, i.name AS item_name, u.username AS buyer_name " +
                "FROM item_ownership_history h " +
                "JOIN items i ON h.item_id  = i.id " +
                "JOIN users u ON h.buyer_id = u.id " +
                "WHERE h.seller_id = ? " +
                "ORDER BY h.sold_time DESC";
        return queryHistory(sql, sellerId);
    }

    /**
     * Lịch sử theo buyer — JOIN items lấy tên sản phẩm.
     */
    public ArrayList<ItemHistory> getHistoryByBuyer(String buyerId) {
        String sql = "SELECT h.*, i.name AS item_name, u.username AS buyer_name " +
                "FROM item_ownership_history h " +
                "JOIN items i ON h.item_id  = i.id " +
                "JOIN users u ON h.buyer_id = u.id " +
                "WHERE h.buyer_id = ? " +
                "ORDER BY h.sold_time DESC";
        return queryHistory(sql, buyerId);
    }

    /**
     * Lịch sử theo item — JOIN items và users.
     */
    public ArrayList<ItemHistory> getHistoryByItem(String itemId) {
        String sql = "SELECT h.*, i.name AS item_name, u.username AS buyer_name " +
                "FROM item_ownership_history h " +
                "JOIN items i ON h.item_id  = i.id " +
                "JOIN users u ON h.buyer_id = u.id " +
                "WHERE h.item_id = ? " +
                "ORDER BY h.sold_time ASC";
        return queryHistory(sql, itemId);
    }

    // Helper chung — map ResultSet → ItemHistory (bao gồm item_name và buyer_name)
    private ArrayList<ItemHistory> queryHistory(String sql, String param) {
        ArrayList<ItemHistory> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ItemHistory h = new ItemHistory();
                h.setId(rs.getString("id"));
                h.setItemId(rs.getString("item_id"));
                h.setItemName(rs.getString("item_name"));
                h.setSellerId(rs.getString("seller_id"));
                h.setBuyerId(rs.getString("buyer_id"));
                h.setBuyerName(rs.getString("buyer_name"));
                h.setSoldPrice(rs.getDouble("sold_price"));
                h.setSoldTime(rs.getTimestamp("sold_time").toLocalDateTime());
                list.add(h);
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi truy xuất lịch sử: " + e.getMessage());
        }
        return list;
    }

    // ─── 3. KIỂM TRA MUA HÀNG ────────────────────────────────────────────────

    public boolean hasBuyerPurchasedFromSeller(String buyerId, String sellerId) {
        String sql = "SELECT COUNT(*) FROM item_ownership_history WHERE buyer_id = ? AND seller_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buyerId);
            ps.setString(2, sellerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (Exception e) {
            System.err.println("❌ [ItemHistoryDAO] hasBuyerPurchasedFromSeller lỗi: " + e.getMessage());
        }
        return false;
    }

}
