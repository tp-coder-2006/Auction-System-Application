package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.ItemHistory;
import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;

public class ItemHistoryDAO {

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
            System.err.println("Lỗi ghi lịch sử: " + e.getMessage());
            return false;
        }
    }

    public ArrayList<ItemHistory> getHistoryBySeller(String sellerId) {
        String sql = "SELECT * FROM item_ownership_history WHERE seller_id = ? ORDER BY sold_time DESC";
        return queryHistory(sql, sellerId);
    }

    public ArrayList<ItemHistory> getHistoryByBuyer(String buyerId) {
        String sql = "SELECT * FROM item_ownership_history WHERE buyer_id = ? ORDER BY sold_time DESC";
        return queryHistory(sql, buyerId);
    }

    public ArrayList<ItemHistory> getHistoryByItem(String itemId) {
        String sql = "SELECT * FROM item_ownership_history WHERE item_id = ? ORDER BY sold_time ASC";
        return queryHistory(sql, itemId);
    }

    // Dùng chung để tránh lặp code
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
                h.setSellerId(rs.getString("seller_id"));
                h.setBuyerId(rs.getString("buyer_id"));
                h.setSoldPrice(rs.getDouble("sold_price"));
                h.setSoldTime(rs.getTimestamp("sold_time").toLocalDateTime());
                list.add(h);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy xuất lịch sử: " + e.getMessage());
        }
        return list;
    }
}