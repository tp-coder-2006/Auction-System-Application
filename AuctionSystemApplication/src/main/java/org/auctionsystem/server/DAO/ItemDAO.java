package org.auctionsystem.server.DAO;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.*;
import java.util.UUID;

/**
 * ItemDAO — Tầng truy cập dữ liệu cho bảng items.
 *
 * Phân biệt với ItemRepository:
 *   - ItemRepository: chỉ phục vụ Bidder (đọc danh sách, lấy chi tiết, cập nhật giá).
 *   - ItemDAO: phục vụ Seller (thêm, sửa, xóa sản phẩm của chính mình).
 */
public class ItemDAO {

    // ─── THÊM SẢN PHẨM ────────────────────────────────────────────────────────

    /**
     * Seller tạo sản phẩm mới.
     * Trạng thái ban đầu luôn là 'pending' — chờ thời gian bắt đầu.
     *
     * @return id của sản phẩm vừa tạo, hoặc null nếu thất bại
     */
    public String addItem(String name, String description, double startingPrice,
                          String startTime, String endTime, String sellerId) {
        String sql = "INSERT INTO items "
                + "(id, name, description, starting_price, current_highest_price, "
                + "start_time, end_time, status, seller_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?)";

        String itemId = UUID.randomUUID().toString();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, name);
            stmt.setString(3, description);
            stmt.setDouble(4, startingPrice);
            stmt.setDouble(5, startingPrice);   // current_highest_price = starting_price lúc mới tạo
            stmt.setString(6, startTime);
            stmt.setString(7, endTime);
            stmt.setString(8, sellerId);

            int rows = stmt.executeUpdate();
            return rows > 0 ? itemId : null;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi thêm sản phẩm: " + e.getMessage());
            return null;
        }
    }

    // ─── SỬA SẢN PHẨM ─────────────────────────────────────────────────────────

    /**
     * Seller cập nhật thông tin sản phẩm.
     *
     * Quy tắc:
     *   - Chỉ Seller sở hữu sản phẩm mới được sửa (WHERE seller_id = ?).
     *   - Chỉ sửa được khi status = 'pending' (chưa có ai đặt giá).
     *   - Không cho sửa khi đang 'active' hoặc 'closed'.
     *
     * @return true nếu cập nhật thành công
     */
    public boolean updateItem(String itemId, String name, String description,
                              double startingPrice, String startTime, String endTime,
                              String sellerId) {
        String sql = "UPDATE items "
                + "SET name = ?, description = ?, starting_price = ?, "
                + "    current_highest_price = ?, start_time = ?, end_time = ? "
                + "WHERE id = ? AND seller_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setDouble(3, startingPrice);
            stmt.setDouble(4, startingPrice);   // reset giá cao nhất khi sửa giá khởi điểm
            stmt.setString(5, startTime);
            stmt.setString(6, endTime);
            stmt.setString(7, itemId);
            stmt.setString(8, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                System.err.println("⚠️ Không thể sửa: sản phẩm không tồn tại, không thuộc seller này, hoặc đã active.");
            }
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi sửa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── XÓA SẢN PHẨM ─────────────────────────────────────────────────────────

    /**
     * Seller xóa sản phẩm của mình.
     *
     * Quy tắc:
     *   - Chỉ Seller sở hữu mới xóa được (WHERE seller_id = ?).
     *   - Chỉ xóa được khi status = 'pending' hoặc 'cancelled'.
     *   - Không xóa được khi đang 'active' (đang có người đấu giá) hoặc 'closed'.
     *
     * @return true nếu xóa thành công
     */
    public boolean deleteItem(String itemId, String sellerId) {
        String sql = "DELETE FROM items "
                + "WHERE id = ? AND seller_id = ? AND status IN ('pending', 'cancelled')";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                System.err.println("⚠️ Không thể xóa: sản phẩm không tồn tại, không thuộc seller này, hoặc đang active.");
            }
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi xóa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── LẤY SẢN PHẨM THEO SELLER ─────────────────────────────────────────────

    /**
     * Lấy toàn bộ sản phẩm của một Seller để hiển thị trên Seller Dashboard.
     * Bao gồm tất cả trạng thái (pending, active, closed, cancelled).
     *
     * @param sellerId UUID của Seller
     * @return JsonArray chứa danh sách sản phẩm
     */
    public JsonArray getItemsBySeller(String sellerId) {
        JsonArray items = new JsonArray();

        String sql = "SELECT id, name, description, starting_price, current_highest_price, "
                + "start_time, end_time, status "
                + "FROM items "
                + "WHERE seller_id = ? "
                + "ORDER BY end_time DESC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sellerId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject item = new JsonObject();
                item.addProperty("id",           rs.getString("id"));
                item.addProperty("name",         rs.getString("name"));
                item.addProperty("description",  rs.getString("description"));
                item.addProperty("startingPrice",rs.getDouble("starting_price"));
                item.addProperty("currentPrice", rs.getDouble("current_highest_price"));
                item.addProperty("startTime",    rs.getString("start_time"));
                item.addProperty("endTime",      rs.getString("end_time"));
                item.addProperty("status",       rs.getString("status"));
                items.add(item);
            }

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy sản phẩm của seller: " + e.getMessage());
        }

        return items;
    }
}