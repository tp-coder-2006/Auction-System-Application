package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ItemRepository {

    public JsonArray getActiveItems() {
        JsonArray items = new JsonArray();

        String sql = "SELECT id, name, description, starting_price, current_highest_price, "
                + "start_time, end_time, status "
                + "FROM items "
                + "WHERE status IN ('active') "
                + "ORDER BY end_time ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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
            System.err.println("❌ Lỗi khi lấy danh sách sản phẩm: " + e.getMessage());
        }

        return items;
    }

    public JsonObject getItemById(String itemId) {
        String sql = "SELECT id, name, description, starting_price, current_highest_price, "
                + "start_time, end_time, status, seller_id "
                + "FROM items WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
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

    public boolean updateCurrentPrice(String itemId, double newPrice) {
        String sql = "UPDATE items SET current_highest_price = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
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