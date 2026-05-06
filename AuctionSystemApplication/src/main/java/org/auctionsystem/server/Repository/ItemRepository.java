package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ItemRepository - Tầng truy cập dữ liệu cho bảng items.
 *
 * Đây là "thủ kho" chuyên quản lý sản phẩm đấu giá.
 * ClientHandler sẽ gọi các hàm ở đây khi nhận được yêu cầu liên quan đến sản phẩm.
 */
public class ItemRepository {

    /**
     * Lấy toàn bộ sản phẩm đang mở đấu giá (status = 'OPEN' hoặc 'RUNNING').
     * Trả về JsonArray để ClientHandler dễ dàng đưa vào response gửi về client.
     *
     * Ví dụ kết quả trả về:
     * [
     *   { "id": "abc", "name": "iPhone 15", "startingPrice": 10000000, "currentPrice": 12000000 },
     *   { "id": "def", "name": "Toyota Camry", "startingPrice": 500000000, "currentPrice": 550000000 }
     * ]
     */
    public JsonArray getActiveItems() {
        JsonArray items = new JsonArray();

        // Chỉ lấy những sản phẩm đang mở hoặc đang chạy, sắp xếp theo thời gian kết thúc gần nhất
        String sql = "SELECT id, name, description, starting_price, current_highest_price, " +
                "start_time, end_time, status " +
                "FROM items " +
                "WHERE status IN ('OPEN', 'RUNNING') " +
                "ORDER BY end_time ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            // Mỗi dòng trong ResultSet → một JsonObject → thêm vào JsonArray
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
            System.err.println("❌ Lỗi khi lấy danh sách sản phẩm: " + e.getMessage());
        }

        return items;
    }

    /**
     * Lấy thông tin chi tiết của một sản phẩm theo ID.
     * Dùng khi client bấm vào một sản phẩm để xem chi tiết trước khi đặt giá.
     *
     * @param itemId  UUID của sản phẩm cần xem
     * @return        JsonObject chứa thông tin, hoặc null nếu không tìm thấy
     */
    public JsonObject getItemById(String itemId) {
        String sql = "SELECT id, name, description, starting_price, current_highest_price, " +
                "start_time, end_time, status, seller_id " +
                "FROM items WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                JsonObject item = new JsonObject();
                item.addProperty("id",           rs.getString("id"));
                item.addProperty("name",         rs.getString("name"));
                item.addProperty("description",  rs.getString("description"));
                item.addProperty("startingPrice",rs.getDouble("starting_price"));
                item.addProperty("currentPrice", rs.getDouble("current_highest_price"));
                item.addProperty("startTime",    rs.getString("start_time"));
                item.addProperty("endTime",      rs.getString("end_time"));
                item.addProperty("status",       rs.getString("status"));
                item.addProperty("sellerId",     rs.getString("seller_id"));
                return item;
            }

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy chi tiết sản phẩm: " + e.getMessage());
        }

        return null;
    }

    /**
     * Cập nhật giá cao nhất hiện tại của sản phẩm sau khi có người đặt giá thành công.
     * BidRepository sẽ gọi hàm này sau khi lưu bid thành công.
     *
     * @param itemId    UUID của sản phẩm
     * @param newPrice  Giá mới cao hơn giá hiện tại
     * @return          true nếu cập nhật thành công
     */
    public boolean updateCurrentPrice(String itemId, double newPrice) {
        String sql = "UPDATE items SET current_highest_price = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, newPrice);
            stmt.setString(2, itemId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi cập nhật giá sản phẩm: " + e.getMessage());
        }

        return false;
    }
}
