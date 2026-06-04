package org.auctionsystem.server.DAO;

import com.google.gson.JsonObject;
import org.auctionsystem.model.enums.TransactionType;
import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    public boolean insertTransaction(Connection conn, String userId, TransactionType type,
                                     double amount, double balanceBefore, double balanceAfter,
                                     String relatedItemId, String note) {
        String sql = "INSERT INTO transactions (id, user_id, type, amount, balance_before, balance_after, related_item_id, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, userId);
            ps.setString(3, type.name());
            ps.setDouble(4, amount);
            ps.setDouble(5, balanceBefore);
            ps.setDouble(6, balanceAfter);
            ps.setString(7, relatedItemId);
            ps.setString(8, note);
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // GIỮ NGUYÊN TÊN HÀM: getTransactionsByUser
    public List<JsonObject> getTransactionsByUser(String userId) {
        String sql = "SELECT t.*, i.name AS item_name, u.username AS username " +
                "FROM transactions t " +
                "LEFT JOIN items i ON t.related_item_id = i.id " +
                "LEFT JOIN users u ON t.user_id = u.id " +
                "WHERE t.user_id = ? ORDER BY t.created_at DESC";
        return queryAsJson(sql, userId, null);
    }

    // GIỮ NGUYÊN TÊN HÀM: getTransactionsByUserAndType
    public List<JsonObject> getTransactionsByUserAndType(String userId, TransactionType type) {
        String sql = "SELECT t.*, i.name AS item_name, u.username AS username " +
                "FROM transactions t " +
                "LEFT JOIN items i ON t.related_item_id = i.id " +
                "LEFT JOIN users u ON t.user_id = u.id " +
                "WHERE t.user_id = ? AND t.type = ? ORDER BY t.created_at DESC";
        return queryAsJson(sql, userId, type.name());
    }

    // GIỮ NGUYÊN TÊN HÀM: getTransactionsByItem
    public List<JsonObject> getTransactionsByItem(String itemId) {
        String sql = "SELECT t.*, i.name AS item_name, u.username AS username " +
                "FROM transactions t " +
                "LEFT JOIN items i ON t.related_item_id = i.id " +
                "LEFT JOIN users u ON t.user_id = u.id " +
                "WHERE t.related_item_id = ? ORDER BY t.created_at DESC";
        return queryAsJson(sql, itemId, null);
    }


    // [THÊM MỚI] Lấy toàn bộ giao dịch — dành cho admin xem tổng quan
    public List<JsonObject> getAllTransactions() {
        String sql = "SELECT t.*, i.name AS item_name, u.username AS username " +
                "FROM transactions t " +
                "LEFT JOIN items i ON t.related_item_id = i.id " +
                "LEFT JOIN users u ON t.user_id = u.id " +
                "ORDER BY t.created_at DESC LIMIT 500";
        List<JsonObject> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonObject json = new JsonObject();
                json.addProperty("id",             rs.getString("id"));
                json.addProperty("user_id",        rs.getString("user_id"));
                json.addProperty("username",       rs.getString("username"));
                json.addProperty("type",           rs.getString("type"));
                json.addProperty("amount",         rs.getDouble("amount"));
                json.addProperty("balance_before", rs.getDouble("balance_before"));
                json.addProperty("balance_after",  rs.getDouble("balance_after"));
                json.addProperty("related_item_id",rs.getString("related_item_id"));
                json.addProperty("note",           rs.getString("note"));
                json.addProperty("created_at",     rs.getTimestamp("created_at").toString());
                json.addProperty("item_name",      rs.getString("item_name"));
                try { json.addProperty("username", rs.getString("username")); } catch (Exception ignored) {}
                list.add(json);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private List<JsonObject> queryAsJson(String sql, String p1, String p2) {
        List<JsonObject> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p1);
            if (p2 != null) ps.setString(2, p2);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonObject json = new JsonObject();
                json.addProperty("id", rs.getString("id"));
                json.addProperty("user_id", rs.getString("user_id"));
                json.addProperty("type", rs.getString("type"));
                json.addProperty("amount", rs.getDouble("amount"));
                json.addProperty("balance_before", rs.getDouble("balance_before"));
                json.addProperty("balance_after", rs.getDouble("balance_after"));
                json.addProperty("related_item_id", rs.getString("related_item_id"));
                json.addProperty("note", rs.getString("note"));
                json.addProperty("created_at", rs.getTimestamp("created_at").toString());
                json.addProperty("item_name", rs.getString("item_name")); // Lấy từ JOIN
                // username: có thể null nếu query không JOIN users
                try { json.addProperty("username", rs.getString("username")); } catch (Exception ignored) {}
                list.add(json);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean updateBalance(String userId, double amount, Connection conn) {
        String sql = "UPDATE users SET balance = balance + ? WHERE id = ? AND balance + ? >= 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public double getBalanceById(String userId, Connection conn) {
        String sql = "SELECT balance FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) { }
        return -1;
    }
}