package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BidRepository {

    private final ItemRepository itemRepository = new ItemRepository();

    public boolean saveBid(String bidderId, String itemId, double bidAmount) {
        JsonObject item = itemRepository.getItemById(itemId);
        if (item == null) {
            System.err.println("❌ Không tìm thấy sản phẩm: " + itemId);
            return false;
        }

        double currentPrice  = item.get("currentPrice").getAsDouble();
        double startingPrice = item.get("startingPrice").getAsDouble();
        double minimumRequired = Math.max(currentPrice, startingPrice);

        if (bidAmount <= minimumRequired) {
            System.err.println("❌ Giá đặt " + bidAmount + " không cao hơn giá hiện tại " + minimumRequired);
            return false;
        }

        String sql = "INSERT INTO bids (id, bid_amount, bid_time, bidder_id, item_id) "
                + "VALUES (UUID(), ?, NOW(), ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, bidAmount);
            stmt.setString(2, bidderId);
            stmt.setString(3, itemId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) return false;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lưu bid: " + e.getMessage());
            return false;
        }

        return itemRepository.updateCurrentPrice(itemId, bidAmount);
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