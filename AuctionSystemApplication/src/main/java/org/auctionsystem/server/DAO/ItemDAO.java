package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.Item;
import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

public class ItemDAO {

    // ─── HELPER — map ResultSet sang Item ─────────────────────────────────────

    private Item mapRow(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setId(rs.getString("id"));
        item.setName(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setStartingPrice(rs.getDouble("starting_price"));
        item.setCurrentHighestPrice((Double) rs.getObject("current_highest_price"));
        item.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        item.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        item.setStatus(ItemStatus.valueOf(rs.getString("status").toUpperCase()));
        item.setSellerId(rs.getString("seller_id"));
        item.setOwnerId(rs.getString("owner_id"));
        return item;
    }

    private static final String SELECT_COLUMNS =
            "SELECT id, name, description, starting_price, current_highest_price, " +
                    "start_time, end_time, status, seller_id, owner_id FROM items";

    // ─── THÊM SẢN PHẨM ────────────────────────────────────────────────────────

    public String addItem(String name, String description, double startingPrice,
                          String startTime, String endTime, String sellerId) {
        String sql = "INSERT INTO items "
                + "(id, name, description, starting_price, current_highest_price, "
                + "start_time, end_time, status, seller_id, owner_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)";

        String itemId = UUID.randomUUID().toString();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, name);
            stmt.setString(3, description);
            stmt.setDouble(4, startingPrice);
            stmt.setNull(5, Types.DOUBLE);
            stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(startTime)));
            stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(endTime)));
            stmt.setString(8, sellerId);  // seller_id
            stmt.setString(9, sellerId);  // owner_id = seller_id lúc đầu

            return stmt.executeUpdate() > 0 ? itemId : null;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi thêm sản phẩm: " + e.getMessage());
            return null;
        }
    }

    // ─── SỬA SẢN PHẨM ─────────────────────────────────────────────────────────

    public boolean updateItem(String itemId, String name, String description,
                              double startingPrice, String startTime, String endTime,
                              String sellerId) {
        String sql = "UPDATE items "
                + "SET name = ?, description = ?, starting_price = ?, "
                + "current_highest_price = NULL, start_time = ?, end_time = ? "
                + "WHERE id = ? AND seller_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setDouble(3, startingPrice);
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(startTime)));
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(endTime)));
            stmt.setString(6, itemId);
            stmt.setString(7, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0)
                System.err.println("⚠️ Không thể sửa: không tồn tại, không thuộc seller, hoặc đã active.");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi sửa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── XÓA SẢN PHẨM ─────────────────────────────────────────────────────────

    public boolean deleteItem(String itemId, String sellerId) {
        String sql = "DELETE FROM items "
                + "WHERE id = ? AND seller_id = ? AND status IN ('pending', 'cancelled')";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0)
                System.err.println("⚠️ Không thể xóa: không tồn tại, không thuộc seller, hoặc đang active.");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi xóa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── HỦY SẢN PHẨM ─────────────────────────────────────────────────────────

    public boolean cancelItem(String itemId, String sellerId) {
        String sql = "UPDATE items SET status = 'cancelled' "
                + "WHERE id = ? AND seller_id = ? "
                + "AND (status = 'pending' OR (status = 'active' AND current_highest_price IS NULL))";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0)
                System.err.println("⚠️ Không thể hủy: đã có người đặt giá hoặc không thuộc quyền sở hữu.");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi hủy sản phẩm: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelItem(String itemId) {
        String sql = "UPDATE items SET status = 'cancelled' WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi hủy sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── CẬP NHẬT OWNER SAU KHI THANH TOÁN ───────────────────────────────────

    public boolean updateOwner(String itemId, String newOwnerId) {
        String sql = "UPDATE items SET owner_id = ?, status = 'closed' WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newOwnerId);
            ps.setString(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật owner: " + e.getMessage());
            return false;
        }
    }

    // ─── TÁI KHỞI ĐỘNG ĐẤU GIÁ ───────────────────────────────────────────────

    public boolean restartItemAuction(String itemId, String requesterId,
                                      double newStartingPrice,
                                      String newStartTime, String newEndTime) {
        String checkSql  = "SELECT owner_id FROM items WHERE id = ? AND status IN ('closed', 'cancelled')";
        String updateSql = "UPDATE items SET "
                + "seller_id = ?, owner_id = ?, "
                + "starting_price = ?, current_highest_price = NULL, "
                + "start_time = ?, end_time = ?, status = 'pending' "
                + "WHERE id = ? AND owner_id = ? AND status IN ('closed', 'cancelled')";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, itemId);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.err.println("❌ Không tìm thấy item hoặc item chưa closed/cancelled!");
                return false;
            }

            String ownerId = rs.getString("owner_id");
            if (!ownerId.equals(requesterId)) {
                System.err.println("❌ Bạn không phải chủ sở hữu item này!");
                return false;
            }

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, ownerId);  // seller_id mới = owner hiện tại
                updateStmt.setString(2, ownerId);  // owner_id giữ nguyên
                updateStmt.setDouble(3, newStartingPrice);
                updateStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(newStartTime)));
                updateStmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(newEndTime)));
                updateStmt.setString(6, itemId);
                updateStmt.setString(7, ownerId);
                return updateStmt.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi tái khởi động đấu giá: " + e.getMessage());
            return false;
        }
    }

    // ─── CẬP NHẬT TRẠNG THÁI TỰ ĐỘNG ─────────────────────────────────────────

    public boolean autoUpdateItemStatuses() {
        String sqlActive = "UPDATE items SET status = 'active' WHERE status = 'pending' AND start_time <= NOW()";
        String sqlClosed = "UPDATE items SET status = 'closed' WHERE status = 'active' AND end_time <= NOW()";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            int total = stmt.executeUpdate(sqlActive);
            total    += stmt.executeUpdate(sqlClosed);
            conn.commit();
            return total > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tự động cập nhật trạng thái: " + e.getMessage());
            return false;
        }
    }

    // ─── TRUY VẤN ─────────────────────────────────────────────────────────────

    public Item getAItemById(String itemId) {
        String sql = SELECT_COLUMNS + " WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy item: " + e.getMessage());
        }
        return null;
    }

    public ArrayList<Item> getItemsBySeller(String sellerId) {
        String sql = SELECT_COLUMNS + " WHERE seller_id = ? ORDER BY end_time DESC";
        return queryList(sql, sellerId);
    }

    public ArrayList<Item> getItemsByOwner(String ownerId) {
        String sql = SELECT_COLUMNS + " WHERE owner_id = ? ORDER BY end_time DESC";
        return queryList(sql, ownerId);
    }

    public ArrayList<Item> getAllItems() {
        String sql = SELECT_COLUMNS + " ORDER BY end_time DESC";
        ArrayList<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy tất cả sản phẩm: " + e.getMessage());
        }
        return items;
    }

    // ─── HELPER — tránh lặp code query danh sách ──────────────────────────────

    private ArrayList<Item> queryList(String sql, String param) {
        ArrayList<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi truy vấn danh sách item: " + e.getMessage());
        }
        return items;
    }

    public ArrayList<Item> getActiveItems() {
        String sql = SELECT_COLUMNS + " WHERE status = 'active' ORDER BY end_time ASC";
        ArrayList<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy item active: " + e.getMessage());
        }
        return items;
    }
}